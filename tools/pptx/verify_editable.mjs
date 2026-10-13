// Independent development-only reader/editor. Not shipped in Android or called by Gradle.
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const [modulePath, nativeDir, outDir] = process.argv.slice(2);
if(!modulePath || !nativeDir || !outDir) throw new Error('Usage: verify_editable.mjs artifact_tool.mjs native-directory output-directory');
const {PresentationFile, FileBlob} = await import(pathToFileURL(modulePath).href);
await fs.mkdir(outDir,{recursive:true});
const expected=JSON.parse(await fs.readFile(path.join(nativeDir,'expected.json'),'utf8')).projects[0];
const results=[];
for(const name of (await fs.readdir(nativeDir)).filter(n=>n.endsWith('.pptx')).sort()) {
  const deck=await PresentationFile.importPptx(await FileBlob.load(path.join(nativeDir,name)));
  if(deck.slides.items.length!==expected.slides.length) throw new Error('Slide count changed');
  const inspect=(await deck.inspect({kind:'slide,textbox,shape,image,notes',maxChars:200000})).ndjson;
  await fs.writeFile(path.join(outDir,name.replace('.pptx','-inspect.ndjson')),inspect);
  const rows=inspect.split('\n').filter(Boolean).map(v=>JSON.parse(v));
  const notes=name.includes('notes-true');
  for(const [i,slide] of deck.slides.items.entries()) {
    const text=Object.fromEntries(rows.filter(v=>v.kind==='textbox' && v.slideIndex===i).map(v=>[v.name,v.text]));
    if(text.Heading!==expected.slides[i].heading || text.Body!==expected.slides[i].body) throw new Error(`Text changed: ${name}/${i}`);
    const actualNotes=rows.find(v=>v.kind==='notes' && v.slideIndex===i)?.text ?? '';
    if(actualNotes!==(notes?expected.slides[i].notes:'')) throw new Error('Notes mismatch');
    const png=await deck.export({slide,format:'png',scale:1});
    await fs.writeFile(path.join(outDir,name.replace('.pptx',`-${i+1}.png`)),new Uint8Array(await png.arrayBuffer()));
  }
  results.push({file:name,slides:deck.slides.items.length,editable_text:true,notes});
  if(name==='CLASSIC-notes-true.pptx') {
    const heading=rows.find(v=>v.kind==='textbox' && v.name==='Heading' && v.slideIndex===0);
    deck.resolve(heading.id).text.replace(expected.slides[0].heading,'Edited title');
    const target=path.join(outDir,'edited-roundtrip.pptx');
    await (await PresentationFile.exportPptx(deck)).save(target);
    const reopened=await PresentationFile.importPptx(await FileBlob.load(target));
    const reopenedRows=(await reopened.inspect({kind:'textbox,notes',maxChars:200000})).ndjson.split('\n').filter(Boolean).map(v=>JSON.parse(v));
    if(!reopenedRows.some(v=>v.kind==='textbox' && v.name==='Heading' && v.slideIndex===0 && v.text==='Edited title')) throw new Error('Roundtrip edit lost');
    if(reopenedRows.find(v=>v.kind==='notes' && v.slideIndex===0)?.text!==expected.slides[0].notes) throw new Error('Roundtrip notes lost');
    const png=await reopened.export({slide:reopened.slides.items[0],format:'png',scale:1});
    await fs.writeFile(path.join(outDir,'edited-roundtrip.png'),new Uint8Array(await png.arrayBuffer()));
  }
  console.log(`${name}: independent text/notes/render checks passed`);
}
if(results.length!==6) throw new Error('Expected six production fixtures');
await fs.writeFile(path.join(outDir,'verification.json'),JSON.stringify({results,roundtrip_edit:true,powerpoint_tested:false},null,2));
