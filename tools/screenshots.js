// Скриншоты прототипа для отчёта: полные страницы, состояния и отдельные фрагменты (deviceScaleFactor 2)
const { chromium } = require('playwright'); const path = require('path');
const P = f => 'file://' + path.resolve('prototype', f);
const pages = [
  ['index.html', '01-glavnaya'], ['catalog.html', '02-katalog'], ['catalog.html?filter=vip', '02b-katalog-filtr-vip'],
  ['about.html', '03-o-klube'], ['account.html', '04-lichnyj-kabinet'], ['account.html?modal=topup', '04b-popolnenie-balansa', 'viewport'],
  ['feedback.html', '05-obratnaya-svyaz'], ['feedback.html?state=error', '05b-obratnaya-svyaz-oshibki'], ['feedback.html?state=success', '05c-zayavka-otpravlena'],
  ['login.html', '06-vhod'], ['login.html?state=error', '06b-vhod-oshibka'], ['register.html', '07-registraciya'], ['register.html?state=error', '07b-registraciya-oshibki'],
  ['design-system.html', '00-dizajn-sistema'],
];
const parts = [
  ['index.html', '.header', 'f-shapka'], ['index.html', '.footer', 'f-podval'], ['index.html', '.hero', 'f-banner-na-glavnoj'],
  ['catalog.html', '#booking', 'f-skhema-zala'], ['catalog.html', '.filters', 'f-filtry'],
  ['design-system.html', '[data-figma="Кнопки"]', 'f-knopki-sostoyaniya'], ['design-system.html', '[data-figma="Поля ввода"]', 'f-polya-sostoyaniya'],
  ['design-system.html', '[data-figma="Цвета"]', 'f-palitra'], ['design-system.html', '[data-figma="Типографика"]', 'f-tipografika'],
  ['design-system.html', '[data-figma="Иконки"]', 'f-ikonki'], ['design-system.html', '[data-figma="Сетка и отступы"]', 'f-setka'],
];
(async () => {
  const b = await chromium.launch(); const out = 'screenshots';
  const ctx = await b.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2, locale: 'ru-RU' }); const p = await ctx.newPage();
  const errs = []; p.on('pageerror', e => errs.push(e.message));
  const fix = () => p.addStyleTag({ content: '.header{position:relative!important} .filters,.summary,.side{position:static!important}' });
  for (const [pg, name, mode] of pages) {
    await p.goto(P(pg)); await p.waitForTimeout(500); await fix(); await p.evaluate(() => scrollTo(0, 0));
    await p.screenshot({ path: `${out}/${name}.png`, fullPage: mode !== 'viewport' }); console.log(name);
  }
  for (const [pg, sel, name] of parts) {
    await p.goto(P(pg)); await p.waitForTimeout(400); await fix();
    await p.locator(sel).first().screenshot({ path: `${out}/${name}.png` }); console.log(name);
  }
  // логотипы и баннер
  const fs = require('fs');
  for (const [f, name, bg] of [['brand/logo.svg', 'b-logo', '#0A0D14'], ['brand/logo-mono.svg', 'b-logo-mono', '#FFFFFF'], ['brand/logo-mono-white.svg', 'b-logo-mono-white', '#0A0D14'], ['brand/logo-mark.svg', 'b-znak', '#0A0D14'], ['brand/banner.svg', 'b-banner', '#0A0D14']]) {
    const svg = fs.readFileSync(f, 'utf8');
    await p.setContent(`<body style="margin:0;background:${bg};display:inline-block;padding:${name === 'b-banner' ? 0 : 40}px">${svg}</body>`); await p.waitForTimeout(200);
    await p.locator('svg').first().screenshot({ path: `${out}/${name}.png` }); console.log(name);
  }
  if (errs.length) console.log('PAGE ERRORS', errs);
  await b.close();
})();
