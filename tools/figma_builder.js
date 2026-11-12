// Код сборщика, который хранится в файле Figma (sharedPluginData 'tpc'/'builder') и выполняется через new Function.
const STY = { 'Unbounded:500': 'Medium', 'Unbounded:700': 'Bold', 'Unbounded:800': 'ExtraBold', 'Inter:400': 'Regular', 'Inter:500': 'Medium', 'Inter:600': 'Semi Bold', 'Inter:700': 'Bold' };
const hex = h => ({ r: parseInt(h.slice(1, 3), 16) / 255, g: parseInt(h.slice(3, 5), 16) / 255, b: parseInt(h.slice(5, 7), 16) / 255 });
const getA = () => JSON.parse(figma.root.getSharedPluginData('tpc', 'assets') || '{}');
async function fixFonts(fr, seq) {
  const texts = fr.findAllWithCriteria({ types: ['TEXT'] });
  const loaded = new Set();
  const load = async f => { const k = f.family + '|' + f.style; if (!loaded.has(k)) { loaded.add(k); await figma.loadFontAsync({ family: f.family, style: f.style }); } };
  for (const t of texts) if (t.fontName !== figma.mixed) await load(t.fontName);
  let n = 0;
  for (let i = 0; i < texts.length && i < seq.length; i++) {
    const [fam, w] = seq[i].split(':'); const f = { family: fam, style: STY[fam + ':' + w] || 'Regular' };
    await load(f); texts[i].fontName = f; n++;
  }
  return { texts: texts.length, seq: seq.length, fontsSet: n };
}
async function placeAssets(fr) {
  const A = getA(); const marks = fr.findAll(n => n.name.startsWith('@A:')); const missing = []; let placed = 0;
  for (const m of marks) {
    const p = m.name.slice(3).split(':'); const key = p[0];
    const parent = m.parent, idx = parent.children.indexOf(m); const x = m.x, y = m.y, w = m.width, h = m.height;
    const comp = A[key] ? await figma.getNodeByIdAsync(A[key]) : null;
    if (!comp) { missing.push(key); m.remove(); continue; }
    const inst = comp.createInstance(); let node = inst;
    if (key.startsWith('i-')) {
      inst.rescale(w / inst.width); inst.name = key.slice(2);
      const col = hex(p[1]); for (const v of inst.findAll(n => 'strokes' in n && n.strokes.length)) v.strokes = [{ type: 'SOLID', color: col }];
      parent.insertChild(idx, inst); inst.x = x; inst.y = y;
    } else if (p[1] === 'fit') {
      inst.rescale(Math.min(w / inst.width, h / inst.height)); parent.insertChild(idx, inst);
      inst.x = x + (w - inst.width) / 2; inst.y = y + (h - inst.height) / 2;
    } else {
      inst.rescale(Math.max(w / inst.width, h / inst.height));
      const box = figma.createFrame(); box.name = key; box.resize(w, h); box.fills = []; box.clipsContent = true;
      if (+p[2]) box.cornerRadius = +p[2];
      box.appendChild(inst); inst.x = (w - inst.width) / 2; inst.y = p[1] === 'bottom' ? h - inst.height : (h - inst.height) / 2;
      parent.insertChild(idx, box); box.x = x; box.y = y; node = box;
    }
    m.remove(); placed++;
  }
  return { placed, missing };
}
function buf(name, i, part) { figma.root.setSharedPluginData('tpc', 'buf:' + name + ':' + i, part); }
function readBuf(name) { let s = '', i = 0, c; while ((c = figma.root.getSharedPluginData('tpc', 'buf:' + name + ':' + i))) { s += c; figma.root.setSharedPluginData('tpc', 'buf:' + name + ':' + i, ''); i++; } return s; }
async function build({ name, svg, fonts, x, y, pageId }) {
  if (pageId) await figma.setCurrentPageAsync(await figma.getNodeByIdAsync(pageId));
  if (!svg) svg = readBuf(name);
  const old = figma.currentPage.findChild(n => n.name === name); if (old) old.remove();
  const fr = figma.createNodeFromSvg(svg); fr.name = name; fr.x = x; fr.y = y; fr.clipsContent = true;
  const f = await fixFonts(fr, fonts); const a = await placeAssets(fr);
  return { id: fr.id, name, w: Math.round(fr.width), h: Math.round(fr.height), ...f, ...a };
}
return { build, buf, hex, getA };
