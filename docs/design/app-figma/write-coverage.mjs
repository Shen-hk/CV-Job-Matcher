import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {groups,scenes} from './scene-manifest.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const temp=path.join(root,'tmp/figma-app-design-20261125');
const out=path.join(root,'output/tielink-app-design');
const qa=JSON.parse(await fs.readFile(path.join(temp,'final-qa.json'),'utf8'));
const locations=JSON.parse(await fs.readFile(path.join(temp,'design-final-locations.json'),'utf8'));
const inventory=JSON.parse(await fs.readFile(path.join(temp,'figma-inventory.json'),'utf8'));
const ids=Object.fromEntries(qa.screens.map(s=>[s.key,s]));
const figma=id=>'https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id='+id.replace(':','-');
const prototype=id=>'https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id='+id.replace(':','-')+'&page-id='+encodeURIComponent(qa.pageId)+'&starting-point-node-id='+encodeURIComponent(id)+'&scaling=min-zoom&content-scaling=fixed';
const checks=[];
for(const name of ['team','task','resources','career','onboarding']){
  try{checks.push({flow:name,steps:JSON.parse(await fs.readFile(path.join(temp,'prototype-'+name+'-checks.json'),'utf8'))});}catch{}
}
const retained=inventory.pages.filter(p=>/^0[4-9] ·|^10 ·/.test(p.name));
const sourceCount=scenes.filter(s=>s.source).length;
const data={date:'2026-11-25',fileKey:'9Wp2MFkHvQhRoWPA1IQ2RN',pageId:qa.pageId,
  figma:figma(locations.preview),overview:figma(locations.overview),prototype:prototype(locations.agent),
  screens:scenes.map(s=>({id:s.id,title:s.title,group:groups.find(g=>g[0]===s.id[0])[1],nodeId:ids[s.id]?.id,
    figma:figma(ids[s.id].id),layout:s.layout,source:ids[s.id].source||null})),
  flows:qa.flows.map(f=>({...f,url:prototype(f.nodeId)})),
  validation:{screenCount:qa.screens.length,newScreens:scenes.length-sourceCount,reusedScreens:sourceCount,
    componentInstances:qa.instances,editableTextLayers:qa.texts,sceneInteractions:qa.interactions,
    missingDestinations:qa.missing,incorrectScreenSizes:qa.wrongSizes,missingFonts:qa.missingFonts,textOverflow:qa.overflow,
    browserChecks:checks},
  retainedSpecialistStates:retained.map(p=>({name:p.name,pageId:p.id,frames:p.frames.filter(f=>f.name!=='分区导览').length,url:figma(p.id)}))};
await fs.mkdir(out,{recursive:true});
await fs.writeFile(path.join(out,'app-scenes.json'),JSON.stringify(data,null,2));
await fs.writeFile(path.join(out,'verification.json'),JSON.stringify({date:data.date,figma:data.figma,prototype:data.prototype,...data.validation},null,2));
const lines=[
  '# TieLink：整套 APP 场景与 Figma 交接',
  '',
  '> 2026-11-25。本轮交付以 Figma 的 APP 页面、场景状态和原型连接为主，未继续扩展 Android 功能。',
  '',
  `[查看整套 APP 预览](${data.figma}) · [设计总览](${data.overview}) · [打开可点击原型](${data.prototype})`,
  '',
  '设计只写入原文件的新页面 **12 · APP 完整体验与场景 · 11.25**。沿用原文件的 Noto Sans SC 中文字体、色彩变量、图标、按钮与专业工作台。原有页面保留，01b 的团队首页稿继续作为对照。',
  '',
  `本页共 ${scenes.length} 个手机页面与状态，其中 ${scenes.length-sourceCount} 个新增场景、${sourceCount} 个复用的专业工作台状态；另有 APP 总览和八场景预览。主 Agent 保持默认入口；任务、团队、工具、资料与成果均有独立页面。`,
  '',
  '## 导航与用户旅程',
  '',
  '- 一级入口：Agent、任务、团队、资料；复杂编辑、预览和专业工作台从相应上下文进入。',
  '- 没有团队也能开始；创建、编辑和选用团队不会启动业务任务。',
  '- 委托之后可以离开聊天；任务页与通知呈现动作、可靠阶段成果和处理入口。',
  '- 文档修改、HTML/PDF 查看、版本关系、采用为正式资料和文件导出各有明确状态。',
  '- 团队快照、资料引用、会话归属、输入保留、失败恢复和未保存修改都在场景中体现。',
  '',
  '## 场景分组',
  '',
  '| 分组 | 页面与状态 | 范围 |',
  '| --- | ---: | --- |',
  ...groups.map(([key,title,desc])=>`| ${key} · ${title} | ${scenes.filter(s=>s.id[0]===key).length} | ${desc} |`),
  '',
  '## 可点击主流程起点',
  '',
  ...data.flows.map(f=>`- [${f.name}](${f.url})`),
  '',
  '## 保留的专业分支',
  '',
  '以下原页面完整保留；本轮把典型入口和主干状态接入新 APP 原型，不重复重画整套专业界面。',
  '',
  ...data.retainedSpecialistStates.map(p=>`- [${p.name}](${p.url})：${p.frames} 个既有页面/状态（不含分区导览）。`),
  '',
  '## 验证与交付边界',
  '',
  `已检查 ${qa.screens.length} 个手机画板均为 390 × 844；画板中有 ${qa.instances} 个组件实例和 ${qa.texts} 个可编辑文字图层。画面由原生图层与组件构成，没有将整屏截图贴回 Figma 充当设计。`,
  '',
  `检查到 ${qa.interactions} 项手机场景内交互，目标节点缺失 ${qa.missing.length} 项、缺失字体 ${qa.missingFonts.length} 项、文本高度检查异常 ${qa.overflow.length} 项。画布总览入口另行提供导航，不把总览或预览副本计为新手机场景。`,
  '',
  `浏览器实际点通记录：${checks.map(c=>c.flow+' '+c.steps.length+' 步').join('；')||'核验记录尚待汇总'}。明细见输出目录 verification.json。`,
  '',
  'Figma 原型使用示例内容与预设状态。它用于审阅页面、入口、返回路径和状态关系；文字输入、系统通知、模型调用、文件读写与 Android 生命周期不会因此成为已实现功能。',
  '',
  '## 逐页索引',
  '',
  '| 编号 | 页面 / 状态 | 设计位置 |',
  '| --- | --- | --- |',
  ...data.screens.map(s=>`| ${s.id} | ${s.title} | [打开 Figma](${s.figma}) |`),
  '',
];
await fs.writeFile(path.join(root,'docs/APP_DESIGN_COVERAGE.md'),lines.join('\n'));
console.log(JSON.stringify({screens:scenes.length,newScreens:scenes.length-sourceCount,reused:sourceCount,browserFlows:checks.map(c=>c.flow),figma:data.figma,prototype:data.prototype},null,2));
