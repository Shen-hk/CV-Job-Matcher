import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=dirname(fileURLToPath(import.meta.url));
const out=resolve(root,'../../../output/tielink-agent-prototype');
const [entry,css,js]=await Promise.all(['index.html','styles.css','app.js'].map(name=>readFile(resolve(root,name),'utf8')));
if(!entry.includes('href="styles.css"')||!entry.includes('src="app.js"'))throw new Error('Expected local entry references');
const bundled=entry.replace('<link rel="stylesheet" href="styles.css">',`<style>\n${css}\n</style>`).replace('<script src="app.js"></script>',`<script>\n${js.replace(/<\/script/gi,'<\\/script')}\n</script>`);
await mkdir(out,{recursive:true});
await writeFile(resolve(out,'TieLink-Agent-Prototype.html'),bundled,'utf8');
console.log(`Standalone prototype: ${resolve(out,'TieLink-Agent-Prototype.html')}`);
