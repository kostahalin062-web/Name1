var TINF_OK = 0;
var TINF_DATA_ERROR = -3;
function Tree() {
this.table = new Uint16Array(16);   
this.trans = new Uint16Array(288);  
}
function Data(source, dest) {
this.source = source;
this.sourceIndex = 0;
this.tag = 0;
this.bitcount = 0;
this.dest = dest;
this.destLen = 0;
this.ltree = new Tree();  
this.dtree = new Tree();  
}
var sltree = new Tree();
var sdtree = new Tree();
var length_bits = new Uint8Array(30);
var length_base = new Uint16Array(30);
var dist_bits = new Uint8Array(30);
var dist_base = new Uint16Array(30);
var clcidx = new Uint8Array([
16, 17, 18, 0, 8, 7, 9, 6,
10, 5, 11, 4, 12, 3, 13, 2,
14, 1, 15
]);
var code_tree = new Tree();
var lengths = new Uint8Array(288 + 32);
function tinf_build_bits_base(bits, base, delta, first) {
var i, sum;
for (i = 0; i < delta; ++i) bits[i] = 0;
for (i = 0; i < 30 - delta; ++i) bits[i + delta] = i / delta | 0;
for (sum = first, i = 0; i < 30; ++i) {
base[i] = sum;
sum += 1 << bits[i];
}
}
function tinf_build_fixed_trees(lt, dt) {
var i;
for (i = 0; i < 7; ++i) lt.table[i] = 0;
lt.table[7] = 24;
lt.table[8] = 152;
lt.table[9] = 112;
for (i = 0; i < 24; ++i) lt.trans[i] = 256 + i;
for (i = 0; i < 144; ++i) lt.trans[24 + i] = i;
for (i = 0; i < 8; ++i) lt.trans[24 + 144 + i] = 280 + i;
for (i = 0; i < 112; ++i) lt.trans[24 + 144 + 8 + i] = 144 + i;
for (i = 0; i < 5; ++i) dt.table[i] = 0;
dt.table[5] = 32;
for (i = 0; i < 32; ++i) dt.trans[i] = i;
}
var offs = new Uint16Array(16);
function tinf_build_tree(t, lengths, off, num) {
var i, sum;
for (i = 0; i < 16; ++i) t.table[i] = 0;
for (i = 0; i < num; ++i) t.table[lengths[off + i]]++;
t.table[0] = 0;
for (sum = 0, i = 0; i < 16; ++i) {
offs[i] = sum;
sum += t.table[i];
}
for (i = 0; i < num; ++i) {
if (lengths[off + i]) t.trans[offs[lengths[off + i]]++] = i;
}
}
function tinf_getbit(d) {
if (!d.bitcount--) {
d.tag = d.source[d.sourceIndex++];
d.bitcount = 7;
}
var bit = d.tag & 1;
d.tag >>>= 1;
return bit;
}
function tinf_read_bits(d, num, base) {
if (!num)
return base;
while (d.bitcount < 24) {
d.tag |= d.source[d.sourceIndex++] << d.bitcount;
d.bitcount += 8;
}
var val = d.tag & (0xffff >>> (16 - num));
d.tag >>>= num;
d.bitcount -= num;
return val + base;
}
function tinf_decode_symbol(d, t) {
while (d.bitcount < 24) {
d.tag |= d.source[d.sourceIndex++] << d.bitcount;
d.bitcount += 8;
}
var sum = 0, cur = 0, len = 0;
var tag = d.tag;
do {
cur = 2 * cur + (tag & 1);
tag >>>= 1;
++len;
sum += t.table[len];
cur -= t.table[len];
} while (cur >= 0);
d.tag = tag;
d.bitcount -= len;
return t.trans[sum + cur];
}
function tinf_decode_trees(d, lt, dt) {
var hlit, hdist, hclen;
var i, num, length;
hlit = tinf_read_bits(d, 5, 257);
hdist = tinf_read_bits(d, 5, 1);
hclen = tinf_read_bits(d, 4, 4);
for (i = 0; i < 19; ++i) lengths[i] = 0;
for (i = 0; i < hclen; ++i) {
var clen = tinf_read_bits(d, 3, 0);
lengths[clcidx[i]] = clen;
}
tinf_build_tree(code_tree, lengths, 0, 19);
for (num = 0; num < hlit + hdist;) {
var sym = tinf_decode_symbol(d, code_tree);
switch (sym) {
case 16:
var prev = lengths[num - 1];
for (length = tinf_read_bits(d, 2, 3); length; --length) {
lengths[num++] = prev;
}
break;
case 17:
for (length = tinf_read_bits(d, 3, 3); length; --length) {
lengths[num++] = 0;
}
break;
case 18:
for (length = tinf_read_bits(d, 7, 11); length; --length) {
lengths[num++] = 0;
}
break;
default:
lengths[num++] = sym;
break;
}
}
tinf_build_tree(lt, lengths, 0, hlit);
tinf_build_tree(dt, lengths, hlit, hdist);
}
function tinf_inflate_block_data(d, lt, dt) {
while (1) {
var sym = tinf_decode_symbol(d, lt);
if (sym === 256) {
return TINF_OK;
}
if (sym < 256) {
d.dest[d.destLen++] = sym;
} else {
var length, dist, offs;
var i;
sym -= 257;
length = tinf_read_bits(d, length_bits[sym], length_base[sym]);
dist = tinf_decode_symbol(d, dt);
offs = d.destLen - tinf_read_bits(d, dist_bits[dist], dist_base[dist]);
for (i = offs; i < offs + length; ++i) {
d.dest[d.destLen++] = d.dest[i];
}
}
}
}
function tinf_inflate_uncompressed_block(d) {
var length, invlength;
var i;
while (d.bitcount > 8) {
d.sourceIndex--;
d.bitcount -= 8;
}
length = d.source[d.sourceIndex + 1];
length = 256 * length + d.source[d.sourceIndex];
invlength = d.source[d.sourceIndex + 3];
invlength = 256 * invlength + d.source[d.sourceIndex + 2];
if (length !== (~invlength & 0x0000ffff))
return TINF_DATA_ERROR;
d.sourceIndex += 4;
for (i = length; i; --i)
d.dest[d.destLen++] = d.source[d.sourceIndex++];
d.bitcount = 0;
return TINF_OK;
}
function tinf_uncompress(source, dest) {
var d = new Data(source, dest);
var bfinal, btype, res;
do {
bfinal = tinf_getbit(d);
btype = tinf_read_bits(d, 2, 0);
switch (btype) {
case 0:
res = tinf_inflate_uncompressed_block(d);
break;
case 1:
res = tinf_inflate_block_data(d, sltree, sdtree);
break;
case 2:
tinf_decode_trees(d, d.ltree, d.dtree);
res = tinf_inflate_block_data(d, d.ltree, d.dtree);
break;
default:
res = TINF_DATA_ERROR;
}
if (res !== TINF_OK)
throw new Error('Data error');
} while (!bfinal);
if (d.destLen < d.dest.length) {
if (typeof d.dest.slice === 'function')
return d.dest.slice(0, d.destLen);
else
return d.dest.subarray(0, d.destLen);
}
return d.dest;
}
tinf_build_fixed_trees(sltree, sdtree);
tinf_build_bits_base(length_bits, length_base, 4, 3);
tinf_build_bits_base(dist_bits, dist_base, 2, 1);
length_bits[28] = 0;
length_base[28] = 258;

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
function utf8(b) { let s = '', i = 0; while (i < b.length) { let c = b[i++]; if (c > 127) { if (c > 223 && c < 240) c = ((c & 15) << 12) | ((b[i++] & 63) << 6) | (b[i++] & 63); else if (c > 239) { c = ((c & 7) << 18) | ((b[i++] & 63) << 12) | ((b[i++] & 63) << 6) | (b[i++] & 63); } else c = ((c & 31) << 6) | (b[i++] & 63); } s += String.fromCodePoint(c); } return s; }
function unpack(packed) { const k = packed.indexOf(':'); const size = +packed.slice(0, k); const src = figma.base64Decode(packed.slice(k + 1)); const out = new Uint8Array(size); tinf_uncompress(src, out); return JSON.parse(utf8(out)); }
async function buildPacked(packed, pageId) {
  const d = unpack(packed);
  const r = await build({ name: d.n, svg: d.s, fonts: d.f.split(','), x: d.x, y: d.y, pageId });
  figma.root.setSharedPluginData('tpc', 'links:' + d.n, JSON.stringify(d.l));
  return r;
}
return { build, buildPacked, unpack, buf, hex, getA };
