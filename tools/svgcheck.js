// usage: node svgcheck.js in.svg out.png — рендер SVG (через <img>) для визуальной проверки
const { chromium } = require('playwright'); const fs = require('fs');
(async () => { const [inp, out] = process.argv.slice(2); const s = fs.readFileSync(inp, 'utf8'); const m = s.match(/width="(\d+)" height="(\d+)"/);
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: +m[1], height: +m[2] }, deviceScaleFactor: 0.5 });
  await p.setContent(`<body style="margin:0">${s}</body>`); await p.waitForTimeout(800);
  await p.screenshot({ path: out, clip: { x: 0, y: 0, width: +m[1], height: +m[2] } }); await b.close(); })();
