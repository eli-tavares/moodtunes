'use strict';

// Bump CACHE_VERSION whenever the app shell changes so old caches are dropped.
const CACHE_VERSION = 'moodtunes-v3';
const SHELL = [
    '/',
    '/styles.css',
    '/app.js',
    '/charts.js',
    '/manifest.webmanifest',
    '/favicon/apple-touch-icon.png',
    '/favicon/android-chrome-192x192.png',
    '/favicon/android-chrome-512x512.png',
    '/favicon/favicon-32x32.png',
];

self.addEventListener('install', (event) => {
    event.waitUntil(caches.open(CACHE_VERSION).then((cache) => cache.addAll(SHELL)));
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(keys.filter((k) => k !== CACHE_VERSION).map((k) => caches.delete(k))))
            .then(() => self.clients.claim())
    );
});

// Network-first for the app shell (always fresh when online, still opens offline).
// API calls are never cached or intercepted: recommendations need the live AI backend.
self.addEventListener('fetch', (event) => {
    const req = event.request;
    const url = new URL(req.url);
    if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')
        || url.pathname.startsWith('/actuator/')) {
        return;
    }
    event.respondWith(
        fetch(req)
            .then((resp) => {
                if (resp.ok) {
                    const copy = resp.clone();
                    caches.open(CACHE_VERSION).then((cache) => cache.put(req, copy));
                }
                return resp;
            })
            .catch(() => caches.match(req).then((hit) => hit || caches.match('/')))
    );
});
