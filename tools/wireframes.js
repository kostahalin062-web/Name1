const { chromium } = require('playwright'); const path = require('path');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 0.6, locale: 'ru-RU' });
  for (const [f, n] of [['index.html', 'wf-glavnaya'], ['catalog.html', 'wf-katalog'], ['account.html', 'wf-kabinet'], ['feedback.html', 'wf-obratnaya-svyaz']]) {
    await p.goto('file://' + path.resolve('prototype', f)); await p.waitForTimeout(400);
    await p.addStyleTag({ content: `*{color:#555!important;background:#fff!important;background-image:none!important;box-shadow:none!important;border-color:#999!important;font-family:"Liberation Sans",Arial!important;text-shadow:none!important;-webkit-text-fill-color:#555!important}
      body{background:#fff!important} img,svg{visibility:hidden} .hero-art,.about-visual,.map,.art{outline:2px dashed #999} .header{position:relative!important}
      .card,.btn,.input,.chip,.seat,.hero,.stats>div,.cta-band,.footer{border:1.5px solid #999!important} .ic-box,.avatar{border:1.5px dashed #999!important}` });
    await p.screenshot({ path: `report/img/${n}.png`, fullPage: true });
  }
  await b.close();
})();
