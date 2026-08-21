// =========================================
// PWA Settings
// =========================================

// Install prompt handler
let deferredPrompt = null;

window.addEventListener('beforeinstallprompt', function(e) {
    // Prevent Chrome 67 and earlier from automatically showing the prompt
    e.preventDefault();
    // Stash the event so it can be triggered later
    deferredPrompt = e;
    
    // Show install button or notification
    console.log('PWA install prompt available');
    
    // You can show a custom install button here
});

// Handle install button click
function installPWA() {
    if (deferredPrompt) {
        deferredPrompt.prompt();
        deferredPrompt.userChoice.then(function(choiceResult) {
            if (choiceResult.outcome === 'accepted') {
                console.log('PWA installed');
                UI.showNotification('تم تثبيت التطبيق بنجاح', 'success');
            } else {
                console.log('PWA installation declined');
            }
            deferredPrompt = null;
        });
    }
}

// Check if app is installed
window.addEventListener('appinstalled', function() {
    console.log('PWA installed successfully');
    UI.showNotification('تم تثبيت التطبيق', 'success');
});

// Network status detection
window.addEventListener('online', function() {
    UI.showNotification('تم استعادة الاتصال بالإنترنت', 'success');
    // Sync data when back online
    if (typeof Events !== 'undefined') {
        Events.syncData();
    }
});

window.addEventListener('offline', function() {
    UI.showNotification('تم قطع الاتصال بالإنترنت - وضع غير متصل', 'error');
});

// Handle visibility change for background sync
document.addEventListener('visibilitychange', function() {
    if (!document.hidden && !navigator.onLine) {
        // App became visible while offline
        console.log('App is visible but offline');
    }
});
