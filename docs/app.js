(function () {
  'use strict';

  const DEFAULT_URL = 'https://www.asu.ru/timetable/students/15/2129441855/?file=2129441855.ics';
  const LOCAL_ICS = './schedule.ics';
  const DAYS = ['Воскресенье', 'Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота'];
  const MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря'];

  // ---------- storage ----------
  const store = {
    get(key, fallback) {
      try {
        const v = localStorage.getItem('dz.' + key);
        return v == null ? fallback : JSON.parse(v);
      } catch { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem('dz.' + key, JSON.stringify(value)); } catch { /* storage full or blocked */ }
    },
  };

  const state = {
    lessons: store.get('lessons', []),
    homework: store.get('homework', []),
    settings: Object.assign({ url: DEFAULT_URL, remindTime: '18:00', remindDefault: true }, store.get('settings', {})),
    lastUpdate: store.get('lastUpdate', null),
    lastCheckDay: store.get('lastCheckDay', null),
    calName: store.get('calName', ''),
    weekOffset: 0,
    tab: 'schedule',
    openLesson: null,
  };

  // ---------- helpers ----------
  const $ = (s) => document.querySelector(s);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const pad = (n) => String(n).padStart(2, '0');

  function ymd(d) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; }
  function fromYmd(s) { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); }
  function addDays(d, n) { const r = new Date(d); r.setDate(r.getDate() + n); return r; }
  function mondayOf(d) { const r = new Date(d.getFullYear(), d.getMonth(), d.getDate()); return addDays(r, -((r.getDay() + 6) % 7)); }
  function todayYmd() { return ymd(new Date()); }
  function nowHM() { const n = new Date(); return `${pad(n.getHours())}:${pad(n.getMinutes())}`; }
  function prettyDate(s) { const d = fromYmd(s); return `${DAYS[d.getDay()]}, ${d.getDate()} ${MONTHS[d.getMonth()]}`; }
  function relDate(s) {
    const diff = Math.round((fromYmd(s) - fromYmd(todayYmd())) / 86400000);
    if (diff === 0) return 'Сегодня';
    if (diff === 1) return 'Завтра';
    if (diff === -1) return 'Вчера';
    return prettyDate(s);
  }
  function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }
  const lkey = (l) => l.date + 'T' + l.start;

  function save() {
    store.set('lessons', state.lessons);
    store.set('homework', state.homework);
    store.set('settings', state.settings);
    store.set('lastUpdate', state.lastUpdate);
    store.set('lastCheckDay', state.lastCheckDay);
    store.set('calName', state.calName);
    syncReminders();
  }

  // ---------- homework model ----------
  // Homework remembers only the lesson where it was given (subject + date/time).
  // The lesson it is due for is looked up every time: the next lesson with the same
  // subject in the current schedule. So when the schedule changes or the next lesson
  // appears on the site later, the homework automatically moves to the right lesson.
  function dueLesson(h) {
    const from = h.fromDate + 'T' + (h.fromStart || '00:00');
    for (const l of state.lessons) {
      if (l.subject === h.subject && !l.cancelled && lkey(l) > from) return l;
    }
    return null;
  }
  function hwFor(lesson) {
    return state.homework.filter((h) => { const d = dueLesson(h); return d && d.uid === lesson.uid; });
  }
  function hwGivenAt(lesson) {
    return state.homework.filter((h) => h.fromUid === lesson.uid ||
      (h.subject === lesson.subject && h.fromDate === lesson.date && h.fromStart === lesson.start));
  }
  function pendingCount() { return state.homework.filter((h) => !h.done).length; }

  // Upgrade homework saved by the earlier version of the app.
  function migrate() {
    let changed = false;
    for (const h of state.homework) {
      if (h.fromDate) continue;
      const src = h.fromUid && state.lessons.find((l) => l.uid === h.fromUid);
      h.fromDate = src ? src.date : (h.createdAt || new Date().toISOString()).slice(0, 10);
      h.fromStart = src ? src.start : '00:00';
      delete h.due; delete h.lessonUid;
      changed = true;
    }
    if (changed) save();
  }

  // ---------- schedule loading ----------
  function applyICS(text, source) {
    const { calName, lessons } = window.parseICS(text);
    if (!lessons.length) throw new Error('В файле нет пар');
    const min = lessons[0].date;
    const max = lessons[lessons.length - 1].date;
    // Keep lessons outside the range of the new file (past weeks stay in history).
    const kept = state.lessons.filter((l) => l.date < min || l.date > max);
    state.lessons = kept.concat(lessons).sort((a, b) => lkey(a).localeCompare(lkey(b)));
    if (calName) state.calName = calName;
    state.lastUpdate = { at: new Date().toISOString(), source };
    state.lastCheckDay = todayYmd();
    save();
  }

  async function fetchText(url) {
    const res = await fetch(url, { cache: 'no-store' });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const text = await res.text();
    if (!text.includes('BEGIN:VCALENDAR')) throw new Error('Это не файл расписания');
    return text;
  }

  let refreshing = false;
  async function refresh(manual) {
    if (refreshing) return;
    refreshing = true;
    $('#btn-refresh').classList.add('spin');
    if (manual || !state.lessons.length) setStatus('Обновляю расписание…');
    const sources = [];
    if (state.settings.url) sources.push([state.settings.url, 'сайт АлтГУ']);
    sources.push([LOCAL_ICS + '?t=' + Date.now(), 'копия на GitHub']);
    let ok = false;
    for (const [url, name] of sources) {
      try { applyICS(await fetchText(url), name); ok = true; break; } catch { /* try next source */ }
    }
    refreshing = false;
    $('#btn-refresh').classList.remove('spin');
    if (ok) {
      setStatus('');
      render();
      if (manual) toast('Расписание обновлено');
    } else {
      setStatus(state.lessons.length
        ? 'Нет связи — показано сохранённое расписание'
        : 'Не удалось загрузить расписание. Загрузите файл .ics в настройках (⋮).', true);
    }
  }

  // The schedule is checked once a day (and any time with the ⟳ button).
  function refreshIfNewDay() {
    if (state.lastCheckDay !== todayYmd()) refresh(false);
  }

  function setStatus(text, warn) {
    const el = $('#status');
    el.textContent = text;
    el.classList.toggle('warn', !!warn);
  }

  let toastTimer;
  function toast(text) {
    let el = $('#toast');
    if (!el) { el = document.createElement('div'); el.id = 'toast'; document.body.appendChild(el); }
    el.textContent = text;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 2400);
  }

  // ---------- reminders ----------
  // Fires the day before the lesson the homework is due for, at settings.remindTime.
  function remindAtFor(lesson) {
    if (!lesson) return null;
    const [hh, mm] = (state.settings.remindTime || '18:00').split(':').map(Number);
    const d = addDays(fromYmd(lesson.date), -1);
    d.setHours(hh || 0, mm || 0, 0, 0);
    return d.getTime();
  }
  function remindLabel(at) {
    const d = new Date(at);
    return `${relDate(ymd(d)).toLowerCase()} в ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function syncReminders() {
    if (!window.ReminderDB) return;
    const list = [];
    for (const h of state.homework) {
      if (!h.remind || h.done) continue;
      const l = dueLesson(h);
      const at = remindAtFor(l);
      if (at == null) continue;
      list.push({
        id: h.id,
        key: h.id + '@' + at,
        at,
        from: Date.parse(h.remindFrom || h.createdAt) || 0,
        title: `📝 Завтра: ${h.subject}`,
        body: `${h.text}\nПара: ${prettyDate(l.date)}, ${l.start}${l.room ? ', ауд. ' + l.room : ''}`,
      });
    }
    window.ReminderDB.set('reminders', list).catch(() => {});
  }

  async function fireDueReminders() {
    if (!window.ReminderDB || !('serviceWorker' in navigator)) return;
    try { await window.ReminderDB.fireDue(await navigator.serviceWorker.ready); } catch { /* ignore */ }
  }

  // Returns true when notifications are allowed (asks the first time).
  async function ensureNotifyPermission() {
    if (typeof Notification === 'undefined') { toast('Этот браузер не поддерживает уведомления'); return false; }
    if (Notification.permission === 'default') {
      try { await Notification.requestPermission(); } catch { /* ignore */ }
      if (Notification.permission === 'granted') setupBackgroundSync();
    }
    if (Notification.permission !== 'granted') {
      toast('Уведомления запрещены — разрешите их в настройках Chrome');
      return false;
    }
    return true;
  }

  async function setupBackgroundSync() {
    try {
      const reg = await navigator.serviceWorker.ready;
      if (!('periodicSync' in reg)) return;
      const st = await navigator.permissions.query({ name: 'periodic-background-sync' });
      if (st.state === 'granted') await reg.periodicSync.register('dz-reminders', { minInterval: 60 * 60 * 1000 });
    } catch { /* not supported */ }
  }

  // ---------- rendering ----------
  function render() {
    $('#title').textContent = state.tab === 'schedule' ? 'Расписание' : 'Домашние задания';
    $('#week-nav').hidden = state.tab !== 'schedule';
    $('#view-schedule').hidden = state.tab !== 'schedule';
    $('#view-homework').hidden = state.tab !== 'homework';
    document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === state.tab));
    const n = pendingCount();
    const badge = $('#hw-badge');
    badge.hidden = n === 0;
    badge.textContent = n;
    if (state.tab === 'schedule') renderSchedule(); else renderHomework();
  }

  function typeClass(t) { return { 'лек.': 'lec', 'пр.з.': 'prac', 'лаб.': 'lab' }[t] || 'other'; }

  function renderSchedule() {
    const monday = addDays(mondayOf(new Date()), state.weekOffset * 7);
    const sunday = addDays(monday, 6);
    const label = { 0: 'Эта неделя', 1: 'Следующая неделя', '-1': 'Прошлая неделя' }[state.weekOffset] || 'Неделя';
    $('#this-week').innerHTML = `<b>${label}</b><small>${monday.getDate()} ${MONTHS[monday.getMonth()]} — ${sunday.getDate()} ${MONTHS[sunday.getMonth()]}</small>`;

    const today = todayYmd();
    const hm = nowHM();
    let html = '';
    let any = false;
    for (let i = 0; i < 7; i++) {
      const date = ymd(addDays(monday, i));
      const lessons = state.lessons.filter((l) => l.date === date);
      if (!lessons.length && i === 6) continue; // hide empty Sunday
      any = any || lessons.length > 0;
      html += `<div class="day ${date === today ? 'today' : ''}" ${date === today ? 'id="today"' : ''}>
        <h2>${esc(prettyDate(date))}${date === today ? ' <em>сегодня</em>' : ''}</h2>`;
      if (!lessons.length) html += '<p class="empty">Нет пар</p>';
      for (const l of lessons) {
        const hw = hwFor(l);
        const open = hw.filter((h) => !h.done).length;
        const doneAll = hw.length > 0 && open === 0;
        const isNow = date === today && l.start <= hm && hm < l.end;
        const past = date < today || (date === today && l.end <= hm);
        html += `<button class="lesson type-${typeClass(l.typeShort)} ${isNow ? 'now' : ''} ${past ? 'past' : ''} ${l.cancelled ? 'cancelled' : ''}" data-uid="${esc(l.uid)}">
          <div class="time"><b>${esc(l.start)}</b><span>${esc(l.end)}</span></div>
          <div class="body">
            <div class="subj">${esc(l.subject)}</div>
            <div class="meta"><span class="kind">${esc(l.type)}</span>${l.room ? ` · ауд. ${esc(l.room)}` : ''}${l.teacher ? ` · ${esc(l.teacher)}` : ''}</div>
          </div>
          ${hw.length ? `<div class="hw-pill ${doneAll ? 'ok' : ''}">${doneAll ? '✓' : '📝 ' + open}</div>` : ''}
        </button>`;
      }
      html += '</div>';
    }

    const tomorrow = ymd(addDays(new Date(), 1));
    let dueToday = 0, dueTomorrow = 0;
    for (const h of state.homework) {
      if (h.done) continue;
      const d = dueLesson(h);
      if (d && d.date === today) dueToday++;
      if (d && d.date === tomorrow) dueTomorrow++;
    }
    if (dueToday || dueTomorrow) {
      const parts = [];
      if (dueToday) parts.push(`сегодня — ${dueToday}`);
      if (dueTomorrow) parts.push(`завтра — ${dueTomorrow}`);
      html = `<button class="banner" data-goto="homework">📝 Сдать ДЗ: ${parts.join(', ')} →</button>` + html;
    }
    if (!any) {
      html = `<div class="placeholder">${state.lessons.length
        ? 'На эту неделю расписания пока нет.<br>Сайт публикует расписание на эту и следующую неделю.'
        : 'Расписание ещё не загружено.'}</div>` + html;
    }
    if (state.lastUpdate) {
      const d = new Date(state.lastUpdate.at);
      html += `<p class="updated">Обновлено ${d.toLocaleDateString('ru-RU')} в ${d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })} (${esc(state.lastUpdate.source)})${state.calName ? '<br>' + esc(state.calName) : ''}</p>`;
    }
    $('#view-schedule').innerHTML = html;
  }

  function renderHomework() {
    const today = todayYmd();
    const pending = state.homework.filter((h) => !h.done).map((h) => ({ h, l: dueLesson(h) }));
    pending.sort((a, b) => (a.l ? lkey(a.l) : '9999').localeCompare(b.l ? lkey(b.l) : '9999'));
    const done = state.homework.filter((h) => h.done).sort((a, b) => (b.doneAt || '').localeCompare(a.doneAt || ''));

    let html = '';
    if (!pending.length) {
      html += '<div class="placeholder">🎉 Всё сделано!<br><small>Чтобы записать ДЗ, нажмите «＋» или на пару в расписании.</small></div>';
    } else {
      const groups = new Map();
      for (const it of pending) {
        const k = it.l ? it.l.date : '';
        if (!groups.has(k)) groups.set(k, []);
        groups.get(k).push(it);
      }
      for (const [date, items] of groups) {
        const overdue = date && date < today;
        const head = !date ? '⏳ Следующей пары ещё нет в расписании'
          : (overdue ? 'Просрочено · ' : 'Сдать: ') + relDate(date);
        html += `<h2 class="due-head ${overdue ? 'overdue' : ''}">${esc(head)}</h2>
          <ul class="hw-list">${items.map((it) => hwItem(it.h, true)).join('')}</ul>`;
      }
    }
    if (done.length) {
      html += `<details class="done-block"><summary>Выполнено (${done.length})</summary>
        <ul class="hw-list">${done.map((h) => hwItem(h, true)).join('')}</ul>
        <button class="btn ghost small" id="btn-clear-done">Удалить выполненные</button></details>`;
    }
    $('#view-homework').innerHTML = html;
  }

  function hwItem(h, showSubject) {
    const l = dueLesson(h);
    let meta = '';
    if (!h.done) {
      meta = l ? `📅 к паре ${esc(prettyDate(l.date).toLowerCase())}, ${esc(l.start)}` : '⏳ ждёт следующую пару в расписании';
      if (h.remind) {
        const at = remindAtFor(l);
        meta += at ? ` · 🔔 ${esc(remindLabel(at))}` : ' · 🔔 вкл.';
      }
    }
    return `<li class="hw ${h.done ? 'done' : ''}" data-id="${esc(h.id)}">
      <button class="check" data-act="toggle" aria-label="${h.done ? 'Отметить невыполненным' : 'Отметить выполненным'}">${h.done ? '✓' : ''}</button>
      <div class="hw-body">
        ${showSubject ? `<div class="hw-subj">${esc(h.subject)}</div>` : ''}
        <div class="hw-text">${esc(h.text)}</div>
        ${meta ? `<div class="hw-remind">${meta}</div>` : ''}
      </div>
      ${!h.done ? `<button class="bell ${h.remind ? 'on' : ''}" data-act="bell" aria-label="${h.remind ? 'Выключить напоминание' : 'Включить напоминание'}">${h.remind ? '🔔' : '🔕'}</button>` : ''}
      <button class="del" data-act="delete" aria-label="Удалить">✕</button>
    </li>`;
  }

  // ---------- lesson dialog ----------
  function openLesson(uidValue) {
    const l = state.lessons.find((x) => x.uid === uidValue);
    if (!l) return;
    state.openLesson = l;
    $('#lesson-info').innerHTML = `
      <div class="li-type type-${typeClass(l.typeShort)}">${esc(l.type)}</div>
      <h2>${esc(l.subject)}</h2>
      <p>${esc(prettyDate(l.date))}, ${esc(l.start)}–${esc(l.end)}</p>
      <p>${l.room ? 'Аудитория ' + esc(l.room) : ''}${l.teacher ? ' · ' + esc(l.teacher) : ''}</p>`;
    renderLessonHw();
    $('#hw-text').value = '';
    $('#hw-remind').checked = state.settings.remindDefault !== false;
    updateDuePreview();
    if ($('#dlg-pick').open) $('#dlg-pick').close();
    $('#dlg-lesson').showModal();
  }

  function updateDuePreview() {
    const l = state.openLesson;
    if (!l) return;
    const next = dueLesson({ subject: l.subject, fromDate: l.date, fromStart: l.start });
    $('#due-preview').innerHTML = next
      ? `📅 Сдать к следующей паре: <b>${esc(prettyDate(next.date).toLowerCase())}, ${esc(next.start)}</b> · ${esc(next.type)}`
      : '⏳ Следующей пары по этому предмету пока нет в расписании. Как только она появится на сайте, ДЗ привяжется к ней автоматически.';
    const at = remindAtFor(next);
    const el = $('#remind-preview');
    if (!$('#hw-remind').checked) el.textContent = 'выключено';
    else if (!next) el.textContent = 'накануне следующей пары, когда она появится';
    else if (at <= Date.now()) el.textContent = 'время уже прошло — напоминания не будет';
    else el.textContent = remindLabel(at);
  }

  function renderLessonHw() {
    const l = state.openLesson;
    const due = hwFor(l);
    const given = hwGivenAt(l).filter((h) => !due.includes(h));
    let html = '';
    if (due.length) html += '<li class="sub">Нужно сделать к этой паре</li>' + due.map((h) => hwItem(h, false)).join('');
    if (given.length) html += '<li class="sub">Задано на этой паре</li>' + given.map((h) => hwItem(h, false)).join('');
    if (!html) html = '<li class="empty">Заданий пока нет</li>';
    $('#lesson-hw').innerHTML = html;
  }

  function addHomework() {
    const l = state.openLesson;
    const text = $('#hw-text').value.trim();
    if (!l || !text) return false;
    const remind = $('#hw-remind').checked;
    state.homework.push({
      id: uid(), subject: l.subject, text,
      fromUid: l.uid, fromDate: l.date, fromStart: l.start,
      remind, done: false, createdAt: new Date().toISOString(),
    });
    save();
    if (remind) ensureNotifyPermission();
    return true;
  }

  function hwAction(id, act) {
    const i = state.homework.findIndex((h) => h.id === id);
    if (i < 0) return;
    const h = state.homework[i];
    if (act === 'toggle') {
      h.done = !h.done;
      h.doneAt = h.done ? new Date().toISOString() : null;
    } else if (act === 'bell') {
      h.remind = !h.remind;
      if (h.remind) {
        h.remindFrom = new Date().toISOString();
        ensureNotifyPermission();
        const at = remindAtFor(dueLesson(h));
        toast(!at ? 'Напомню накануне, когда пара появится в расписании'
          : at > Date.now() ? 'Напомню ' + remindLabel(at) : 'Время напоминания уже прошло');
      } else {
        toast('Напоминание выключено');
      }
    } else if (act === 'delete') {
      if (!confirm('Удалить задание?')) return;
      state.homework.splice(i, 1);
    }
    save();
    render();
    if (state.openLesson && $('#dlg-lesson').open) { renderLessonHw(); updateDuePreview(); }
  }

  // ---------- "+" : pick one of today's lessons ----------
  function openPicker() {
    const today = todayYmd();
    let day = today;
    let lessons = state.lessons.filter((l) => l.date === today);
    if (!lessons.length) {
      // No lessons today — offer the most recent day with lessons.
      const past = state.lessons.filter((l) => l.date < today);
      if (past.length) { day = past[past.length - 1].date; lessons = state.lessons.filter((l) => l.date === day); }
    }
    $('#pick-title').textContent = day === today ? 'Какая пара была сегодня?' : `Сегодня пар нет. ${prettyDate(day)}:`;
    $('#pick-list').innerHTML = lessons.length
      ? lessons.map((l) => `<button class="lesson type-${typeClass(l.typeShort)}" data-uid="${esc(l.uid)}">
          <div class="time"><b>${esc(l.start)}</b><span>${esc(l.end)}</span></div>
          <div class="body"><div class="subj">${esc(l.subject)}</div><div class="meta"><span class="kind">${esc(l.type)}</span></div></div>
        </button>`).join('')
      : '<p class="empty">В расписании нет пар.</p>';
    $('#dlg-pick').showModal();
  }

  // ---------- events ----------
  document.addEventListener('click', (e) => {
    const lessonBtn = e.target.closest('.lesson');
    if (lessonBtn) { openLesson(lessonBtn.dataset.uid); return; }
    const actBtn = e.target.closest('[data-act]');
    if (actBtn) {
      e.preventDefault();
      hwAction(actBtn.closest('[data-id]').dataset.id, actBtn.dataset.act);
      return;
    }
    const tab = e.target.closest('.tab, [data-goto]');
    if (tab) { state.tab = tab.dataset.tab || tab.dataset.goto; render(); window.scrollTo(0, 0); return; }
    if (e.target.id === 'btn-clear-done' && confirm('Удалить все выполненные задания?')) {
      state.homework = state.homework.filter((h) => !h.done);
      save(); render();
    }
  });

  $('#prev-week').onclick = () => { state.weekOffset--; render(); };
  $('#next-week').onclick = () => { state.weekOffset++; render(); };
  $('#this-week').onclick = () => { state.weekOffset = 0; render(); scrollToToday(); };
  $('#btn-refresh').onclick = () => refresh(true);
  $('#fab-add').onclick = openPicker;

  $('#btn-add-hw').onclick = (e) => {
    e.preventDefault();
    if (addHomework()) {
      $('#hw-text').value = '';
      renderLessonHw();
      render();
      toast('Задание добавлено');
    } else {
      $('#hw-text').focus();
    }
  };
  $('#dlg-lesson').addEventListener('close', () => { state.openLesson = null; });
  $('#hw-remind').onchange = () => {
    updateDuePreview();
    if ($('#hw-remind').checked) ensureNotifyPermission();
  };

  $('#btn-menu').onclick = () => {
    $('#set-url').value = state.settings.url || '';
    $('#set-remind-time').value = state.settings.remindTime || '18:00';
    $('#set-remind-default').checked = state.settings.remindDefault !== false;
    $('#dlg-settings').showModal();
  };
  $('#btn-save-settings').onclick = () => {
    state.settings.url = $('#set-url').value.trim();
    state.settings.remindTime = $('#set-remind-time').value || '18:00';
    state.settings.remindDefault = $('#set-remind-default').checked;
    save();
    render();
  };
  $('#btn-test-notify').onclick = async () => {
    if (!(await ensureNotifyPermission())) return;
    const reg = await navigator.serviceWorker.ready;
    reg.showNotification('📝 Проверка напоминаний', {
      body: 'Уведомления работают! Так будет выглядеть напоминание о ДЗ.',
      icon: 'icons/icon-192.png', badge: 'icons/icon-192.png', data: { url: './#homework' },
    });
  };
  $('#set-file').onchange = async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try {
      applyICS(await f.text(), 'файл ' + f.name);
      $('#dlg-settings').close();
      setStatus('');
      render();
      toast('Расписание загружено');
    } catch (err) { alert('Не удалось прочитать файл: ' + err.message); }
    e.target.value = '';
  };
  $('#btn-backup').onclick = () => {
    const blob = new Blob([JSON.stringify({ homework: state.homework }, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `dz-backup-${todayYmd()}.json`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  };
  $('#set-restore').onchange = async (e) => {
    const f = e.target.files[0];
    if (!f) return;
    try {
      const data = JSON.parse(await f.text());
      if (!Array.isArray(data.homework)) throw new Error('неверный формат');
      const ids = new Set(state.homework.map((h) => h.id));
      const added = data.homework.filter((h) => h && h.id && !ids.has(h.id));
      state.homework = state.homework.concat(added);
      migrate(); save(); render();
      $('#dlg-settings').close();
      toast(`Восстановлено заданий: ${added.length}`);
    } catch (err) { alert('Не удалось восстановить: ' + err.message); }
    e.target.value = '';
  };

  // Close dialogs by tapping the backdrop.
  document.querySelectorAll('dialog').forEach((d) => d.addEventListener('click', (e) => { if (e.target === d) d.close(); }));

  // Swipe left/right to change week.
  let tx = null, ty = null;
  $('main').addEventListener('touchstart', (e) => { tx = e.touches[0].clientX; ty = e.touches[0].clientY; }, { passive: true });
  $('main').addEventListener('touchend', (e) => {
    if (tx == null || state.tab !== 'schedule') return;
    const dx = e.changedTouches[0].clientX - tx, dy = e.changedTouches[0].clientY - ty;
    tx = null;
    if (Math.abs(dx) > 70 && Math.abs(dx) > Math.abs(dy) * 1.5) { state.weekOffset += dx < 0 ? 1 : -1; render(); }
  }, { passive: true });

  function scrollToToday() {
    const el = document.getElementById('today');
    if (el) el.scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') return;
    render();
    fireDueReminders();
    refreshIfNewDay();
  });

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
    navigator.serviceWorker.addEventListener('message', (e) => {
      if (e.data && e.data.type === 'open-homework') { state.tab = 'homework'; render(); }
    });
    if (typeof Notification !== 'undefined' && Notification.permission === 'granted') setupBackgroundSync();
  }
  if (location.hash === '#homework') state.tab = 'homework';

  migrate();
  syncReminders();
  render();
  setTimeout(scrollToToday, 50);
  if (!state.lessons.length) refresh(false); else refreshIfNewDay();
  setTimeout(fireDueReminders, 1500);
  setInterval(() => { fireDueReminders(); refreshIfNewDay(); }, 60 * 1000);
})();
