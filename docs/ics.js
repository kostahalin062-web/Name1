// Minimal iCalendar parser tailored to the ASU (АлтГУ) timetable export.
// Times are kept as wall-clock strings in the university's timezone, so the
// schedule shows the same times as the site regardless of the phone's timezone.
(function (global) {
  'use strict';

  const TYPES = {
    'лек.': 'Лекция',
    'пр.з.': 'Практика',
    'лаб.': 'Лабораторная',
    'сем.': 'Семинар',
    'конс.': 'Консультация',
    'зач.': 'Зачёт',
    'экз.': 'Экзамен',
  };

  function unescapeText(s) {
    return s
      .replace(/\\n/gi, '\n')
      .replace(/\\([,;\\])/g, '$1');
  }

  // "20261123T164000" -> { date: "2026-11-23", time: "16:40" }
  function parseDate(value) {
    const m = /^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2}))?/.exec(value);
    if (!m) return null;
    return { date: `${m[1]}-${m[2]}-${m[3]}`, time: m[4] ? `${m[4]}:${m[5]}` : '' };
  }

  function parseICS(text) {
    // Unfold continuation lines (RFC 5545 §3.1).
    const lines = text.replace(/\r\n|\r/g, '\n').replace(/\n[ \t]/g, '').split('\n');
    const events = [];
    let cur = null;
    let calName = '';

    for (const line of lines) {
      if (line === 'BEGIN:VEVENT') { cur = {}; continue; }
      if (line === 'END:VEVENT') { if (cur) events.push(cur); cur = null; continue; }
      const idx = line.indexOf(':');
      if (idx < 0) continue;
      const name = line.slice(0, idx).split(';')[0].toUpperCase();
      const value = line.slice(idx + 1);
      if (!cur) {
        if (name === 'X-WR-CALNAME') calName = unescapeText(value);
        continue;
      }
      cur[name] = name.startsWith('DT') ? value : unescapeText(value);
    }

    const lessons = events.map(toLesson).filter(Boolean);
    lessons.sort((a, b) => (a.date + a.start).localeCompare(b.date + b.start));
    return { calName, lessons };
  }

  function toLesson(ev) {
    const start = ev.DTSTART && parseDate(ev.DTSTART);
    if (!start) return null;
    const end = ev.DTEND ? parseDate(ev.DTEND) : null;
    const room = (ev.LOCATION || '').trim();

    // SUMMARY looks like: "16:40 лек. Теория вероятностей ... (Н)"
    let summary = (ev.SUMMARY || '').trim();
    summary = summary.replace(/^\d{1,2}:\d{2}\s+/, '');
    let typeShort = '';
    const tm = /^(\S+\.)\s+/.exec(summary);
    if (tm && /^[а-яё.]+$/i.test(tm[1])) {
      typeShort = tm[1];
      summary = summary.slice(tm[0].length);
    }
    if (room && summary.endsWith(`(${room})`)) {
      summary = summary.slice(0, -(room.length + 2)).trim();
    }

    const teacher = (ev.DESCRIPTION || '').replace(/\s*\(преп\.\)\s*/g, '').trim();

    return {
      uid: ev.UID || `${start.date}T${start.time}-${summary}`,
      date: start.date,
      start: start.time,
      end: end ? end.time : '',
      subject: summary,
      type: TYPES[typeShort] || typeShort.replace(/\.$/, ''),
      typeShort,
      room,
      teacher,
      cancelled: (ev.STATUS || '').toUpperCase() === 'CANCELLED',
    };
  }

  global.parseICS = parseICS;
})(typeof window !== 'undefined' ? window : globalThis);
