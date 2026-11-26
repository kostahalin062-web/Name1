// Shared between the page and the service worker: reminders live in IndexedDB
// so the service worker can show them even when the app is closed.
(function (g) {
  'use strict';
  const DB = 'dz-reminders';
  const STORE = 'kv';

  function open() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(DB, 1);
      req.onupgradeneeded = () => req.result.createObjectStore(STORE);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }
  async function get(key, fallback) {
    const db = await open();
    return new Promise((resolve) => {
      const r = db.transaction(STORE).objectStore(STORE).get(key);
      r.onsuccess = () => resolve(r.result === undefined ? fallback : r.result);
      r.onerror = () => resolve(fallback);
    });
  }
  async function set(key, value) {
    const db = await open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, 'readwrite');
      tx.objectStore(STORE).put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  // reminders: [{ id, at, from, title, body }]
  //   at   – when to notify (ms), from – reminder is valid only if at > from
  //   (so a reminder whose time already passed when it was created never fires).
  async function fireDue(registration) {
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return 0;
    const now = Date.now();
    const reminders = await get('reminders', []);
    const shown = new Set(await get('shown', []));
    let count = 0;
    for (const r of reminders) {
      if (shown.has(r.key) || r.at > now || r.at <= r.from) continue;
      if (now - r.at > 36 * 3600 * 1000) { shown.add(r.key); continue; } // too old, skip
      await registration.showNotification(r.title, {
        body: r.body,
        tag: 'dz-' + r.id,
        icon: 'icons/icon-192.png',
        badge: 'icons/icon-192.png',
        data: { url: './#homework' },
      });
      shown.add(r.key);
      count++;
    }
    // keep only keys that still exist
    const keys = new Set(reminders.map((r) => r.key));
    await set('shown', [...shown].filter((k) => keys.has(k)));
    return count;
  }

  g.ReminderDB = { get, set, fireDue };
})(typeof self !== 'undefined' ? self : window);
