// Проверка навигации прототипа: все ссылки ведут на существующие страницы и якоря, на страницах нет ошибок JS
const { chromium } = require('playwright'); const path = require('path'); const fs = require('fs');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage(); const errs = [];
  p.on('pageerror', e => errs.push(p.url() + ': ' + e.message));
  const files = fs.readdirSync('prototype').filter(f => f.endsWith('.html'));
  let total = 0, bad = []; const anchors = {};
  for (const f of files) { await p.goto('file://' + path.resolve('prototype', f)); await p.waitForTimeout(200); anchors[f] = await p.evaluate(() => [...document.querySelectorAll('[id]')].map(e => e.id)); }
  for (const f of files) {
    await p.goto('file://' + path.resolve('prototype', f)); await p.waitForTimeout(200);
    const hrefs = await p.evaluate(() => [...document.querySelectorAll('a[href]')].map(a => a.getAttribute('href')));
    for (const h of hrefs) {
      total++;
      if (h === '#' || /^(tel|mailto):/.test(h)) continue;
      const [file, hash] = h.split('#'); const target = (file || f).split('?')[0];
      if (!fs.existsSync(path.join('prototype', target))) { bad.push(`${f}: ${h} — нет файла`); continue; }
      if (hash && !anchors[target].includes(hash)) bad.push(`${f}: ${h} — нет якоря`);
    }
  }
  // сценарий: вход демо-учёткой ведёт в кабинет
  await p.goto('file://' + path.resolve('prototype', 'login.html')); await p.fill('#login', 'artem@example.com'); await p.fill('#pass', 'turbo2026'); await p.click('form button.btn-primary'); await p.waitForTimeout(400);
  const loginOk = p.url().endsWith('account.html');
  // сценарий: форма обратной связи — пустая отправка даёт ошибки, заполненная — успех
  await p.goto('file://' + path.resolve('prototype', 'feedback.html')); await p.click('#submit'); const errCount = await p.locator('.has-error').count();
  await p.fill('#f-name', 'Артём'); await p.fill('#f-phone', '9131234567'); await p.selectOption('#f-topic', { index: 1 }); await p.fill('#f-msg', 'Хочу забронировать VIP-зал на день рождения.'); await p.check('#consent'); await p.click('#submit');
  const sent = await p.locator('#fb.sent').count();
  // сценарий: выбор места пересчитывает сумму
  await p.goto('file://' + path.resolve('prototype', 'catalog.html')); await p.click('[data-seat="S01"]'); await p.selectOption('#bdur', '5'); const total1 = await p.textContent('#s-total');
  // фильтр: только доп. услуги
  await p.click('#tabs [data-t=extra]'); const visible = await p.locator('.offer:visible').count();
  console.log(JSON.stringify({ pages: files.length, links: total, broken: bad, jsErrors: errs, loginOk, feedbackErrorsShown: errCount, feedbackSent: !!sent, bookingTotalS01_5h: total1, extrasVisible: visible }, null, 1));
  await b.close();
})();
