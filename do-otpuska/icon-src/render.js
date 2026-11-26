const { chromium } = require('playwright'); const path = require('path');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1024, height: 1024 } });
  for (const n of ['icon-only', 'icon-foreground', 'icon-background', 'splash']) {
    await p.goto('file://' + path.resolve(__dirname, n + '.svg'));
    await p.screenshot({ path: path.resolve(__dirname, '../assets', n + '.png'), omitBackground: n === 'icon-foreground' });
  }
  await b.close();
})();
