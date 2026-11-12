// usage: node shoot.js outDir scale page[?q][#hash]=name ...   — скриншоты страниц прототипа (полная высота)
const { chromium } = require('playwright'); const path = require('path');
(async () => {
  const [outDir, scale, ...items] = process.argv.slice(2);
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: +scale, locale: 'ru-RU' });
  const p = await ctx.newPage(); const errors = [];
  p.on('pageerror', e => errors.push(e.message)); p.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  for (const it of items) {
    const k = it.lastIndexOf('='); const pg = it.slice(0, k), name = it.slice(k + 1);
    const url = 'file://' + path.resolve('prototype', pg);
    await p.goto(url); await p.waitForTimeout(500);
    if (pg.includes('#')) await p.evaluate(() => window.scrollTo(0, 0));
    const full = !pg.includes('modal=');
    await p.addStyleTag({ content: '.header{position:relative!important} .filters,.summary,.side{position:static!important}' });
    await p.screenshot({ path: path.join(outDir, name + '.png'), fullPage: full });
    console.log('shot', name);
  }
  if (errors.length) console.log('ERRORS:', errors);
  await b.close();
})();
