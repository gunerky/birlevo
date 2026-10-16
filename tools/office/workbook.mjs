// Development-only fixture/render/roundtrip using bundled Artifact Tool. Not an Android dependency.
import fs from 'node:fs/promises';
import path from 'node:path';
import {Workbook, SpreadsheetFile, FileBlob} from '@oai/artifact-tool';
const [mode, directory, output]=process.argv.slice(2);
if (!['create','verify'].includes(mode) || !directory) throw new Error('Usage: workbook.mjs create|verify directory [output]');
await fs.mkdir(directory,{recursive:true});
async function render(book, sheet, target, range) {
  const png=await book.render({sheetName:sheet,range,scale:2,format:'png'});
  await fs.writeFile(target,new Uint8Array(await png.arrayBuffer()));
}
if (mode==='create') {
  const book=Workbook.create();
  const data=book.worksheets.add('Data');
  const arabic=book.worksheets.add('العربية');
  data.getRange('A1:D4').values=[['Code','Input','Double','Enabled'],['00123',10,null,true],["'=2+2",0,null,false],['İstanbul & <text>',null,null,null]];
  data.getRange('C2').formulas=[['=B2*2']];
  data.getRange('E1').values=[['Date']];
  data.getRange('E2').values=[[new Date('2026-10-14T00:00:00Z')]];
  data.getRange('A5:D5').merge();data.getRange('A5').values=[['Merged label']];
  data.getRange('A1:E5').format.font={name:'Arial',size:12};
  data.getRange('A1:D1').format.font.bold=true;
  data.getRange('A1:E5').format.columnWidth=22;
  data.getRange('A1:E5').format.rowHeight=24;
  data.getRange('A1:A5').format.numberFormat='@';
  data.getRange('E2').format.numberFormat='yyyy-mm-dd';
  arabic.getRange('A1:B2').values=[['العربية','فارسی'],['١٢٣','سلام‌دنیا']];
  arabic.getRange('A1:B2').format.font={name:'Arial',size:12};
  arabic.getRange('A1:B2').format.columnWidth=24;
  arabic.getRange('A1:B2').format.rowHeight=28;
  book.recalculate();
  if(data.getRange('C2').values[0][0]!==20)throw new Error('Synthetic formula cache mismatch');
  await (await SpreadsheetFile.exportXlsx(book)).save(path.join(directory,'rich.xlsx'));
  await render(book,'Data',path.join(directory,'source-data.png'),'A1:E5');
  await render(book,'العربية',path.join(directory,'source-rtl.png'),'A1:B2');
  console.log('Synthetic workbook exported and both sheets rendered.');
} else {
  if(!output)throw new Error('Output required');
  await fs.mkdir(output,{recursive:true});
  const book=await SpreadsheetFile.importXlsx(await FileBlob.load(path.join(directory,'native.xlsx')));
  const data=book.worksheets.getItem('Data');
  const values=data.getRange('A1:C4').values;
  if(values[1][0]!=='00123'||values[1][1]!=='=2+2'||values[3][1]!=='0')throw new Error('Text type changed');
  if(data.getRange('A1:C4').formulas.flat().some(Boolean))throw new Error('Literal text became formula');
  await fs.writeFile(path.join(output,'native-inspect.ndjson'),(await book.inspect({kind:'region',sheetId:'Data',range:'A1:C4',maxChars:5000})).ndjson);
  await render(book,'Data',path.join(output,'native.png'),'A1:C4');
  data.getRange('A2').values=[['Edited value']];
  book.recalculate();
  await (await SpreadsheetFile.exportXlsx(book)).save(path.join(output,'edited.xlsx'));
  const reopened=await SpreadsheetFile.importXlsx(await FileBlob.load(path.join(output,'edited.xlsx')));
  const after=reopened.worksheets.getItem('Data').getRange('A1:C4').values;
  const expected=values.map(row=>row.slice());expected[1][0]='Edited value';
  if(JSON.stringify(after)!==JSON.stringify(expected))throw new Error('Roundtrip changed untouched cells');
  console.log('Independent XLSX string values, formula isolation, render and edit/export passed.');
}
