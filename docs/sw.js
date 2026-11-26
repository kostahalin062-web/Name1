// Offline support + reminders.
importScripts('reminders-db.js');

const CACHE = 'dz-v3';
const SHELL = ['./', 'index.html', 'style.css', 'ics.js', 'reminders-db.js', 'app.js', 'manifest.webmanifest', 'icons/icon-192.png', 'icons/icon-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // Network first, so updates arrive right away; cache as offline fallback.
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        if (res.ok && !url.pathname.endsWith('schedule.ics')) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(e.request, copy));
        }
        return res;
      })
      .catch(() => caches.match(e.request, { ignoreSearch: true }))
  );
});

// Chrome on Android wakes the installed app in the background from time to time.
self.addEventListener('periodicsync', (e) => {
  if (e.tag === 'dz-reminders') e.waitUntil(self.ReminderDB.fireDue(self.registration));
});

self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const target = new URL((e.notification.data && e.notification.data.url) || './', self.registration.scope).href;
  e.waitUntil((async () => {
    const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    for (const c of all) {
      if (c.url.startsWith(self.registration.scope)) {
        await c.focus();
        c.postMessage({ type: 'open-homework' });
        return;
      }
    }
    await self.clients.openWindow(target);
  })());
});
