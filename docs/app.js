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
    settings: Object.assign({ url: DEFAULT_URL }, store.get('settings', {})),
    lastUpdate: store.get('lastUpdate', null),
    calName: store.get('calName', ''),
    weekOffset: 0,
    tab: 'schedule',
    openLesson: null,
  };

  // ---------- helpers ----------
  const $ = (s) => document.querySelector(s);
  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  function ymd(d) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
  function fromYmd(s) {
    const [y, m, d] = s.split('-').map(Number);
    return new Date(y, m - 1, d);
  }
  function addDays(d, n) { const r = new Date(d); r.setDate(r.getDate() + n); return r; }
  function mondayOf(d) { const r = new Date(d.getFullYear(), d.getMonth(), d.getDate()); const wd = (r.getDay() + 6) % 7; return addDays(r, -wd); }
  function todayYmd() { return ymd(new Date()); }
  function prettyDate(s, withWeekday = true) {
    const d = fromYmd(s);
    const base = `${d.getDate()} ${MONTHS[d.getMonth()]}`;
    return withWeekday ? `${DAYS[d.getDay()]}, ${base}` : base;
  }
  function relDate(s) {
    const diff = Math.round((fromYmd(s) - fromYmd(todayYmd())) / 86400000);
    if (diff === 0) return 'сегодня';
    if (diff === 1) return 'завтра';
    if (diff === -1) return 'вчера';
    return prettyDate(s);
  }
  function uid() { return Date.now().toString(36) + Math.random().toString(36).slice(2, 7); }

  function save() {
    store.set('lessons', state.lessons);
    store.set('homework', state.homework);
    store.set('settings', state.settings);
    store.set('lastUpdate', state.lastUpdate);
    store.set('calName', state.calName);
  }

  // ---------- schedule loading ----------
  function applyICS(text, source) {
    const { calName, lessons } = window.parseICS(text);
    if (!lessons.length) throw new Error('В файле нет пар');
    const min = lessons[0].date;
    const max = lessons[lessons.length - 1].date;
    // Keep previously known lessons outside the range of the new file (history).
    const kept = state.lessons.filter((l) => l.date < min || l.date > max);
    state.lessons = kept.concat(lessons).sort((a, b) => (a.date + a.start).localeCompare(b.date + b.start));
    if (calName) state.calName = calName;
    state.lastUpdate = { at: new Date().toISOString(), source };
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
    setStatus('Обновляю расписание…');
    const sources = [];
    if (state.settings.url) sources.push([state.settings.url, 'сайт АлтГУ']);
    sources.push([LOCAL_ICS + '?t=' + Date.now(), 'копия на GitHub']);
    let ok = false;
    for (const [url, name] of sources) {
      try {
        applyICS(await fetchText(url), name);
        ok = true;
        break;
      } catch (e) { /* try next source */ }
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
    toastTimer = setTimeout(() => el.classList.remove('show'), 2200);
  }

  // ---------- homework logic ----------
  function hwFor(lesson) {
    return state.homework.filter((h) => h.lessonUid === lesson.uid ||
      (!h.lessonUid && h.subject === lesson.subject && h.due === lesson.date));
  }
  function pendingCount() { return state.homework.filter((h) => !h.done).length; }

  // Upcoming lessons of the same subject after the given lesson (for "сдать к").
  function nextLessonsOf(lesson, limit = 8) {
    const key = lesson.date + lesson.start;
    return state.lessons
      .filter((l) => l.subject === lesson.subject && (l.date + l.start) > key && !l.cancelled)
      .slice(0, limit);
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

  function renderSchedule() {
    const monday = addDays(mondayOf(new Date()), state.weekOffset * 7);
    const sunday = addDays(monday, 6);
    const label = state.weekOffset === 0 ? 'Эта неделя' : state.weekOffset === 1 ? 'Следующая неделя' : state.weekOffset === -1 ? 'Прошлая неделя' : '';
    $('#this-week').innerHTML = `<b>${label || 'Неделя'}</b><small>${monday.getDate()} ${MONTHS[monday.getMonth()]} — ${sunday.getDate()} ${MONTHS[sunday.getMonth()]}</small>`;

    const today = todayYmd();
    const now = new Date();
    const nowHM = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
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
        const isNow = date === today && l.start <= nowHM && nowHM < l.end;
        const past = date < today || (date === today && l.end <= nowHM);
        html += `<button class="lesson type-${esc(typeClass(l.typeShort))} ${isNow ? 'now' : ''} ${past ? 'past' : ''} ${l.cancelled ? 'cancelled' : ''}" data-uid="${esc(l.uid)}">
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
    if (!any) {
      html = `<div class="placeholder">${state.lessons.length
        ? 'На эту неделю расписания пока нет.<br>Сайт публикует расписание примерно на 2 недели вперёд.'
        : 'Расписание ещё не загружено.'}</div>` + html;
    }
    if (state.lastUpdate) {
      const d = new Date(state.lastUpdate.at);
      html += `<p class="updated">Обновлено ${d.toLocaleDateString('ru-RU')} в ${d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })} (${esc(state.lastUpdate.source)})${state.calName ? '<br>' + esc(state.calName) : ''}</p>`;
    }
    $('#view-schedule').innerHTML = html;
  }

  function typeClass(t) {
    return { 'лек.': 'lec', 'пр.з.': 'prac', 'лаб.': 'lab' }[t] || 'other';
  }

  function renderHomework() {
    const today = todayYmd();
    const pending = state.homework.filter((h) => !h.done).sort((a, b) => (a.due || '9999').localeCompare(b.due || '9999'));
    const done = state.homework.filter((h) => h.done).sort((a, b) => (b.doneAt || '').localeCompare(a.doneAt || ''));
    let html = '';
    if (!pending.length) {
      html += '<div class="placeholder">🎉 Всё сделано!<br><small>Чтобы добавить задание, нажмите на пару в расписании.</small></div>';
    } else {
      const groups = new Map();
      for (const h of pending) {
        if (!groups.has(h.due)) groups.set(h.due, []);
        groups.get(h.due).push(h);
      }
      for (const [due, items] of groups) {
        const overdue = due && due < today;
        html += `<h2 class="due-head ${overdue ? 'overdue' : ''}">${due ? (overdue ? 'Просрочено · ' : 'Сдать: ') + esc(relDate(due)) : 'Без срока'}</h2>
          <ul class="hw-list">${items.map((h) => hwItem(h, true)).join('')}</ul>`;
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
    return `<li class="hw ${h.done ? 'done' : ''}" data-id="${esc(h.id)}">
      <button class="check" data-act="toggle" aria-label="${h.done ? 'Отметить невыполненным' : 'Отметить выполненным'}">${h.done ? '✓' : ''}</button>
      <div class="hw-body">
        ${showSubject ? `<div class="hw-subj">${esc(h.subject)}</div>` : ''}
        <div class="hw-text">${esc(h.text)}</div>
      </div>
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

    const next = nextLessonsOf(l);
    const opts = next.map((n) => `<option value="${esc(n.uid)}">${esc(prettyDate(n.date))}, ${esc(n.start)} · ${esc(n.type)}</option>`);
    opts.push(`<option value="this">К этой паре (${esc(prettyDate(l.date))})</option>`);
    if (!next.length) {
      const week = ymd(addDays(fromYmd(l.date), 7));
      opts.unshift(`<option value="date:${week}">Через неделю (${esc(prettyDate(week))})</option>`);
    }
    $('#hw-due').innerHTML = opts.join(''); // default: the next lesson of this subject
    $('#hw-text').value = '';
    $('#dlg-lesson').showModal();
  }

  function renderLessonHw() {
    const l = state.openLesson;
    const due = hwFor(l);
    const given = state.homework.filter((h) => h.fromUid === l.uid && !due.includes(h));
    let html = '';
    if (due.length) html += `<li class="sub">Нужно сделать к этой паре</li>` + due.map((h) => hwItem(h, false)).join('');
    if (given.length) html += `<li class="sub">Задано на этой паре</li>` + given.map((h) => hwItem(h, false)).join('');
    if (!html) html = '<li class="empty">Заданий пока нет</li>';
    $('#lesson-hw').innerHTML = html;
  }

  function addHomework() {
    const l = state.openLesson;
    const text = $('#hw-text').value.trim();
    if (!l || !text) return false;
    const sel = $('#hw-due').value;
    let due, lessonUid = null;
    if (sel === 'this') { due = l.date; lessonUid = l.uid; }
    else if (sel.startsWith('date:')) { due = sel.slice(5); }
    else {
      const target = state.lessons.find((x) => x.uid === sel);
      due = target ? target.date : l.date;
      lessonUid = target ? target.uid : null;
    }
    state.homework.push({ id: uid(), subject: l.subject, text, due, lessonUid, fromUid: l.uid, done: false, createdAt: new Date().toISOString() });
    save();
    return true;
  }

  function hwAction(id, act) {
    const i = state.homework.findIndex((h) => h.id === id);
    if (i < 0) return;
    if (act === 'toggle') {
      const h = state.homework[i];
      h.done = !h.done;
      h.doneAt = h.done ? new Date().toISOString() : null;
    } else if (act === 'delete') {
      if (!confirm('Удалить задание?')) return;
      state.homework.splice(i, 1);
    }
    save();
    render();
    if (state.openLesson && $('#dlg-lesson').open) renderLessonHw();
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
    const tab = e.target.closest('.tab');
    if (tab) { state.tab = tab.dataset.tab; render(); window.scrollTo(0, 0); return; }
    if (e.target.id === 'btn-clear-done') {
      if (confirm('Удалить все выполненные задания?')) {
        state.homework = state.homework.filter((h) => !h.done);
        save(); render();
      }
    }
  });

  $('#prev-week').onclick = () => { state.weekOffset--; render(); };
  $('#next-week').onclick = () => { state.weekOffset++; render(); };
  $('#this-week').onclick = () => { state.weekOffset = 0; render(); scrollToToday(); };
  $('#btn-refresh').onclick = () => refresh(true);

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

  $('#btn-menu').onclick = () => {
    $('#set-url').value = state.settings.url || '';
    $('#dlg-settings').showModal();
  };
  $('#btn-save-settings').onclick = () => {
    state.settings.url = $('#set-url').value.trim();
    save();
    refresh(true);
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
      save(); render();
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

  // Refresh when the app comes back to the foreground (at most every 30 minutes).
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') return;
    render();
    const last = state.lastUpdate ? Date.parse(state.lastUpdate.at) : 0;
    if (Date.now() - last > 30 * 60 * 1000) refresh(false);
  });

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  render();
  setTimeout(scrollToToday, 50);
  refresh(false);
})();
