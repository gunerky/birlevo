// Independent reader and roundtrip editor for synthetic files made by the public editable export engine.
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL} from 'node:url';
const [modulePath,nativeDir,outDir]=process.argv.slice(2);
if (!modulePath || !nativeDir || !outDir) throw new Error('Usage: verify_bidi.mjs artifact_tool.mjs native-directory output-directory');
const {PresentationFile,FileBlob}=await import(pathToFileURL(modulePath).href);
const expected=JSON.parse(await fs.readFile(path.join(nativeDir,'expected.json'),'utf8')).projects[0];
await fs.mkdir(outDir,{recursive:true});
const records=[];
const files=(await fs.readdir(nativeDir)).filter(v=>v.endsWith('.pptx')).sort();
if (files.join(',') !== 'CLASSIC-positioned.pptx,OCEAN-positioned.pptx,WARM-positioned.pptx')
  throw new Error('Expected three native theme decks');
function text(rows,role,index) {
  const whole=rows.find(v=>v.kind==='textbox'&&v.slideIndex===index&&v.name===role);
  if(whole)return whole.text;
  return rows.filter(v=>v.kind==='textbox'&&v.slideIndex===index&&v.name.startsWith(role+' part '))
    .sort((a,b)=>Number(a.name.split(' ').at(-1))-Number(b.name.split(' ').at(-1)))
    .map(v=>v.text).join('');
}
for(const file of files) {
  const deck=await PresentationFile.importPptx(await FileBlob.load(path.join(nativeDir,file)));
  const raw=(await deck.inspect({kind:'slide,textbox,shape',maxChars:200000})).ndjson;
  await fs.writeFile(path.join(outDir,file+'.ndjson'),raw);
  const rows=raw.split('\n').filter(Boolean).map(JSON.parse);
  if(deck.slides.items.length!==expected.slides.length) throw new Error('Slide count changed');
  for(const [i,slide] of deck.slides.items.entries()) {
    if(text(rows,'Heading',i)!==expected.slides[i].heading||text(rows,'Body',i)!==expected.slides[i].body||text(rows,'Project title',i)!==expected.title)
      throw new Error(`Logical source text lost: ${file}/${i}: ${JSON.stringify(text(rows,'Body',i))}`);
    const png=await deck.export({slide,format:'png',scale:1});
    await fs.writeFile(path.join(outDir,file.replace('.pptx',`-${i+1}.png`)),new Uint8Array(await png.arrayBuffer()));
  }
  const words=rows.filter(v=>v.kind==='textbox'&&v.slideIndex===0&&v.name.startsWith('Body part '));
  if(words[0].position.left<=words[1].position.left)throw new Error('Arabic word positions reversed');
  records.push({file,logical_text:true,first_arabic_word_right:true,slides:deck.slides.items.length});
  if(file.startsWith('CLASSIC')) {
    const heading=rows.find(v=>v.kind==='textbox'&&v.slideIndex===0&&v.name==='Heading part 0');
    deck.resolve(heading.id).text.replace('مرحبا','أهلا');
    const target=path.join(outDir,'edited-rtl.pptx');
    await (await PresentationFile.exportPptx(deck)).save(target);
    const reopened=await PresentationFile.importPptx(await FileBlob.load(target));
    const checks=(await reopened.inspect({kind:'textbox',maxChars:200000})).ndjson.split('\n').filter(Boolean).map(JSON.parse);
    if(!checks.some(v=>v.name==='Heading part 0'&&v.text==='أهلا '))throw new Error('RTL word edit lost');
    if(reopened.slides.items.length!==expected.slides.length)throw new Error('Roundtrip slide count changed');
    for(const [i,slide] of expected.slides.entries()) {
      const heading=i===0?slide.heading.replace('مرحبا','أهلا'):slide.heading;
      if(text(checks,'Heading',i)!==heading||text(checks,'Body',i)!==slide.body||text(checks,'Project title',i)!==expected.title)
        throw new Error('Roundtrip changed untouched source text');
    }
    const png=await reopened.export({slide:reopened.slides.items[0],format:'png',scale:1});
    await fs.writeFile(path.join(outDir,'edited-rtl.png'),new Uint8Array(await png.arrayBuffer()));
  }
}
await fs.writeFile(path.join(outDir,'verification.json'),JSON.stringify({records,roundtrip_edit:true,powerpoint_tested:false},null,2));
console.log('Independent logical text, physical word order, render and RTL roundtrip passed.');
