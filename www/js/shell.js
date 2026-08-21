(function () {
    'use strict';

    var tabs = Array.prototype.slice.call(document.querySelectorAll('.tab-btn'));
    var frames = {
        'frame-debt': document.getElementById('frame-debt'),
        'frame-materials': document.getElementById('frame-materials')
    };
    var loaded = { 'frame-debt': true, 'frame-materials': false };
    var currentTab = 'frame-debt';

    function activate(targetId) {
        tabs.forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-target') === targetId);
        });
        Object.keys(frames).forEach(function (id) {
            frames[id].classList.toggle('active', id === targetId);
        });
        if (!loaded[targetId]) {
            var frame = frames[targetId];
            var src = frame.getAttribute('data-src');
            if (src) {
                frame.setAttribute('src', src);
                frame.removeAttribute('data-src');
            }
            loaded[targetId] = true;
        }
        currentTab = targetId;
    }

    tabs.forEach(function (btn) {
        btn.addEventListener('click', function () {
            activate(btn.getAttribute('data-target'));
        });
    });

    // Android hardware back button (Cordova): go to the debts tab first,
    // then let a second press exit the app instead of quitting instantly.
    var exitArmed = false;
    document.addEventListener('deviceready', function () {
        document.addEventListener('backbutton', function (e) {
            e.preventDefault();
            if (currentTab !== 'frame-debt') {
                activate('frame-debt');
                exitArmed = false;
                return;
            }
            if (exitArmed) {
                navigator.app.exitApp();
            } else {
                exitArmed = true;
                if (window.cordova && cordova.plugins && cordova.plugins.notification) {
                    // optional toast plugin, ignored if absent
                }
                setTimeout(function () { exitArmed = false; }, 2000);
            }
        }, false);
    }, false);
})();
