// usage: node svg2png.js in.svg out.png [scale]
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
(async () => {
  const [inp, out, scale] = process.argv.slice(2);
  const svg = fs.readFileSync(inp, 'utf8');
  const m = svg.match(/width="(\d+)" height="(\d+)"/);
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: +m[1], height: +m[2] }, deviceScaleFactor: +(scale || 1) });
  await p.setContent(`<html><body style="margin:0">${svg}</body></html>`); await p.waitForTimeout(300);
  await p.screenshot({ path: out, clip: { x: 0, y: 0, width: +m[1], height: +m[2] }, omitBackground: true }); await b.close();
})();
