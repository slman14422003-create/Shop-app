// =========================================
// Main Application - مع تحسين التحميل الأولي
// =========================================

// متغير للتحكم في حالة التحميل
let appInitialized = false;
let loadingTimeout = null;
let retryCount = 0;
const MAX_RETRIES = 3;

// Wait for DOM to load
document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 بدء تحميل التطبيق...');
    
    // بدء计时 التحميل
    let startTime = Date.now();
    
    // تحديث شريط التقدم
    updateSplashProgress(10, 'جاري تهيئة التطبيق...');
    
    // محاولة تهيئة Firebase مع إعادة المحاولة
    initializeAppWithRetry();
});

// تهيئة التطبيق مع إعادة المحاولة
function initializeAppWithRetry() {
    try {
        updateSplashProgress(20, 'جاري الاتصال بقاعدة البيانات...');
        
        // تهيئة Firebase
        const firebaseReady = initFirebase();
        
        if (firebaseReady) {
            console.log('✅ Firebase initialized successfully');
            startApp();
        } else {
            console.warn('⚠️ Firebase initialization failed, retrying...');
            retryCount++;
            
            if (retryCount < MAX_RETRIES) {
                updateSplashProgress(15, 'إعادة محاولة الاتصال... (' + retryCount + '/' + MAX_RETRIES + ')');
                setTimeout(initializeAppWithRetry, 1000);
            } else {
                // إذا فشل Firebase بعد عدة محاولات، نعمل في وضع غير متصل
                console.warn('⚠️ All Firebase retries failed, starting in offline mode');
                updateSplashProgress(30, 'وضع غير متصل...');
                startAppOffline();
            }
        }
    } catch (error) {
        console.error('❌ App initialization error:', error);
        retryCount++;
        
        if (retryCount < MAX_RETRIES) {
            setTimeout(initializeAppWithRetry, 1000);
        } else {
            startAppOffline();
        }
    }
}

// بدء التطبيق
function startApp() {
    try {
        updateSplashProgress(40, 'جاري تحميل المواد...');
        
        // تحميل التفضيلات
        Events.loadTheme();
        
        // تحميل المواد
        Materials.loadMaterials('main');
        
        updateSplashProgress(60, 'جاري تحميل الأسعار...');
        
        // تحميل الأسعار مع تأخير
        setTimeout(function() {
            try {
                PriceManager.loadPrices();
                updateSplashProgress(80, 'جاري تحليل المخزون...');
            } catch (error) {
                console.error('Price load error:', error);
            }
        }, 300);
        
        // تهيئة الأحداث
        Events.init();
        
        // تهيئة السحب والإفلات
        DragDrop.init();
        
        updateSplashProgress(90, 'جاري التجهيز النهائي...');
        
        // إخفاء شاشة البداية
        setTimeout(function() {
            hideSplashScreen();
            appInitialized = true;
            console.log('✅ App initialized successfully in ' + (Date.now() - startTime) + 'ms');
            
            // التحقق من وجود بيانات
            checkDataAvailability();
        }, 500);
        
    } catch (error) {
        console.error('❌ Start app error:', error);
        startAppOffline();
    }
}

// بدء التطبيق في وضع غير متصل
function startAppOffline() {
    try {
        updateSplashProgress(30, 'وضع غير متصل - تحميل البيانات المحلية...');
        
        // تحميل التفضيلات
        Events.loadTheme();
        
        // تحميل من التخزين المحلي
        Materials.loadFromCache();
        PriceManager.loadFromLocal();
        
        // تحديث التحليل
        setTimeout(function() {
            PriceManager.updateAnalysisAfterLoad();
        }, 300);
        
        // تهيئة الأحداث
        Events.init();
        
        // تهيئة السحب والإفلات
        DragDrop.init();
        
        updateSplashProgress(80, 'التجهيز النهائي...');
        
        // إخفاء شاشة البداية
        setTimeout(function() {
            hideSplashScreen();
            appInitialized = true;
            
            // عرض رسالة وضع غير متصل
            UI.showNotification('📡 وضع غير متصل - البيانات من التخزين المحلي', 'warning');
            console.log('✅ App started in offline mode');
        }, 500);
        
    } catch (error) {
        console.error('❌ Offline start error:', error);
        // إخفاء شاشة البداية حتى في حالة الخطأ
        setTimeout(hideSplashScreen, 1000);
    }
}

// تحديث شريط التقدم
function updateSplashProgress(percent, message) {
    const progressBar = document.getElementById('splashProgress');
    const progressText = document.querySelector('.splash-content p');
    
    if (progressBar) {
        progressBar.style.width = percent + '%';
    }
    
    if (progressText && message) {
        progressText.textContent = message;
    }
}

// إخفاء شاشة البداية
function hideSplashScreen() {
    const splash = document.getElementById('splash-screen');
    if (splash) {
        splash.classList.add('hidden');
        // إزالة من DOM بعد الانتهاء من الرسوم المتحركة
        setTimeout(function() {
            if (splash.parentNode) {
                splash.style.display = 'none';
            }
        }, 600);
    }
    
    // إلغاء أي مؤقتات
    if (loadingTimeout) {
        clearTimeout(loadingTimeout);
        loadingTimeout = null;
    }
}

// التحقق من وجود بيانات
function checkDataAvailability() {
    setTimeout(function() {
        const materials = Materials.getAllMaterials();
        if (!materials || materials.length === 0) {
            // لا توجد بيانات، عرض رسالة ترحيبية
            const container = document.getElementById('materialsContainer');
            if (container) {
                container.innerHTML = `
                    <div class="empty-state welcome">
                        <i class="fas fa-hand-wave"></i>
                        <h3>مرحباً بك في مدير المواد الذكي</h3>
                        <p>ابدأ بإضافة المواد من خلال زر <strong>"إضافة"</strong></p>
                        <p style="font-size:0.8rem;color:var(--text-muted);margin-top:8px;">
                            أو استخدم <strong>"قوائم"</strong> لإضافة مجموعات جاهزة
                        </p>
                        <div style="display:flex;gap:8px;justify-content:center;margin-top:12px;flex-wrap:wrap;">
                            <button onclick="UI.showModal('addModal')" class="btn btn-primary" style="width:auto;padding:8px 20px;">
                                <i class="fas fa-plus"></i> إضافة مادة
                            </button>
                            <button onclick="Presets.openModal()" class="btn btn-secondary" style="width:auto;padding:8px 20px;">
                                <i class="fas fa-list"></i> قوائم جاهزة
                            </button>
                        </div>
                    </div>
                `;
            }
        }
    }, 1000);
}

// إضافة مستمع لحدث الاتصال
window.addEventListener('online', function() {
    if (appInitialized) {
        UI.showNotification('🌐 تم استعادة الاتصال بالإنترنت', 'success');
        // محاولة المزامنة
        if (typeof Events !== 'undefined') {
            Events.syncData();
        }
    }
});

window.addEventListener('offline', function() {
    if (appInitialized) {
        UI.showNotification('📡 تم قطع الاتصال - وضع غير متصل', 'warning');
    }
});

// Make functions globally accessible
window.addMaterial = function(e) {
    e.preventDefault();
    const name = document.getElementById('addName').value;
    const quantity = document.getElementById('addQuantity').value;
    const unit = document.getElementById('addUnit').value;
    const section = document.getElementById('addSection').value;
    
    if (!name.trim()) {
        UI.showNotification('يرجى إدخال اسم المادة', 'error');
        return;
    }
    
    if (!quantity || parseFloat(quantity) <= 0) {
        UI.showNotification('يرجى إدخال كمية صحيحة', 'error');
        return;
    }
    
    Materials.addMaterial(name, quantity, unit, section);
};

window.saveEdit = function(e) {
    e.preventDefault();
    const id = document.getElementById('editId').value;
    const name = document.getElementById('editName').value;
    const quantity = document.getElementById('editQuantity').value;
    const unit = document.getElementById('editUnit').value;
    const section = document.getElementById('editSection').value;
    
    if (!name.trim()) {
        UI.showNotification('يرجى إدخال اسم المادة', 'error');
        return;
    }
    
    if (!quantity || parseFloat(quantity) <= 0) {
        UI.showNotification('يرجى إدخال كمية صحيحة', 'error');
        return;
    }
    
    Materials.saveEdit(id, name, quantity, unit, section);
};

window.closeModal = function(modalId) {
    if (modalId === 'priceModal') {
        PriceManager.isPriceModalOpen = false;
    }
    UI.hideModal(modalId);
};

window.saveAllPrices = function() {
    PriceManager.saveAllPrices();
};

// Service Worker Registration مع تحسين التعامل مع الأخطاء
if ('serviceWorker' in navigator) {
    // تأخير تسجيل Service Worker لتجنب تعطيل التحميل الأولي
    setTimeout(function() {
        navigator.serviceWorker.register('sw.js')
            .then(function(registration) {
                console.log('✅ Service Worker registered successfully');
            })
            .catch(function(error) {
                console.warn('⚠️ Service Worker registration failed:', error);
                // لا نعرض خطأ للمستخدم لأن التطبيق يعمل بدون Service Worker
            });
    }, 3000);
}

// تصحيح أخطاء Firebase SDK
console.log('📦 Material Manager v12.0');
console.log('📱 Device: ' + (window.innerWidth < 768 ? 'Mobile' : 'Desktop'));
console.log('🌐 Online: ' + (navigator.onLine ? 'Yes' : 'No'));
