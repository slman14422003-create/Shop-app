// =========================================
// Service Worker for PWA
// =========================================

const CACHE_NAME = 'material-manager-v12';
const OFFLINE_URL = 'offline.html';

// Files to cache
const FILES_TO_CACHE = [
    '/material-manager/',
    '/material-manager/index.html',
    '/material-manager/offline.html',
    '/material-manager/manifest.json',
    '/material-manager/css/style.css',
    '/material-manager/js/firebase-config.js',
    '/material-manager/js/ai-engine.js',
    '/material-manager/js/ai-assistant.js',
    '/material-manager/js/modules/utils.js',
    '/material-manager/js/modules/ui.js',
    '/material-manager/js/modules/materials.js',
    '/material-manager/js/modules/presets.js',
    '/material-manager/js/modules/dragdrop.js',
    '/material-manager/js/modules/prices.js',
    '/material-manager/js/modules/events.js',
    '/material-manager/js/app.js',
    '/material-manager/js/pwa-settings.js'
];

// Install event
self.addEventListener('install', function(event) {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(function(cache) {
                console.log('Caching app shell');
                return cache.addAll(FILES_TO_CACHE);
            })
            .then(function() {
                return self.skipWaiting();
            })
    );
});

// Activate event
self.addEventListener('activate', function(event) {
    event.waitUntil(
        caches.keys().then(function(cacheNames) {
            return Promise.all(
                cacheNames.map(function(cacheName) {
                    if (cacheName !== CACHE_NAME) {
                        console.log('Deleting old cache:', cacheName);
                        return caches.delete(cacheName);
                    }
                })
            );
        }).then(function() {
            return self.clients.claim();
        })
    );
});

// Fetch event
self.addEventListener('fetch', function(event) {
    // Skip Firebase and other external requests
    if (event.request.url.includes('firebase') ||
        event.request.url.includes('googleapis') ||
        event.request.url.includes('gstatic')) {
        event.respondWith(fetch(event.request));
        return;
    }
    
    event.respondWith(
        caches.match(event.request)
            .then(function(response) {
                // Return cached response if available
                if (response) {
                    return response;
                }
                
                // Otherwise try network
                return fetch(event.request)
                    .then(function(networkResponse) {
                        // Don't cache if not successful
                        if (!networkResponse || networkResponse.status !== 200) {
                            return networkResponse;
                        }
                        
                        // Cache the new response
                        const responseClone = networkResponse.clone();
                        caches.open(CACHE_NAME)
                            .then(function(cache) {
                                cache.put(event.request, responseClone);
                            });
                        
                        return networkResponse;
                    })
                    .catch(function() {
                        // If network fails and request is for HTML, show offline page
                        if (event.request.headers.get('accept').includes('text/html')) {
                            return caches.match(OFFLINE_URL);
                        }
                    });
            })
    );
});

// Push notification event
self.addEventListener('push', function(event) {
    const data = event.data.json();
    const options = {
        body: data.body || 'تحديث في مدير المواد',
        icon: '/material-manager/icons/icon-192x192.png',
        badge: '/material-manager/icons/icon-72x72.png',
        vibrate: [200, 100, 200],
        data: {
            url: data.url || '/material-manager/'
        }
    };
    
    event.waitUntil(
        self.registration.showNotification(data.title || 'مدير المواد', options)
    );
});

// Notification click event
self.addEventListener('notificationclick', function(event) {
    event.notification.close();
    
    event.waitUntil(
        clients.openWindow(event.notification.data.url || '/material-manager/')
    );
});
