// Development-only fixture authoring / independent reader. Never imported by Android.
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const [mode, modulePath, directory, ...files] = process.argv.slice(2);
if (!['create', 'render'].includes(mode) || !modulePath || !directory) {
  throw new Error('Usage: fixtures.mjs create|render artifact_tool.mjs directory [pptx ...]');
}
const { Presentation, PresentationFile, FileBlob } = await import(pathToFileURL(modulePath).href);
await fs.mkdir(directory, { recursive: true });
const write = async (name, blob) => fs.writeFile(path.join(directory, name), new Uint8Array(await blob.arrayBuffer()));
const text = (slide, value, top, size = 30) => {
  const shape = slide.shapes.add({ geometry: 'textbox', position: { left: 64, top, width: 828, height: 100 }, fill: 'none', line: { fill: 'none', width: 0 } });
  shape.text = value;
  shape.text.style = { typeface: 'Arial', fontSize: size, color: '#1E232C' };
};
const render = async (deck, stem) => {
  await fs.writeFile(path.join(directory, `${stem}-inspect.ndjson`), (await deck.inspect({ kind: 'slide,textbox,shape,image,table,chart,notes', maxChars: 200000 })).ndjson);
  for (const [i, slide] of deck.slides.items.entries()) {
    await write(`${stem}-${i + 1}.png`, await deck.export({ slide, format: 'png', scale: 1 }));
  }
};
if (mode === 'create') {
  const blank = Presentation.create({ slideSize: { width: 960, height: 540 } });
  blank.slides.add().speakerNotes.text = 'Template note';
  await (await PresentationFile.exportPptx(blank)).save(path.join(directory, 'blank-template.pptx'));
  const deck = Presentation.create({ slideSize: { width: 960, height: 540 } });
  for (const [title, body] of [
    ['Türkçe & English', 'İstanbul, ölçü, dönüşüm — 123.45 <test>'],
    ['العربية / فارسی', 'مرحبا بالعالم\nآزمایش متن و تصویر'],
    ['日本語 / 中文 / 한국어', '日本語の段落\n中文内容\n한국어 문장'],
    ['Unsupported editable objects', 'Native table and chart require a richer model'],
  ]) {
    const slide = deck.slides.add();
    slide.background.fill = '#FFFFFF';
    text(slide, title, 80, 36); text(slide, body, 200, 26);
    slide.speakerNotes.text = `Private notes: ${title}`;
  }
  const extra = deck.slides.items[3];
  extra.tables.add({ rows: 2, columns: 2, left: 64, top: 310, width: 360, height: 100, values: [['Item', 'Count'], ['Sample', '2']] });
  extra.charts.add('bar', { position: { left: 500, top: 300, width: 350, height: 200 }, categories: ['A', 'B'], series: [{ name: 'Count', values: [2, 3] }], hasLegend: false });
  // Asymmetric colored corners are an orientation / aspect-ratio test, not artwork.
  const marker = Presentation.create({ slideSize: { width: 160, height: 100 } });
  const markerSlide = marker.slides.add(); markerSlide.background.fill = '#FFFFFF';
  for (const [left, top, fill] of [[0, 0, '#F15D58'], [120, 0, '#0B1C3F'], [0, 60, '#64DCC8']]) {
    markerSlide.shapes.add({ geometry: 'rect', position: { left, top, width: 40, height: 40 }, fill, line: { fill: 'none', width: 0 } });
  }
  await write('orientation.png', await marker.export({ slide: markerSlide, format: 'png', scale: 1 }));
  deck.slides.items[0].images.add({ blob: new Uint8Array(await fs.readFile(path.join(directory, 'orientation.png'))), contentType: 'image/png', fit: 'contain', position: { left: 500, top: 320, width: 320, height: 200 }, alt: 'Orientation fixture' });
  await (await PresentationFile.exportPptx(deck)).save(path.join(directory, 'external-reference.pptx'));
  await render(deck, 'external-reference');
  console.log('Created template and four-slide independent reference; no runtime dependency added.');
} else {
  for (const file of files) {
    const deck = await PresentationFile.importPptx(await FileBlob.load(file));
    await render(deck, path.basename(file, '.pptx'));
    console.log(`${path.basename(file)}: imported and rendered ${deck.slides.items.length} slides`);
    if (path.basename(file) === 'classic-editable.pptx') {
      const inspect = await deck.inspect({ kind: 'textbox', maxChars: 200000 });
      const heading = inspect.ndjson.split('\n').filter(Boolean).map(line => JSON.parse(line)).find(item => item.kind === 'textbox' && item.name === 'Heading' && item.slideIndex === 0);
      if (!heading) throw new Error('Editable heading was not found by independent reader');
      deck.resolve(heading.id).text.replace('Türkçe & English', 'Türkçe & English + edited');
      await (await PresentationFile.exportPptx(deck)).save(path.join(directory, 'classic-edited-roundtrip.pptx'));
    }
  }
}
