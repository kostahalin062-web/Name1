// Конвертер отрисованной HTML-страницы прототипа в SVG с редактируемыми слоями (для импорта в Figma)
// usage: node dom2svg.js outDir page[?q]=name[@cropHeight] ...
// Для каждой страницы создаёт name.svg и name.links.json (кликабельные области → целевая страница)
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');

function extract() {
  const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  let uid = 0; const defs = []; const sx = window.scrollX, sy = window.scrollY;
  const px = v => parseFloat(v) || 0;
  const rgba = c => { const m = c && c.match(/rgba?\(([^)]+)\)/); if (!m) return null; const p = m[1].split(/[ ,/]+/).filter(Boolean).map(Number); return { c: `rgb(${p[0]},${p[1]},${p[2]})`, a: p.length > 3 ? p[3] : 1 }; };
  const fillAttr = (c, name = 'fill') => { const k = rgba(c); if (!k || k.a === 0) return null; return `${name}="${k.c}"` + (k.a < 1 ? ` ${name}-opacity="${+k.a.toFixed(3)}"` : ''); };
  function splitTop(s) { const out = []; let d = 0, cur = ''; for (const ch of s) { if (ch === '(') d++; if (ch === ')') d--; if (ch === ',' && d === 0) { out.push(cur.trim()); cur = ''; } else cur += ch; } if (cur.trim()) out.push(cur.trim()); return out; }
  function gradient(img, box) {
    // linear-gradient / radial-gradient → id в defs
    const m = img.match(/^(linear|radial)-gradient\((.*)\)$/s); if (!m) return null;
    const parts = splitTop(m[2]); let angle = 180, first = parts[0], stopsArr = parts, rad = null;
    if (m[1] === 'linear') { if (/deg$/.test(first)) { angle = parseFloat(first); stopsArr = parts.slice(1); } else if (/^to /.test(first)) { angle = { 'to right': 90, 'to left': 270, 'to top': 0, 'to bottom': 180 }[first] ?? 180; stopsArr = parts.slice(1); } }
    else { if (!/^rgb/.test(first)) { rad = first; stopsArr = parts.slice(1); } }
    const stops = stopsArr.map((s, i) => { const cm = s.match(/rgba?\([^)]+\)/); const pm = s.replace(cm ? cm[0] : '', '').match(/([\d.]+)%/); const k = rgba(cm && cm[0]); return k ? `<stop offset="${pm ? pm[1] / 100 : i / Math.max(1, stopsArr.length - 1)}" stop-color="${k.c}" stop-opacity="${k.a}"/>` : ''; }).join('');
    const id = 'g' + (++uid);
    if (m[1] === 'linear') {
      const a = (angle - 90) * Math.PI / 180, w = box.w, h = box.h; const L = Math.abs(w * Math.cos(a)) + Math.abs(h * Math.sin(a));
      const cx = box.x + w / 2, cy = box.y + h / 2, dx = Math.cos(a) * L / 2, dy = Math.sin(a) * L / 2;
      defs.push(`<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${(cx - dx).toFixed(1)}" y1="${(cy - dy).toFixed(1)}" x2="${(cx + dx).toFixed(1)}" y2="${(cy + dy).toFixed(1)}">${stops}</linearGradient>`);
    } else {
      let rx = box.w / 2, ry = box.h / 2, cx = box.x + box.w / 2, cy = box.y + box.h / 2;
      const rm = rad && rad.match(/([\d.]+)px\s+([\d.]+)px\s+at\s+([\d.]+)%\s+([\d.]+)%/);
      if (rm) { rx = +rm[1]; ry = +rm[2]; cx = box.x + box.w * rm[3] / 100; cy = box.y + box.h * rm[4] / 100; }
      defs.push(`<radialGradient id="${id}" gradientUnits="userSpaceOnUse" cx="${cx.toFixed(1)}" cy="${cy.toFixed(1)}" r="${rx.toFixed(1)}" gradientTransform="translate(${cx.toFixed(1)} ${cy.toFixed(1)}) scale(1 ${(ry / rx).toFixed(3)}) translate(${-cx.toFixed(1)} ${-cy.toFixed(1)})">${stops}</radialGradient>`);
    }
    return id;
  }
  function radius(st, w, h) { const r = st.borderTopLeftRadius; const v = /%$/.test(r) ? Math.min(w, h) * parseFloat(r) / 100 : px(r); return Math.min(v, w / 2, h / 2); }
  function bgLayers(st, box) {
    const out = []; const c = fillAttr(st.backgroundColor); if (c) out.push(c);
    if (st.backgroundImage && st.backgroundImage !== 'none' && !/url\(/.test(st.backgroundImage) && st.webkitBackgroundClip !== 'text') {
      splitTop(st.backgroundImage).reverse().forEach(l => { const id = gradient(l, box); if (id) out.push(`fill="url(#${id})"`); });
    }
    return out;
  }
  const baseCache = {};
  function baseline(st) {
    const key = [st.fontFamily, st.fontWeight, st.fontSize].join('|'); if (key in baseCache) return baseCache[key];
    const s = document.createElement('span'); s.style.cssText = `font-family:${st.fontFamily};font-weight:${st.fontWeight};font-size:${st.fontSize};line-height:normal;position:absolute;left:-9999px;top:0`;
    s.innerHTML = 'Hg<span style="display:inline-block;width:1px;height:0;vertical-align:baseline"></span>'; document.body.appendChild(s);
    const v = s.lastChild.getBoundingClientRect().bottom - s.getBoundingClientRect().top; s.remove(); return baseCache[key] = v;
  }
  function fontAttrs(st) {
    const fam = st.fontFamily.split(',')[0].replace(/["']/g, '').trim();
    let a = `font-family="${fam}" font-size="${px(st.fontSize)}" font-weight="${st.fontWeight}"`;
    if (st.fontStyle === 'italic') a += ' font-style="italic"';
    if (px(st.letterSpacing)) a += ` letter-spacing="${px(st.letterSpacing)}"`;
    return a;
  }
  function tx(text, st) { return st.textTransform === 'uppercase' ? text.toUpperCase() : st.textTransform === 'lowercase' ? text.toLowerCase() : text; }
  function textFill(st, el) {
    if (st.webkitBackgroundClip === 'text' && st.backgroundImage !== 'none') {
      const r = el.getBoundingClientRect(); const id = gradient(splitTop(st.backgroundImage)[0], { x: r.left + sx, y: r.top + sy, w: r.width, h: r.height }); return `fill="url(#${id})"`;
    }
    return fillAttr(st.color) || 'fill="#fff"';
  }
  function textNode(node, out) {
    const el = node.parentElement, st = getComputedStyle(el); const raw = node.textContent; if (!raw.trim()) return;
    const words = []; const re = /[^\s-]+-?\s*|-+\s*/g; let m; const rng = document.createRange();
    while ((m = re.exec(raw))) { rng.setStart(node, m.index); rng.setEnd(node, m.index + m[0].trimEnd().length); const rs = rng.getClientRects(); if (!rs.length) continue; const r = rs[0]; if (!r.width && !r.height) continue; words.push({ t: m[0], x: r.left + sx, y: r.top + sy, h: r.height }); }
    if (!words.length) return;
    const lines = []; for (const w of words) { const L = lines.find(l => Math.abs(l.y - w.y) < 3); if (L) L.t += w.t; else lines.push({ x: w.x, y: w.y, t: w.t }); }
    const bl = baseline(st); const fill = textFill(st, el); const fa = fontAttrs(st);
    for (const l of lines) out.push(`<text ${fa} ${fill} x="${l.x.toFixed(1)}" y="${(l.y + bl).toFixed(1)}" xml:space="preserve">${esc(tx(l.t.replace(/\s+$/, ''), st))}</text>`);
  }
  function svgEmbed(markup, x, y, w, h, color, par = 'xMidYMid meet') {
    let s = markup.replace(/currentColor/g, color || '#fff');
    s = s.replace(/<svg\b([^>]*)>/, (all, attrs) => {
      attrs = attrs.replace(/\s(width|height|x|y|class|preserveAspectRatio|style|aria-hidden)="[^"]*"/g, '');
      return `<svg${attrs} x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${w.toFixed(1)}" height="${h.toFixed(1)}" preserveAspectRatio="${par}">`;
    });
    // уникализируем id внутри встроенного svg
    const p = 'e' + (++uid) + '_'; s = s.replace(/id="([^"]+)"/g, `id="${p}$1"`).replace(/url\(#([^)]+)\)/g, `url(#${p}$1)`).replace(/href="#([^"]+)"/g, `href="#${p}$1"`);
    return s.replace(/<title>[^<]*<\/title>/g, '');
  }
  const svgCache = {};
  function fetchSvg(src) { const k = Object.keys(window.__SVG).find(f => decodeURIComponent(src).endsWith(f)); if (!k) throw new Error('svg not found ' + src); return window.__SVG[k]; }
  function nameOf(el) {
    if (el.dataset && el.dataset.figma) return el.dataset.figma;
    const cls = (el.className && el.className.baseVal === undefined ? el.className : '').split(' ').filter(Boolean)[0];
    return (el.tagName.toLowerCase() + (cls ? '.' + cls : '')).slice(0, 40);
  }
  function pseudo(el, which, box, out) {
    const st = getComputedStyle(el, which); if (!st || st.content === 'none' || st.display === 'none') return;
    if (st.position !== 'absolute') return;
    const W = box.w - px(getComputedStyle(el).borderLeftWidth) - px(getComputedStyle(el).borderRightWidth), H = box.h;
    let w = px(st.width), h = px(st.height); let l = st.left, t = st.top, r = st.right, b = st.bottom;
    let x = l !== 'auto' ? px(l) : W - px(r) - w; let y = t !== 'auto' ? px(t) : H - px(b) - h;
    if (!w && l !== 'auto' && r !== 'auto') w = W - px(l) - px(r);
    const bx = { x: box.x + px(getComputedStyle(el).borderLeftWidth) + x, y: box.y + px(getComputedStyle(el).borderTopWidth) + y, w, h };
    const rr = radius(st, w, h); const op = +st.opacity;
    const layers = bgLayers(st, bx); const bw = px(st.borderTopWidth);
    for (const f of layers) out.push(`<rect x="${bx.x.toFixed(1)}" y="${bx.y.toFixed(1)}" width="${w.toFixed(1)}" height="${h.toFixed(1)}" rx="${rr.toFixed(1)}" ${f}${op < 1 ? ` opacity="${op}"` : ''}/>`);
    if (bw) { const s = fillAttr(st.borderTopColor, 'stroke'); if (s) out.push(`<rect x="${(bx.x + bw / 2).toFixed(1)}" y="${(bx.y + bw / 2).toFixed(1)}" width="${(w - bw).toFixed(1)}" height="${(h - bw).toFixed(1)}" rx="${Math.max(0, rr - bw / 2).toFixed(1)}" fill="none" ${s} stroke-width="${bw}"/>`); }
  }
  function walk(el, out) {
    const st = getComputedStyle(el);
    if (st.display === 'none' || st.visibility === 'hidden' || +st.opacity === 0) return;
    const R = el.getBoundingClientRect(); const box = { x: R.left + sx, y: R.top + sy, w: R.width, h: R.height };
    if ((box.w < 0.5 || box.h < 0.5) && st.overflow !== 'visible') return;
    const tag = el.tagName.toLowerCase(); const g = []; const op = +st.opacity;
    // тень-обводка (focus ring) — box-shadow со spread
    const sh = st.boxShadow && st.boxShadow !== 'none' ? splitTop(st.boxShadow) : [];
    for (const s of sh) { const cm = s.match(/rgba?\([^)]+\)/); const nums = s.replace(cm ? cm[0] : '', '').trim().split(/\s+/).map(px); if (nums.length >= 4 && nums[2] === 0 && nums[3] > 0) { const sp = nums[3]; g.push(`<rect x="${(box.x - sp).toFixed(1)}" y="${(box.y - sp).toFixed(1)}" width="${(box.w + 2 * sp).toFixed(1)}" height="${(box.h + 2 * sp).toFixed(1)}" rx="${(radius(st, box.w, box.h) + sp).toFixed(1)}" ${fillAttr(cm[0])}/>`); } }
    const rr = radius(st, box.w, box.h);
    if (tag !== 'svg' && tag !== 'img') for (const f of bgLayers(st, box)) g.push(`<rect x="${box.x.toFixed(1)}" y="${box.y.toFixed(1)}" width="${box.w.toFixed(1)}" height="${box.h.toFixed(1)}" rx="${rr.toFixed(1)}" ${f}/>`);
    // рамки
    const bw = ['Top', 'Right', 'Bottom', 'Left'].map(s => px(st['border' + s + 'Width'])); const bs = st.borderTopStyle;
    if (bw.every(v => v > 0) && bw.every(v => v === bw[0]) && st.borderTopColor === st.borderLeftColor) {
      const s = fillAttr(st.borderTopColor, 'stroke'); const w = bw[0];
      if (s) g.push(`<rect x="${(box.x + w / 2).toFixed(1)}" y="${(box.y + w / 2).toFixed(1)}" width="${(box.w - w).toFixed(1)}" height="${(box.h - w).toFixed(1)}" rx="${Math.max(0, rr - w / 2).toFixed(1)}" fill="none" ${s} stroke-width="${w}"${bs === 'dashed' ? ' stroke-dasharray="6 4"' : ''}/>`);
    } else {
      const sides = [['Top', box.x, box.y, box.w, bw[0]], ['Right', box.x + box.w - bw[1], box.y, bw[1], box.h], ['Bottom', box.x, box.y + box.h - bw[2], box.w, bw[2]], ['Left', box.x, box.y, bw[3], box.h]];
      for (const [s, x, y, w, h] of sides) if (w > 0 && h > 0) { const f = fillAttr(st['border' + s + 'Color']); if (f) g.push(`<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${w.toFixed(1)}" height="${h.toFixed(1)}" ${f}/>`); }
    }
    pseudo(el, '::before', box, g);
    if (tag === 'img') {
      const src = el.getAttribute('src');
      if (/\.svg$/.test(src)) {
        const fit = st.objectFit; const par = fit === 'cover' ? (st.objectPosition.includes('100%') && st.objectPosition.startsWith('50%') ? 'xMidYMax slice' : 'xMidYMid slice') : 'xMidYMid meet';
        let inner = svgEmbed(fetchSvg(el.src), box.x, box.y, box.w, box.h, null, par);
        if (rr) { const id = 'c' + (++uid); defs.push(`<clipPath id="${id}"><rect x="${box.x}" y="${box.y}" width="${box.w}" height="${box.h}" rx="${rr}"/></clipPath>`); inner = `<g clip-path="url(#${id})">${inner}</g>`; }
        g.push(inner);
      }
    } else if (tag === 'svg') {
      let mk = el.outerHTML; if (el.classList.contains('ic')) mk = mk.replace(/<svg\b/, `<svg fill="none" stroke="currentColor" stroke-width="${st.strokeWidth ? px(st.strokeWidth) : 2}" stroke-linecap="round" stroke-linejoin="round"`);
      g.push(svgEmbed(mk, box.x, box.y, box.w, box.h, st.color));
    } else if (tag === 'input' || tag === 'textarea' || tag === 'select') {
      if (el.type === 'checkbox') {
        if (el.checked) g.push(`<path d="M${box.x + 5.5} ${box.y + 10} l3.2 3.2 l5.8 -6.4" fill="none" stroke="#0A0D14" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>`);
      } else {
        let val = tag === 'select' ? el.options[el.selectedIndex]?.text || '' : el.value; let ph = false;
        if (!val && el.placeholder) { val = el.placeholder; ph = true; }
        if (el.type === 'password' && !ph) val = '•'.repeat(val.length);
        if (el.type === 'number' || el.type === 'date') val = String(val);
        const color = ph ? 'rgb(125,135,160)' : st.color; const bl = baseline(st);
        const pl = px(st.paddingLeft) + px(st.borderLeftWidth);
        const fs = px(st.fontSize); const lh = fs * 1.2;
        const y = tag === 'textarea' ? box.y + px(st.paddingTop) + px(st.borderTopWidth) : box.y + (box.h - lh) / 2;
        const k = rgba(color);
        g.push(`<text ${fontAttrs(st)} fill="${k.c}"${k.a < 1 ? ` fill-opacity="${k.a}"` : ''} x="${(box.x + pl).toFixed(1)}" y="${(y + bl).toFixed(1)}" xml:space="preserve">${esc(val)}</text>`);
        if (tag === 'select') g.push(`<path d="M${box.x + box.w - 14 - 13.5} ${box.y + box.h / 2 - 2.2} l4.5 4.5 l4.5 -4.5" fill="none" stroke="#A3ACC2" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>`);
      }
    } else {
      const kids = []; for (const n of el.childNodes) { if (n.nodeType === 3) textNode(n, kids); else if (n.nodeType === 1 && !['SCRIPT', 'STYLE', 'TEMPLATE', 'OPTION'].includes(n.tagName)) walk(n, kids); }
      pseudo(el, '::after', box, kids);
      if (st.overflow !== 'visible' && el !== document.body) { const id = 'c' + (++uid); defs.push(`<clipPath id="${id}"><rect x="${box.x.toFixed(1)}" y="${box.y.toFixed(1)}" width="${box.w.toFixed(1)}" height="${box.h.toFixed(1)}" rx="${rr.toFixed(1)}"/></clipPath>`); g.push(`<g clip-path="url(#${id})">${kids.join('')}</g>`); }
      else g.push(...kids);
    }
    if (!g.length) return;
    const named = el.dataset && el.dataset.figma || ['header', 'footer', 'section', 'aside', 'main', 'form', 'nav', 'button', 'a', 'table'].includes(tag) || /\b(card|btn|chip|field|seat|modal)\b/.test(el.className && el.className.baseVal === undefined ? el.className : '');
    if (named || op < 1) out.push(`<g id="${esc(nameOf(el))}"${op < 1 ? ` opacity="${op}"` : ''}>${g.join('')}</g>`); else out.push(...g);
  }
  const out = []; walk(document.body, out);
  const W = 1440, H = Math.ceil(document.documentElement.scrollHeight);
  // кликабельные области
  const links = [];
  document.querySelectorAll('a[href], [data-open]').forEach(a => {
    const r = a.getBoundingClientRect(); if (!r.width || !r.height || getComputedStyle(a).visibility === 'hidden') return;
    let href = a.getAttribute('href') || '#' + a.dataset.open;
    links.push({ x: Math.round(r.left + sx), y: Math.round(r.top + sy), w: Math.round(r.width), h: Math.round(r.height), href, text: (a.innerText || '').trim().slice(0, 40) });
  });
  return { svg: `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}"><defs>${defs.join('')}</defs><rect width="${W}" height="${H}" fill="#0A0D14"/>${out.join('')}</svg>`, links, H };
}

(async () => {
  const [outDir, ...items] = process.argv.slice(2);
  const b = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, locale: 'ru-RU' }); const p = await ctx.newPage();
  for (const it of items) {
    const k = it.lastIndexOf('='); const pg = it.slice(0, k); let [name, crop] = it.slice(k + 1).split('@');
    await p.goto('file://' + path.resolve('prototype', pg)); await p.waitForTimeout(400);
    await p.addStyleTag({ content: '.header{position:relative!important;backdrop-filter:none!important;background:#0A0D14!important} .filters,.summary,.side{position:static!important} .modal-bg{backdrop-filter:none!important}' });
    await p.evaluate(() => window.scrollTo(0, 0)); await p.waitForTimeout(100);
    const svgs = {}; for (const d of ['prototype/assets', 'brand']) for (const f of fs.readdirSync(d)) if (f.endsWith('.svg')) svgs[(d === 'brand' ? 'brand/' : 'assets/') + f] = fs.readFileSync(path.join(d, f), 'utf8');
    await p.evaluate(m => { window.__SVG = m; }, svgs);
    const res = await p.evaluate(extract);
    let svg = res.svg;
    if (crop) svg = svg.replace(/height="\d+" viewBox="0 0 1440 \d+"/, `height="${crop}" viewBox="0 0 1440 ${crop}"`);
    fs.writeFileSync(path.join(outDir, name + '.svg'), svg);
    fs.writeFileSync(path.join(outDir, name + '.links.json'), JSON.stringify(res.links.filter(l => !crop || l.y < +crop)));
    console.log(name, (svg.length / 1024).toFixed(0) + 'KB', 'h=' + (crop || res.H), 'links=' + res.links.length);
  }
  await b.close();
})();
