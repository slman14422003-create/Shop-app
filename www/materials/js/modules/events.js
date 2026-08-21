// =========================================
// Events Module
// =========================================

const Events = {
    // Initialize all event listeners
    init: function() {
        // Add button
        document.getElementById('addBtn').addEventListener('click', function() {
            UI.showModal('addModal');
        });
        
        // Presets button
        document.getElementById('presetsBtn').addEventListener('click', function() {
            Presets.openModal();
        });
        
        // Prices button
        document.getElementById('pricesBtn').addEventListener('click', function() {
            PriceManager.openModal();
        });
        
        // Sync button
        document.getElementById('syncBtn').addEventListener('click', function() {
            Events.syncData();
        });
        
        // Theme toggle
        document.getElementById('themeToggle').addEventListener('click', function() {
            Events.toggleTheme();
        });
        
        // Backup button
        document.getElementById('backupBtn').addEventListener('click', function() {
            Events.backupData();
        });
        
        // Restore button
        document.getElementById('restoreBtn').addEventListener('click', function() {
            Events.restoreData();
        });
        
        // Clear button
        document.getElementById('clearBtn').addEventListener('click', function() {
            Materials.clearAll();
        });
        
        // Preset tabs
        document.querySelectorAll('.preset-tab').forEach(function(tab) {
            tab.addEventListener('click', function() {
                const section = this.dataset.section;
                Presets.renderPresets(section);
            });
        });
        
        // Price search
        document.getElementById('priceSearch').addEventListener('input', Utils.debounce(function() {
            PriceManager.renderPriceList();
        }, 300));
        
        // Price filter
        document.getElementById('priceFilter').addEventListener('change', function() {
            PriceManager.renderPriceList();
        });
        
        // Close modals on backdrop click
        document.querySelectorAll('.modal').forEach(function(modal) {
            modal.addEventListener('click', function(e) {
                if (e.target === this) {
                    UI.hideModal(this.id);
                }
            });
        });
        
        // Close modals with Escape key
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') {
                document.querySelectorAll('.modal.active').forEach(function(modal) {
                    UI.hideModal(modal.id);
                });
            }
        });
    },
    
    // Sync data
    syncData: function() {
        const btn = document.getElementById('syncBtn');
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
        btn.disabled = true;
        
        setTimeout(function() {
            Materials.loadMaterials(Materials.currentSection);
            PriceManager.loadPrices();
            
            btn.innerHTML = '<i class="fas fa-check"></i>';
            setTimeout(function() {
                btn.innerHTML = '<i class="fas fa-sync"></i>';
                btn.disabled = false;
            }, 1000);
            
            UI.showNotification('تمت المزامنة بنجاح', 'success');
        }, 500);
    },
    
    // Toggle theme
    toggleTheme: function() {
        const html = document.documentElement;
        const currentTheme = html.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        
        html.setAttribute('data-theme', newTheme);
        localStorage.setItem('theme', newTheme);
        
        const icon = document.querySelector('#themeToggle i');
        if (icon) {
            icon.className = newTheme === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
        }
    },
    
    // Load theme preference
    loadTheme: function() {
        const savedTheme = localStorage.getItem('theme');
        if (savedTheme) {
            document.documentElement.setAttribute('data-theme', savedTheme);
            const icon = document.querySelector('#themeToggle i');
            if (icon) {
                icon.className = savedTheme === 'dark' ? 'fas fa-sun' : 'fas fa-moon';
            }
        }
    },
    
    // Backup data
    backupData: function() {
        const materials = Materials.getAllMaterials();
        const prices = PriceManager.prices;
        
        const data = {
            version: '12.0',
            timestamp: Utils.getTimestamp(),
            materials: materials,
            prices: prices,
            totalMaterials: materials.length
        };
        
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'material_manager_backup_' + new Date().toISOString().split('T')[0] + '.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        UI.showNotification('تم إنشاء النسخة الاحتياطية بنجاح', 'success');
    },
    
    // Restore data
    restoreData: function() {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.json';
        input.onchange = function(e) {
            const file = e.target.files[0];
            if (!file) return;
            
            const reader = new FileReader();
            reader.onload = function(event) {
                try {
                    const data = JSON.parse(event.target.result);
                    Events.processRestore(data);
                } catch (error) {
                    UI.showNotification('ملف غير صالح', 'error');
                }
            };
            reader.readAsText(file);
        };
        input.click();
    },
    
    // Process restore
    processRestore: function(data) {
        if (!data.materials || !Array.isArray(data.materials)) {
            UI.showNotification('بيانات غير صالحة', 'error');
            return;
        }
        
        if (!confirm('سيتم استعادة ' + data.materials.length + ' مادة. هل أنت متأكد؟')) return;
        
        if (!isFirebaseReady()) {
            UI.showNotification('Firebase غير جاهز', 'error');
            return;
        }
        
        const db = getDB();
        if (!db) return;
        
        // Restore materials
        const batch = db.batch();
        data.materials.forEach(function(material) {
            const ref = db.collection(COLLECTION).doc();
            batch.set(ref, {
                name: material.name,
                quantity: material.quantity || 0,
                unit: material.unit || 'كغ',
                section: material.section || 'main',
                timestamp: Utils.getTimestamp()
            });
        });
        
        batch.commit()
            .then(function() {
                UI.showNotification('تم استعادة المواد بنجاح', 'success');
                
                // Restore prices
                if (data.prices) {
                    const priceBatch = db.batch();
                    Object.keys(data.prices).forEach(function(name) {
                        const ref = db.collection(PRICES_COLLECTION).doc(name);
                        priceBatch.set(ref, {
                            price: data.prices[name],
                            updatedAt: Utils.getTimestamp()
                        });
                    });
                    return priceBatch.commit();
                }
            })
            .then(function() {
                UI.showNotification('تم استعادة الأسعار بنجاح', 'success');
                Materials.loadMaterials(Materials.currentSection);
                PriceManager.loadPrices();
            })
            .catch(function(error) {
                console.error('Restore error:', error);
                UI.showNotification('حدث خطأ أثناء الاستعادة', 'error');
            });
    }
};

// Make Events globally accessible
window.Events = Events;
