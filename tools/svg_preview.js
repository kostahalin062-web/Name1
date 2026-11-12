// usage: node svg_preview.js out.png bg file1.svg [file2.svg ...]  — рендер SVG на фоне для проверки
const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const [out, bg, ...files] = process.argv.slice(2);
  const html = `<body style="margin:0;padding:24px;background:${bg};display:flex;flex-direction:column;gap:24px;align-items:flex-start">` +
    files.map(f => `<img src="data:image/svg+xml;base64,${Buffer.from(fs.readFileSync(f)).toString('base64')}" style="max-width:1440px">`).join('') + '</body>';
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1490, height: 400 } });
  await p.setContent(html); await p.waitForTimeout(300); await p.screenshot({ path: out, fullPage: true }); await b.close();
})();
