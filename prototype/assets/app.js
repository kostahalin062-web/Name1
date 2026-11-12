/* TurboPC — общие компоненты прототипа: иконки, шапка, подвал, состояния */
const ICONS = {
  monitor: '<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 20h8M12 16v4"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  wallet: '<rect x="3" y="6" width="18" height="14" rx="2"/><path d="M3 10h13a2 2 0 0 1 2 2v0a2 2 0 0 1-2 2h-1M7 6V4.5h10V6"/>',
  card: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18M7 15h4"/>',
  trophy: '<path d="M8 4h8v5a4 4 0 0 1-8 0zM8 6H5a3 3 0 0 0 3 4M16 6h3a3 3 0 0 1-3 4M12 13v4M8 21h8M10 17h4v4h-4z"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><circle cx="17" cy="9" r="2.5"/><path d="M16.5 14.6A5 5 0 0 1 21.5 20"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  mail: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 7l9 6 9-6"/>',
  phone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/>',
  pin: '<path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/>',
  star: '<path d="M12 3l2.8 5.8 6.2.8-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.8z"/>',
  headset: '<path d="M4 15v-3a8 8 0 0 1 16 0v3"/><rect x="3" y="14" width="4" height="6" rx="1.5"/><rect x="17" y="14" width="4" height="6" rx="1.5"/>',
  cup: '<path d="M6 8h11v6a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5zM17 10h1.5a2.5 2.5 0 0 1 0 5H17M9 3v2M13 3v2"/>',
  bolt: '<path d="M13 2L4 14h7l-1 8 10-13h-7z"/>',
  check: '<path d="M5 12l5 5 9-10"/>',
  alert: '<circle cx="12" cy="12" r="9"/><path d="M12 7v6M12 16.5v.5"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.5"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  'eye-off': '<path d="M3 3l18 18M10.6 5.1A10 10 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3 3.9M6.6 6.6A17 17 0 0 0 2 12s3.5 7 10 7a9.6 9.6 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
  'arrow-right': '<path d="M5 12h14M13 6l6 6-6 6"/>',
  'arrow-left': '<path d="M19 12H5M11 6l-6 6 6 6"/>',
  'chevron-down': '<path d="M6 9l6 6 6-6"/>',
  filter: '<path d="M4 5h16l-6 8v5l-4 2v-7z"/>',
  logout: '<path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l-5-5 5-5M5 12h11"/>',
  sliders: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
  history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5M12 7v5l3 2"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  x: '<path d="M6 6l12 12M18 6L6 18"/>',
  box: '<path d="M4 8l8-4 8 4v8l-8 4-8-4zM4 8l8 4 8-4M12 12v8"/>',
  grid: '<rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  gauge: '<path d="M4 18a8 8 0 1 1 16 0M12 18l4-6"/>',
  message: '<path d="M4 5h16v11H9l-5 4z"/>',
  send: '<path d="M4 12l16-8-6 16-3-7z"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16zM13 7l4 4"/>',
  gamepad: '<path d="M7 7h10a4 4 0 0 1 4 4l-.6 4.5a2.8 2.8 0 0 1-4.9 1.4L14 15h-4l-1.5 1.9a2.8 2.8 0 0 1-4.9-1.4L3 11a4 4 0 0 1 4-4z"/><path d="M8 10v3M6.5 11.5h3M15.5 11h.01M17.5 13h.01"/>',
  shield: '<path d="M12 3l8 3v6c0 4.5-3.4 8-8 9-4.6-1-8-4.5-8-9V6z"/><path d="M9 12l2 2 4-4"/>',
  wifi: '<path d="M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01"/>',
  cpu: '<rect x="6" y="6" width="12" height="12" rx="2"/><rect x="9.5" y="9.5" width="5" height="5"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/>',
  armchair: '<path d="M6 11V7a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3v4M4 11a2 2 0 0 1 2 2v2h12v-2a2 2 0 1 1 4 0v5H2v-5a2 2 0 0 1 2-2zM5 18v2M19 18v2"/>',
  ticket: '<path d="M3 8a2 2 0 0 0 2-2h14a2 2 0 0 0 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 0-2 2H5a2 2 0 0 0-2-2v-2a2 2 0 0 0 0-4z"/><path d="M14 6v12" stroke-dasharray="2 2.5"/>',
  receipt: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3"/>',
};
function icon(name, cls = '') {
  return `<svg class="ic ${cls}" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name] || ''}</svg>`;
}
function hydrateIcons(root = document) {
  root.querySelectorAll('i[data-i]').forEach(el => {
    const t = document.createElement('template');
    t.innerHTML = icon(el.dataset.i, el.className).trim();
    el.replaceWith(t.content.firstChild);
  });
}

const NAV = [
  ['index.html', 'Главная'],
  ['catalog.html', 'Тарифы и услуги'],
  ['catalog.html#booking', 'Бронирование'],
  ['about.html', 'О клубе'],
  ['feedback.html', 'Обратная связь'],
];

function renderHeader() {
  const el = document.querySelector('[data-header]');
  if (!el) return;
  const page = document.body.dataset.page;
  const auth = document.body.dataset.auth === 'user';
  const links = NAV.map(([href, title]) => {
    const active = (href === page + '.html') || (page === 'booking' && href.includes('#booking'));
    return `<a href="${href}" class="${active ? 'active' : ''}">${title}</a>`;
  }).join('');
  const actions = auth
    ? `<a href="account.html#balance" class="balance-pill" title="Баланс">${icon('wallet', 'ic-sm')}1 250 ₽</a>
       <a href="account.html" class="user-chip"><span class="avatar">АК</span>Артём</a>`
    : `<a href="login.html" class="btn btn-secondary btn-sm">${icon('user', 'ic-sm')}Войти</a>
       <a href="catalog.html#booking" class="btn btn-primary btn-sm">Забронировать</a>`;
  el.outerHTML = `<header class="header"><div class="container">
    <a href="index.html" class="logo" aria-label="TurboPC — на главную"><img src="assets/logo-compact.svg" alt="TurboPC"></a>
    <nav class="nav">${links}</nav>
    <div class="header-actions">${actions}</div></div></header>`;
}

function renderFooter() {
  const el = document.querySelector('[data-footer]');
  if (!el) return;
  el.outerHTML = `<footer class="footer"><div class="container">
    <div class="cols">
      <div><a href="index.html"><img src="assets/logo.svg" alt="TurboPC" style="height:44px"></a>
        <p class="about">Компьютерный клуб с мощным железом, VIP-залом и турнирами каждую неделю. Работаем круглосуточно.</p></div>
      <div><h5>Навигация</h5><ul>
        <li><a href="index.html">Главная</a></li><li><a href="catalog.html">Тарифы и услуги</a></li>
        <li><a href="about.html">О клубе</a></li><li><a href="feedback.html">Обратная связь</a></li></ul></div>
      <div><h5>Клиентам</h5><ul>
        <li><a href="catalog.html#booking">Бронирование ПК</a></li><li><a href="index.html#tournaments">Турниры</a></li>
        <li><a href="account.html">Личный кабинет</a></li><li><a href="register.html">Регистрация</a></li></ul></div>
      <div><h5>Контакты</h5><ul>
        <li>${icon('pin')}г. Барнаул, ул. Скоростная, 7</li>
        <li>${icon('phone')}<a href="tel:+73852000000">+7 (3852) 00-00-00</a></li>
        <li>${icon('mail')}<a href="mailto:hello@turbopc.example">hello@turbopc.example</a></li>
        <li>${icon('clock')}Круглосуточно, без выходных</li></ul></div>
    </div>
    <div class="bottom"><span>© 2026 TurboPC. Учебный прототип интерфейса.</span>
      <a href="feedback.html">Политика обработки персональных данных</a></div>
  </div></footer>`;
}

/* Состояние страницы из URL: ?state=error|success, ?modal=topup */
function applyUrlState() {
  const q = new URLSearchParams(location.search);
  document.body.dataset.state = q.get('state') || '';
  if (q.get('modal')) document.getElementById(q.get('modal'))?.classList.add('open');
  return q;
}

/* Валидация форм прототипа */
const RULES = {
  required: v => v.trim().length > 0 || 'Заполните поле',
  name: v => /^[A-Za-zА-Яа-яЁё\- ]{2,40}$/.test(v.trim()) || 'Имя: от 2 букв, без цифр',
  phone: v => v.replace(/\D/g, '').length === 11 || 'Введите номер полностью: +7 (XXX) XXX-XX-XX',
  email: v => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim()) || 'Некорректный e-mail',
  login: v => (/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim()) || v.replace(/\D/g, '').length === 11) || 'Введите телефон или e-mail',
  password: v => (v.length >= 8 && /\d/.test(v) && /[A-Za-zА-Яа-я]/.test(v)) || 'Минимум 8 символов, буквы и цифры',
  nick: v => /^[A-Za-z0-9_]{3,16}$/.test(v) || '3–16 символов: латиница, цифры, _',
  age: v => { const m = v.trim().match(/^(\d{2})\.(\d{2})\.(\d{4})$/); if (!m) return 'Укажите дату в формате ДД.ММ.ГГГГ'; const a = (Date.now() - new Date(+m[3], m[2] - 1, +m[1])) / 31557600000; return a >= 14 || 'Клуб доступен с 14 лет'; },
  min20: v => v.trim().length >= 20 || 'Сообщение — не короче 20 символов',
  same: (v, form) => v === form.querySelector('[name=password]').value || 'Пароли не совпадают',
};
function validateField(input, form) {
  const field = input.closest('.field') || input.closest('.check');
  const rules = (input.dataset.rules || '').split(' ').filter(Boolean);
  let msg = true;
  if (input.type === 'checkbox') msg = input.checked || 'Необходимо согласие';
  else for (const r of rules) { msg = RULES[r](input.value, form); if (msg !== true) break; }
  const ok = msg === true;
  if (field) {
    field.classList.toggle('has-error', !ok);
    const e = field.querySelector('.err span'); if (e && !ok) e.textContent = msg;
  }
  if (input.classList.contains('input')) { input.classList.toggle('is-error', !ok); input.classList.toggle('is-success', ok && rules.length > 0); }
  return ok;
}
function bindForm(form, onOk) {
  const inputs = [...form.querySelectorAll('[data-rules], input[type=checkbox][required]')];
  inputs.forEach(i => i.addEventListener(i.type === 'checkbox' ? 'change' : 'blur', () => validateField(i, form)));
  inputs.forEach(i => i.addEventListener('input', () => { if (i.classList.contains('is-error')) validateField(i, form); }));
  form.addEventListener('submit', e => {
    e.preventDefault();
    const ok = inputs.map(i => validateField(i, form)).every(Boolean);
    if (ok) onOk(); else form.querySelector('.is-error, .has-error input')?.focus();
  });
  return inputs;
}
function phoneMask(input) {
  input.addEventListener('input', () => {
    let d = input.value.replace(/\D/g, ''); if (d.startsWith('8')) d = '7' + d.slice(1); if (!d.startsWith('7')) d = '7' + d; d = d.slice(0, 11);
    const p = [d.slice(1, 4), d.slice(4, 7), d.slice(7, 9), d.slice(9, 11)];
    input.value = '+7' + (p[0] ? ` (${p[0]}` : '') + (p[0].length === 3 ? ')' : '') + (p[1] ? ` ${p[1]}` : '') + (p[2] ? `-${p[2]}` : '') + (p[3] ? `-${p[3]}` : '');
  });
}
function bindPasswordToggles() {
  document.querySelectorAll('[data-toggle-pass]').forEach(b => b.addEventListener('click', () => {
    const i = b.parentElement.querySelector('input'); const show = i.type === 'password';
    i.type = show ? 'text' : 'password'; b.innerHTML = icon(show ? 'eye-off' : 'eye', 'ic-sm');
  }));
}

document.addEventListener('DOMContentLoaded', () => {
  renderHeader(); renderFooter(); hydrateIcons();
  document.querySelectorAll('[data-open]').forEach(b => b.addEventListener('click', e => { e.preventDefault(); document.getElementById(b.dataset.open).classList.add('open'); }));
  document.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', () => b.closest('.modal-bg').classList.remove('open')));
  document.querySelectorAll('.modal-bg').forEach(m => m.addEventListener('click', e => { if (e.target === m) m.classList.remove('open'); }));
  document.querySelectorAll('.switch').forEach(s => s.addEventListener('click', () => s.classList.toggle('on')));
  bindPasswordToggles();
  document.querySelectorAll('input[type=tel]').forEach(phoneMask);
  window.pageInit?.(applyUrlState());
});
