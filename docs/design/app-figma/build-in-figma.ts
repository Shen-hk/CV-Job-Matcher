// Runs through the visible Scripter plugin in the existing Figma file.
// The injected SCENES/GROUPS constants come from scene-manifest.mjs.
// Existing pages are read-only. New editable layers are cloned from the file's own foundations.
const TARGET_PAGE = '12 · APP 完整体验与场景 · 11.25';
const OWN = 'tielink-app-design-1125';
const target = figma.currentPage;
if (target.name !== TARGET_PAGE) throw new Error('Select the new APP experience page before running.');
for(const node of [...target.children]){
  const key=node.getPluginData('key');
  if(node.getPluginData('owner')===OWN && ((key.startsWith('scene:')&&REBUILD_IDS.includes(key.slice(6)))||(key.startsWith('label:')&&REBUILD_IDS.includes(key.slice(6))))){node.remove();continue;}
  if(node.getPluginData('owner')===OWN && (key.startsWith('scene:')||key.startsWith('component:')) &&
    (node.getPluginData('buildVersion')!=='v2'||(key.startsWith('scene:')&&node.getPluginData('built')!=='yes')))node.remove();
  else if(node.type==='TEXT' && SCENES.some(s=>node.characters===s.id+'  /  '+s.title) && node.getPluginData('owner')!==OWN)node.remove();
}
const ds = figma.root.children.find(p=>p.name.startsWith('00 ·'));
const original = figma.root.children.find(p=>p.name.startsWith('01c ·'));
if (!ds || !original) throw new Error('Existing design foundations are unavailable.');
const frameSeed = original.children.find(n=>n.name.startsWith('A01 /'));
const textSeed = original.findOne(n=>n.type==='TEXT' && n.fontName && n.fontName.family==='Noto Sans SC' && n.fontName.style==='Regular');
const rectSeed = frameSeed.findOne(n=>n.type==='RECTANGLE');
const componentSeed = ds.findOne(n=>n.type==='COMPONENT' && n.name==='按钮底层/light/primary');
const variables = await figma.variables.getLocalVariablesAsync();
const colors = Object.fromEntries(variables.filter(v=>v.resolvedType==='COLOR').map(v=>[v.name.replace('色彩/',''),v]));
const styles = figma.getLocalTextStyles();
const fonts = {regular:{family:'Noto Sans SC',style:'Regular'},medium:{family:'Noto Sans SC',style:'Medium'},bold:{family:'Noto Sans SC',style:'Bold'}};
await Promise.all(Object.values(fonts).map(f=>figma.loadFontAsync(f)));
const icons = Object.fromEntries(ds.findAll(n=>n.type==='COMPONENT' && n.name.startsWith('图标/')).map(n=>[n.name,n]));
const owned = (key)=>target.children.find(n=>n.getPluginData('owner')===OWN && n.getPluginData('key')===key);
function tag(n,key){n.setPluginData('owner',OWN);n.setPluginData('key',key);return n;}
function color(name){
  const v=colors[name]||colors.ink;
  const val=Object.values(v.valuesByMode)[0];
  const rgb=val && typeof val.r==='number'?val:{r:0,g:0,b:0};
  return figma.variables.setBoundVariableForPaint({type:'SOLID',color:rgb},'color',v);
}
function fill(n,name){n.fills=[color(name)];}
function clean(n){if('children'in n)for(const c of [...n.children])c.remove();if('reactions'in n)n.reactions=[];return n;}
let blank=owned('frame-seed');
if(!blank){blank=tag(clean(frameSeed.clone()),'frame-seed');target.appendChild(blank);blank.name='内部模板 / 页面容器';blank.x=-2600;blank.y=0;blank.resize(390,844);}

// Clone-backed $fig keeps every new layer native and editable while reusing existing styles.
const $fig={
  frame(name,x,y,w,h,bg='bg',parent=target){
    const n=blank.clone();parent.appendChild(n);n.setPluginData('key','');n.name=name;n.layoutMode='NONE';n.resize(w,h);n.x=x;n.y=y;
    n.clipsContent=true;n.cornerRadius=0;n.effects=[];n.strokes=[];fill(n,bg);return n;
  },
  text(parent,value,x,y,w,h,role='body',tone='ink'){
    const n=textSeed.clone();parent.appendChild(n);n.name=value.slice(0,36);n.textAutoResize='NONE';
    const style=styles.find(s=>s.name==='中文排版/'+role);
    if(style)n.textStyleId=style.id;else{n.fontName=fonts.regular;n.fontSize=15;}
    n.characters=value;n.resize(w,h);n.x=x;n.y=y;fill(n,tone);n.textAlignVertical='TOP';n.textAlignHorizontal='LEFT';
    n.constraints={horizontal:'STRETCH',vertical:'MIN'};return n;
  },
  rect(parent,x,y,w,h,tone,r=0){
    const n=rectSeed.clone();parent.appendChild(n);n.name='色块 / '+tone;n.resize(w,h);n.x=x;n.y=y;n.cornerRadius=r;n.strokes=[];n.effects=[];fill(n,tone);return n;
  },
  component(name,w,h,tone){
    const key='component:'+name;const found=owned(key);if(found)return found;
    const n=tag(clean(componentSeed.clone()),key);target.appendChild(n);n.name='APP 11.25 / '+name;n.resize(w,h);n.layoutMode='NONE';n.strokes=[];n.effects=[];fill(n,tone);n.cornerRadius=16;return n;
  },
  instance(c,parent,x,y,w,h,props={}){
    const n=c.createInstance();parent.appendChild(n);n.x=x;n.y=y;if(w&&h)n.resize(w,h);
    const defs=c.componentPropertyDefinitions;const overrides={};
    for(const [k,v]of Object.entries(props)){const key=Object.keys(defs).find(a=>a.split('#')[0]===k);if(key)overrides[key]=v;}
    if(Object.keys(overrides).length)n.setProperties(overrides);return n;
  }
};
const links=[];
function link(node,to,trigger='ON_CLICK',timeout=1.6){if(to){
  node.setPluginData('prototypeTo',to);node.setPluginData('prototypeTrigger',trigger);node.setPluginData('prototypeTimeout',String(timeout));
  links.push({node,to,trigger,timeout});
}}
function hotspot(parent,x,y,w,h,to){const n=$fig.frame('交互 / '+to,x,y,w,h,'white',parent);n.fills=[];n.clipsContent=false;link(n,to);return n;}
function icon(parent,name,x,y,size=22,tone='ink'){
  const c=icons['图标/'+name+'/'+tone]||icons['图标/'+name+'/ink']||icons['图标/'+name+'/muted'];
  if(!c)return null;return $fig.instance(c,parent,x,y,size,size);
}
function prop(c,n,name,type='TEXT',initial=''){
  const key=Object.keys(c.componentPropertyDefinitions).find(k=>k.split('#')[0]===name)||c.addComponentProperty(name,type,initial);
  n.componentPropertyReferences=type==='BOOLEAN'?{visible:key}:{characters:key};
}
function componentBody(kind,w,h,bg){
  const c=$fig.component(kind,w,h,bg);if(c.children.length)return c;
  c.setPluginData('buildVersion','v2');
  if(kind.startsWith('Button')){
    const t=$fig.text(c,'继续',10,13,w-20,24,'bodyStrong',kind==='Button/Dark'?'nightText':'ink');t.textAlignHorizontal='CENTER';prop(c,t,'Label','TEXT','继续');
  }else if(kind==='Row'){
    const a=$fig.text(c,'标题',16,10,w-148,24,'bodyStrong');prop(c,a,'Title','TEXT','标题');
    const b=$fig.text(c,'说明',16,37,w-32,36,'caption','muted');prop(c,b,'Body','TEXT','说明');
    prop(c,$fig.text(c,'',w-124,12,106,18,'micro','muted'),'Meta','TEXT','');
  }else if(kind==='Field'){
    prop(c,$fig.text(c,'字段',14,9,w-28,20,'caption','muted'),'Title','TEXT','字段');
    prop(c,$fig.text(c,'内容',14,31,w-28,29,'body'),'Body','TEXT','内容');
    prop(c,$fig.text(c,'提示',14,65,w-28,18,'micro','muted'),'Meta','TEXT','');
  }else if(kind==='Tile'){
    icon(c,'file-text',14,12,22,'ink');
    prop(c,$fig.text(c,'工具',14,44,w-28,24,'bodyStrong'),'Title','TEXT','工具');
    prop(c,$fig.text(c,'用途',14,75,w-28,36,'caption','muted'),'Body','TEXT','用途');
  }else if(kind==='Banner'){
    prop(c,$fig.text(c,'当前状态',14,10,w-28,22,'label'),'Title','TEXT','当前状态');
    prop(c,$fig.text(c,'说明',14,35,w-28,31,'caption','muted'),'Body','TEXT','说明');
  }else if(kind==='Hero'){
    prop(c,$fig.text(c,'重要状态',18,18,w-36,54,'title'),'Title','TEXT','重要状态');
    prop(c,$fig.text(c,'说明',18,76,w-36,58,'caption','muted'),'Body','TEXT','说明');
    prop(c,$fig.text(c,'',18,138,w-36,17,'micro','muted'),'Meta','TEXT','');
  }else{
    prop(c,$fig.text(c,'成果标题',16,12,w-32,24,'bodyStrong'),'Title','TEXT','成果标题');
    prop(c,$fig.text(c,'成果摘要',16,42,w-32,48,'caption','muted'),'Body','TEXT','成果摘要');
    prop(c,$fig.text(c,'状态',16,h-22,w-32,18,'micro','muted'),'Meta','TEXT','状态');
  }
  return c;
}
const C={
  card:componentBody('Card',354,112,'white'),
  row:componentBody('Row',354,76,'white'),
  field:componentBody('Field',354,88,'field'),
  tile:componentBody('Tile',171,124,'white'),
  banner:componentBody('Banner',354,70,'lav'),
  hero:componentBody('Hero',354,158,'limeWash'),
  primary:componentBody('Button/Primary',354,48,'lime'),
  quiet:componentBody('Button/Quiet',354,48,'white'),
  dark:componentBody('Button/Dark',354,48,'night'),
  notice:componentBody('Notification',354,158,'white'),
};
let ci=0;for(const c of Object.values(C)){c.x=-2000+(ci%2)*410;c.y=Math.floor(ci/2)*210;ci++;}
C.quiet.strokes=[color('line')];C.quiet.strokeWeight=1;
const built={};
for(const n of target.children){const k=n.getPluginData('key');if(n.getPluginData('owner')===OWN && k.startsWith('scene:'))built[k.slice(6)]=n;}
function styledInstance(kind,parent,b,x,y,w=354,h){
  const c=C[kind],n=$fig.instance(c,parent,x,y,w,h||c.height,{Title:b.title||'',Body:b.body||'',Meta:b.meta||''});
  if(b.tone)fill(n,b.tone);
  if(['night','night2'].includes(b.tone)){
    for(const t of n.findAll(t=>t.type==='TEXT'))fill(t,t.fontSize>=19?'nightText':'nightSub');
  }
  if(b.to){icon(n.parent,'chevron-right',x+w-30,y+15,16,'muted');link(n,b.to);}
  return n;
}
function button(parent,a,x,y,w){
  const n=$fig.instance(a.style==='quiet'?C.quiet:C.primary,parent,x,y,w,48,{Label:a.label});link(n,a.to);return n;
}
function nav(parent,active){
  $fig.rect(parent,0,756,390,67,'paper');
  const items=[['Agent','H01','sparkles'],['任务','B01','clipboard-list'],['团队','T01','layers'],['资料','L01','folder-open']];
  items.forEach(([name,to,ic],i)=>{
    const x=i*97.5;
    if(name===active)$fig.rect(parent,x+12,762,74,29,'limeWash',14);
    icon(parent,ic,x+39,765,21,name===active?'limeInk':'muted');
    const t=$fig.text(parent,name,x+9,795,80,20,'micro',name===active?'ink':'muted');t.textAlignHorizontal='CENTER';
    hotspot(parent,x,756,97.5,67,to);
  });
}
function shell(scene,x,y){
  const dark=['notification','lock'].includes(scene.layout);
  const p=$fig.frame(scene.id+' · '+scene.title,x,y,390,844,dark?'night':'bg');tag(p,'scene:'+scene.id);
  p.setPluginData('sceneTitle',scene.title);p.setPluginData('layout',scene.layout);
  const topbar=frameSeed.children.filter(n=>n.y<40||n.name==='系统手势区');
  for(const source of topbar){const n=source.clone();p.appendChild(n);
    if(dark){
      if(n.type==='TEXT')n.visible=false;
      else if(n.type==='INSTANCE'&&source.name.toLowerCase().includes('wifi')){n.visible=false;icon(p,'wifi',source.x,source.y,15,'nightText');}
      else {if('fills'in n&&n.fills.length)fill(n,'nightText');if('strokes'in n&&n.strokes.length)n.strokes=[color('nightText')];}
    }
  }
  if(dark)$fig.text(p,'9:41',29,13,60,22,'label','nightText');
  if(dark){
    $fig.text(p,scene.layout==='lock'?'09:41':'通知中心',22,66,345,60,scene.layout==='lock'?'number':'headline','nightText');
    $fig.text(p,scene.subtitle,22,scene.layout==='lock'?126:112,345,24,'caption','nightSub');
  }else{
    if(scene.back){icon(p,'chevron-left',18,57,24);hotspot(p,0,44,58,52,scene.back);}
    const hx=scene.back?53:22;
    $fig.text(p,scene.title,hx,53,scene.back?272:300,31,'title');
    $fig.text(p,scene.subtitle,hx,87,scene.back?274:320,24,'caption','muted');
    icon(p,'settings',344,58,22,'muted');hotspot(p,333,44,50,50,'S01');
  }
  if(scene.nav)nav(p,scene.nav);
  return p;
}
function clearReactions(n){if('reactions'in n && n.reactions.length)n.reactions=[];if('children'in n)for(const c of n.children)clearReactions(c);}
function existingSource(prefix){
  for(const page of figma.root.children){if(page.id===target.id||page.name.startsWith('11 ·'))continue;const n=page.children.find(c=>c.type==='FRAME'&&c.name.startsWith(prefix));if(n)return n;}
  throw new Error('Source screen not found: '+prefix);
}
const groupsOrder=GROUPS.map(g=>g[0]);
function location(scene){
  const gi=groupsOrder.indexOf(scene.id[0]);const siblings=SCENES.filter(s=>s.id[0]===scene.id[0]);
  const idx=siblings.findIndex(s=>s.id===scene.id);
  let y=1250;for(let g=0;g<gi;g++)y+=Math.ceil(SCENES.filter(s=>s.id[0]===groupsOrder[g]).length/6)*980+190;
  return {x:(idx%6)*462,y:y+Math.floor(idx/6)*980};
}
const created=[], problems=[];
for(const scene of SCENES){
  if(PROOF_IDS.length&&!PROOF_IDS.includes(scene.id))continue;
  if(built[scene.id])continue;
  const {x,y}=location(scene);
  let p;
  if(scene.layout==='source'){
    const src=existingSource(scene.source);p=src.clone();target.appendChild(p);clearReactions(p);
    p.name=scene.id+' · '+scene.title+' · 复用';p.x=x;p.y=y;tag(p,'scene:'+scene.id);p.setPluginData('sourceId',src.id);p.setPluginData('sceneTitle',scene.title);p.setPluginData('layout','source');
    hotspot(p,0,44,58,54,scene.back||'L01');
    const oldNavigationTexts=p.findAll(n=>n.type==='TEXT'&&['工作台','资料库','面试','投递'].includes(n.characters)&&n.absoluteBoundingBox.y-p.y>740);
    if(oldNavigationTexts.length>=3){
      const bar=p.findAll(n=>n.type==='FRAME'&&n.width>340&&n.height<100&&n.absoluteBoundingBox.y-p.y>730&&n.findAll(c=>c.type==='TEXT'&&['工作台','资料库','面试','投递'].includes(c.characters)).length>=3)[0];
      if(bar)bar.remove();nav(p,'资料');
    }
    const labels=p.findAll(n=>n.type==='TEXT'&& (scene.primaryLabel?n.characters===scene.primaryLabel:/保存|导出|开始|生成|下一步|添加|创建|继续|润色|分析|查看评估|返回|结束|更新状态|确定/.test(n.characters))&&n.height<60);
    const preferred=labels.filter(n=>n.absoluteBoundingBox.y-p.absoluteBoundingBox.y>500);
    const candidate=(preferred.length?preferred:labels).slice(-1)[0];
    if(candidate&&scene.sourceNext){const b=candidate.absoluteBoundingBox;hotspot(p,Math.max(0,b.x-p.x-10),Math.max(40,b.y-p.y-8),Math.max(80,b.width+20),Math.max(44,b.height+16),scene.sourceNext);}
    else if(scene.sourceNext){problems.push({id:scene.id,note:'Original action requires a specific hotspot; no covering button was added.'});}
    if(scene.id==='J12'){
      const transcript=p.findOne(n=>n.type==='TEXT'&&n.characters==='查看转写');
      if(transcript){const b=transcript.absoluteBoundingBox;hotspot(p,b.x-p.x-8,b.y-p.y-8,Math.max(100,b.width+16),44,'J20');}
    }
    for(const cancel of p.findAll(n=>n.type==='TEXT'&&['取消','继续面试'].includes(n.characters))){
      const b=cancel.absoluteBoundingBox;hotspot(p,Math.max(0,b.x-p.x-8),b.y-p.y-8,Math.max(90,b.width+16),Math.max(44,b.height+16),'@back');
    }
  }else{
    p=shell(scene,x,y);
    let cursor=scene.layout==='lock'?224:scene.layout==='notification'?160:126;
    if(scene.layout==='onboarding'){icon(p,'link-2',173,130,44,'limeInk');cursor=200;}
    for(const b of scene.blocks||[]){
      if(['hero','card','row','field','banner'].includes(b.kind)){
        const n=styledInstance(b.kind,p,b,18,cursor);cursor+=n.height+12;
      }else if(b.kind==='grid'){
        b.items.forEach(([title,body,to,ic],i)=>{
          const xx=18+(i%2)*183,yy=cursor+Math.floor(i/2)*136;
          const n=styledInstance('tile',p,{title,body,to},xx,yy,171,124);
          const old=n.findOne(n=>n.type==='INSTANCE');if(old)old.visible=false;
          icon(p,ic,xx+14,yy+12,22,'ink');
        });cursor+=Math.ceil(b.items.length/2)*136;
      }else if(b.kind==='filters'){
        const ww=354/b.items.length;
        b.items.forEach((label,i)=>{
          $fig.rect(p,18+i*ww,cursor,ww-4,34,i===0?'limeWash':'soft',12);
          const text=$fig.text(p,label,20+i*ww,cursor+8,ww-8,21,'caption',i===0?'ink':'muted');text.textAlignHorizontal='CENTER';
        });cursor+=46;
      }else if(b.kind==='document'){
        const hh=b.height||420;
        const paper=$fig.frame('可编辑文档内容',18,cursor,354,hh,'white',p);paper.cornerRadius=8;
        $fig.text(paper,b.title,20,22,314,54,'title');
        const content=$fig.text(paper,b.body,20,84,314,hh-100,scene.layout==='pdf'?'caption':'body');content.lineHeight={unit:'PIXELS',value:scene.layout==='pdf'?20:24};
        content.textAutoResize='HEIGHT';paper.overflowDirection='VERTICAL';
        if(scene.layout==='html'){$fig.rect(paper,0,0,354,7,'lime');}
        if(scene.layout==='pdf'){
          paper.resize(318,hh);paper.x=36;content.resize(278,hh-100);
          $fig.text(p, '1 / 2',170,cursor+hh+8,65,24,'caption','muted');
        }
        if(scene.layout!=='pdf')$fig.text(p,'在文档内滑动，继续阅读全文',18,cursor+hh+7,354,18,'micro','muted');
        cursor+=hh+(scene.layout==='pdf'?38:32);
      }else if(b.kind==='compare'){
        const w=171;
        for(const [i,title,body,tone]of [[0,b.leftTitle,b.left,'white'],[1,b.rightTitle,b.right,'limeWash']]){
          const box=$fig.frame('版本对照',18+i*183,cursor,w,260,tone,p);box.cornerRadius=14;
          $fig.text(box,title,14,16,w-28,45,'label');
          $fig.text(box,body,14,77,w-28,160,'caption','muted');
        }cursor+=274;
      }else if(b.kind==='notice'){
        const n=styledInstance('notice',p,b,18,cursor);cursor+=n.height+16;
      }else if(b.kind==='dialog'){
        // Design state: a real editable dialog over a muted app backdrop.
        $fig.text(p,'已有任务与成果都在',22,151,330,30,'body','muted');
        const veil=$fig.rect(p,0,111,390,709,'dim');veil.opacity=0.45;
        const d=$fig.frame('确认对话框',18,268,354,318,'white',p);d.cornerRadius=24;
        $fig.text(d,b.title,22,25,310,64,'title');
        $fig.text(d,b.body,22,98,310,104,'body','muted');
        button(d,{label:b.confirm,to:b.to},16,209,322);
        button(d,{label:b.cancel,to:b.cancelTo,style:'quiet'},16,264,322);
        cursor=0;
      }
    }
    if(scene.layout==='chat'){
      const composer=$fig.frame('对话输入入口',18,scene.id==='H01'?660:654,354,86,'white',p);composer.cornerRadius=18;
      $fig.text(composer,'说出你想完成的事…',14,13,290,25,'body','muted');
      icon(composer,'plus',14,51,19,'muted');icon(composer,'mic',276,51,19,'muted');icon(composer,'arrow-up',314,51,19,'ink');
      hotspot(composer,0,0,354,86,scene.id==='H01'?'H02':'H03');
      if(scene.actions.length)cursor=Math.min(cursor,620);
    }
    const limit=scene.layout==='chat'?654:scene.nav?684:730;
    if(cursor>limit && !['modal','notification','lock'].includes(scene.layout))problems.push({id:scene.id,contentBottom:cursor,limit});
    if(scene.actions.length && scene.layout!=='chat'){
      const yy=scene.nav?696:748;
      const w=scene.actions.length>1?171:354;
      scene.actions.forEach((a,i)=>button(p,a,18+i*183,yy,w));
    }
    if(scene.after)link(p,scene.after,'AFTER_TIMEOUT',scene.afterSeconds||2.6);
  }
  p.setPluginData('built','yes');p.setPluginData('buildVersion','v2');built[scene.id]=p;created.push({id:scene.id,nodeId:p.id,name:p.name});
  tag($fig.text(target,scene.id+'  /  '+scene.title,x,y-39,420,31,'title','nightText'),'label:'+scene.id);
}
if(!PROOF_IDS.length){
  // Keep groups aligned when new state branches add a row to a section.
  for(const scene of SCENES){const p=built[scene.id],pos=location(scene);p.x=pos.x;p.y=pos.y;
    const label=owned('label:'+scene.id);if(label){label.x=pos.x;label.y=pos.y-39;}
  }
  for(const [key]of GROUPS){const first=SCENES.find(s=>s.id[0]===key),band=owned('group:'+key);if(band)band.y=location(first).y-150;}
}
if(!PROOF_IDS.length && !owned('overview')){
  const overview=tag($fig.frame('APP 设计总览 / 从这里开始',0,0,2700,1000,'night'),'overview');
  $fig.text(overview,'TieLink · 整套 APP 体验',40,36,2500,56,'display','nightText');
  $fig.text(overview,'主 Agent 承接需求，团队、工具、任务、资料和成果组成完整工作路径。',40,110,2500,36,'title','nightSub');
  $fig.text(overview,'本页为可编辑设计原型与示例状态；以 Figma 为设计基线，不等同于 Android 已实现功能。01b 等旧首页方案保留作对照。',40,163,2500,28,'body','nightSub');
  GROUPS.forEach(([key,title,desc],i)=>{
    const x=40+(i%3)*884,y=235+Math.floor(i/3)*230;
    const card=$fig.frame(key+' / '+title,x,y,850,202,'night2',overview);card.cornerRadius=20;
    $fig.text(card,key+'  '+title,22,18,806,34,'title','nightText');
    $fig.text(card,desc,22,64,806,31,'body','nightSub');
    const list=SCENES.filter(s=>s.id[0]===key);const generated=list.filter(s=>!s.source).length;
    $fig.text(card,list.length+' 个页面 / 状态 · '+(generated?generated+' 个新增场景':'沿用已有专业工作台'),22,111,806,26,'caption','nightSub');
    const entry=list[0].id;button(card,{label:'打开 '+title,to:entry},22,148,340);
  });
  for(const [key,title,desc] of GROUPS){
    const first=SCENES.find(s=>s.id[0]===key),pos=location(first);
    const band=tag($fig.frame('分区 / '+title,0,pos.y-150,2700,93,'night2'),'group:'+key);band.cornerRadius=16;
    $fig.text(band,key+'  '+title,26,15,2600,38,'headline','nightText');
    $fig.text(band,desc,26,58,2600,25,'body','nightSub');
  }
}
// Rebuild links from tagged destination metadata on every run, without touching original pages.
for(const l of links){l.node.setPluginData('prototypeTo',l.to);l.node.setPluginData('prototypeTrigger',l.trigger);l.node.setPluginData('prototypeTimeout',String(l.timeout));}
const allNodes=target.findAll(n=>!!n.getPluginData('prototypeTo'));
let connected=0;
for(const n of allNodes){
  const to=n.getPluginData('prototypeTo');const trigger=n.getPluginData('prototypeTrigger')||'ON_CLICK';
  if(to!=='@back'&&!built[to])continue;
  let root=n;while(root.parent && root.parent.id!==target.id)root=root.parent;
  if(to!=='@back'&&root.id===built[to].id){n.setPluginData('prototypeTo','');continue;}
  const action=to==='@back'?{type:'BACK'}:{type:'NODE',destinationId:built[to].id,navigation:'NAVIGATE',transition:{type:'DISSOLVE',easing:{type:'EASE_OUT'},duration:0.18},preserveScrollPosition:false};
  await n.setReactionsAsync([{trigger:trigger==='AFTER_TIMEOUT'?{type:trigger,timeout:Number(n.getPluginData('prototypeTimeout')||2.6)}:{type:trigger},actions:[action]}]);
  connected++;
}
if(!PROOF_IDS.length){
  target.flowStartingPoints=[['A01','01 首次使用'],['H01','02 主 Agent 与全局导航'],['T01','03 创建与定制团队'],['L01','04 资料与工具'],['B03','05 后台任务与成果'],['B15','06 系统通知'],['D02','07 文档与版本'],['J06','08 简历工作台'],['J10','09 模拟面试'],['J15','10 投递记录'],['S01','11 历史与设置']].map(([key,name])=>({nodeId:built[key].id,name}));
}
if(PROOF_IDS.length){
  const shots=[];for(const key of PROOF_IDS){const bytes=await built[key].exportAsync({format:'PNG',constraint:{type:'SCALE',value:1}});shots.push({key,base64:figma.base64Encode(bytes)});}
  await fetch('http://127.0.0.1:8917/proof-shots',{method:'POST',body:JSON.stringify(shots)});
}
figma.viewport.scrollAndZoomIntoView(PROOF_IDS.length?Object.values(built).slice(0,6):[built.H01,built.T01,built.B03,built.L01]);
await fetch('http://127.0.0.1:8917/'+(PROOF_IDS.length?'proof-build':'app-build'),{method:'POST',body:JSON.stringify({pageId:target.id,created,scenes:Object.entries(built).map(([key,n])=>({key,id:n.id,name:n.name,source:n.getPluginData('sourceId')})),connected,problems})});
print('Created '+created.length+' editable screens; '+connected+' links; '+problems.length+' layout items to inspect.');
