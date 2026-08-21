// =========================================
// Price Management Module - مع إصلاح حساب الأسعار
// =========================================

const PriceManager = {
    // Prices cache
    prices: {},
    priceListener: null,
    isPriceModalOpen: false,
    
    // Load prices from Firebase
    loadPrices: function() {
        // First load from local
        this.loadFromLocal();
        
        // Then sync with Firebase
        if (!isFirebaseReady()) {
            console.warn('Firebase not ready for prices');
            this.updateAnalysisAfterLoad();
            return;
        }
        
        const db = getDB();
        if (!db) return;
        
        // Detach previous listener
        if (this.priceListener) {
            this.priceListener();
            this.priceListener = null;
        }
        
        this.priceListener = db.collection(PRICES_COLLECTION)
            .onSnapshot(function(snapshot) {
                const prices = {};
                snapshot.forEach(function(doc) {
                    const data = doc.data();
                    prices[doc.id] = data.price || 0;
                });
                
                PriceManager.prices = prices;
                PriceManager.saveToLocal(prices);
                
                // تحديث التحليل الذكي فوراً
                PriceManager.updateAnalysisAfterLoad();
                
                // تحديث واجهة الأسعار إذا كانت مفتوحة
                if (PriceManager.isPriceModalOpen) {
                    PriceManager.renderPriceList();
                    PriceManager.updatePriceStats();
                }
            }, function(error) {
                console.error('Price listener error:', error);
                PriceManager.updateAnalysisAfterLoad();
            });
    },
    
    // تحديث التحليل بعد تحميل الأسعار
    updateAnalysisAfterLoad: function() {
        // التأكد من تحديث التحليل الذكي
        if (typeof Materials !== 'undefined' && Materials.allMaterials) {
            const analysis = AIEngine.analyzeInventory(
                Materials.allMaterials,
                this.getPrice.bind(this)
            );
            
            if (typeof AIAssistant !== 'undefined') {
                AIAssistant.updateStats(analysis);
            }
        }
    },
    
    // Load from local storage
    loadFromLocal: function() {
        try {
            const saved = localStorage.getItem('material_prices');
            if (saved) {
                this.prices = JSON.parse(saved) || {};
                console.log('Loaded prices from local:', Object.keys(this.prices).length);
            }
        } catch (e) {
            console.warn('Load prices from local error:', e);
            this.prices = {};
        }
    },
    
    // Save to local storage
    saveToLocal: function(prices) {
        try {
            localStorage.setItem('material_prices', JSON.stringify(prices || this.prices));
        } catch (e) {
            console.warn('Save prices to local error:', e);
        }
    },
    
    // Get price for material - مع تحسين الأداء
    getPrice: function(name) {
        if (!name) return 0;
        const price = this.prices[name] || 0;
        return typeof price === 'number' ? price : 0;
    },
    
    // الحصول على سعر المادة مع معلومات إضافية
    getPriceWithInfo: function(name) {
        if (!name) return { price: 0, exists: false };
        const price = this.prices[name] || 0;
        return {
            price: price,
            exists: typeof this.prices[name] === 'number' && this.prices[name] > 0
        };
    },
    
    // Update price for material
    updatePrice: function(name, price) {
        if (!name) return;
        
        const priceNum = parseFloat(price);
        if (isNaN(priceNum) || priceNum < 0) {
            UI.showNotification('سعر غير صحيح', 'error');
            return false;
        }
        
        // تحديث السعر
        if (priceNum === 0) {
            delete this.prices[name];
        } else {
            this.prices[name] = priceNum;
        }
        
        this.saveToLocal();
        
        // Sync to Firebase
        if (isFirebaseReady()) {
            const db = getDB();
            if (db) {
                db.collection(PRICES_COLLECTION).doc(name).set({
                    price: priceNum,
                    updatedAt: Utils.getTimestamp()
                })
                .catch(function(error) {
                    console.error('Save price error:', error);
                });
            }
        }
        
        // تحديث التحليل الذكي فوراً
        this.updateAnalysisAfterLoad();
        
        // تحديث واجهة المستخدم
        if (this.isPriceModalOpen) {
            this.renderPriceList();
            this.updatePriceStats();
        }
        
        return true;
    },
    
    // تحديث جميع الأسعار دفعة واحدة
    updateMultiplePrices: function(priceData) {
        if (!priceData || typeof priceData !== 'object') return;
        
        let updated = 0;
        const db = isFirebaseReady() ? getDB() : null;
        const batch = db ? db.batch() : null;
        
        Object.keys(priceData).forEach(function(name) {
            const price = parseFloat(priceData[name]);
            if (!isNaN(price) && price >= 0) {
                if (price === 0) {
                    delete PriceManager.prices[name];
                } else {
                    PriceManager.prices[name] = price;
                }
                updated++;
                
                // إضافة إلى batch إذا كان Firebase متاحاً
                if (db && batch) {
                    const ref = db.collection(PRICES_COLLECTION).doc(name);
                    batch.set(ref, {
                        price: price,
                        updatedAt: Utils.getTimestamp()
                    });
                }
            }
        });
        
        if (updated > 0) {
            this.saveToLocal();
            
            // تنفيذ batch إذا كان متاحاً
            if (batch) {
                batch.commit()
                    .then(function() {
                        console.log('All prices saved to Firebase');
                    })
                    .catch(function(error) {
                        console.error('Batch save error:', error);
                    });
            }
            
            // تحديث التحليل
            this.updateAnalysisAfterLoad();
            UI.showNotification('تم تحديث ' + updated + ' سعر', 'success');
        }
        
        return updated;
    },
    
    // Format currency
    formatCurrency: function(value) {
        if (value === undefined || value === null || isNaN(value)) return '0 ل.س';
        return Math.round(value).toLocaleString() + ' ل.س';
    },
    
    // حساب القيمة الإجمالية للمخزون
    calculateTotalValue: function(materials) {
        if (!materials || !Array.isArray(materials)) return 0;
        
        let totalValue = 0;
        materials.forEach(function(material) {
            const kg = AIEngine.convertToKg(material.quantity, material.unit);
            const price = PriceManager.getPrice(material.name);
            if (price > 0 && kg > 0) {
                totalValue += kg * price;
            }
        });
        
        // تطبيق الخصم 35%
        return totalValue * 0.65;
    },
    
    // Open price modal
    openModal: function() {
        this.isPriceModalOpen = true;
        UI.showModal('priceModal');
        this.renderPriceList();
        this.updatePriceStats();
    },
    
    // Close price modal
    closeModal: function() {
        this.isPriceModalOpen = false;
        UI.hideModal('priceModal');
    },
    
    // Render price list
    renderPriceList: function() {
        const container = document.getElementById('priceList');
        if (!container) return;
        
        const search = document.getElementById('priceSearch');
        const filter = document.getElementById('priceFilter');
        
        const searchTerm = search ? search.value.toLowerCase() : '';
        const filterValue = filter ? filter.value : 'all';
        
        // Get all materials
        const materials = Materials.getAllMaterials() || [];
        
        // Filter materials
        let filtered = materials;
        if (filterValue !== 'all') {
            filtered = filtered.filter(function(m) {
                return m.section === filterValue;
            });
        }
        if (searchTerm) {
            filtered = filtered.filter(function(m) {
                return m.name.toLowerCase().includes(searchTerm);
            });
        }
        
        // ترتيب المواد: المسعرة أولاً
        filtered.sort(function(a, b) {
            const priceA = PriceManager.getPrice(a.name);
            const priceB = PriceManager.getPrice(b.name);
            if (priceA > 0 && priceB === 0) return -1;
            if (priceA === 0 && priceB > 0) return 1;
            return a.name.localeCompare(b.name);
        });
        
        if (filtered.length === 0) {
            container.innerHTML = '<p style="text-align:center;color:var(--text-muted);padding:1rem;">لا توجد مواد</p>';
            return;
        }
        
        container.innerHTML = filtered.map(function(material) {
            const priceInfo = PriceManager.getPriceWithInfo(material.name);
            const price = priceInfo.price;
            const hasPrice = priceInfo.exists;
            
            // حساب القيمة لهذه المادة
            const kg = AIEngine.convertToKg(material.quantity, material.unit);
            const value = hasPrice && price > 0 ? kg * price : 0;
            
            return '<div class="price-item" data-material="' + material.name + '">' +
                '<div class="price-item-info">' +
                '<span class="price-item-name">' + Utils.capitalize(material.name) + '</span>' +
                '<span class="price-item-quantity">' + material.quantity + ' ' + material.unit + '</span>' +
                (value > 0 ? '<span class="price-item-value">' + PriceManager.formatCurrency(value) + '</span>' : '') +
                '</div>' +
                '<div class="price-item-input-group">' +
                '<input type="number" class="price-item-input" ' +
                'value="' + (hasPrice ? price : '') + '" ' +
                'onchange="PriceManager.updatePriceFromInput(\'' + material.name.replace(/'/g, "\\'") + '\', this.value)" ' +
                'placeholder="سعر/كغ" step="0.01" min="0" />' +
                '<span class="price-item-unit">ل.س/كغ</span>' +
                '</div>' +
                '</div>';
        }).join('');
        
        this.updatePriceStats();
    },
    
    // Update price from input
    updatePriceFromInput: function(name, value) {
        const price = parseFloat(value);
        if (isNaN(price) || price < 0) {
            // عرض القيمة القديمة
            const input = document.querySelector('.price-item-input[value="' + value + '"]');
            if (input) {
                const currentPrice = this.getPrice(name);
                input.value = currentPrice || '';
            }
            return;
        }
        this.updatePrice(name, price);
    },
    
    // Update price stats
    updatePriceStats: function() {
        const materials = Materials.getAllMaterials() || [];
        let pricedCount = 0;
        let totalValue = 0;
        let totalValueBeforeDiscount = 0;
        
        materials.forEach(function(m) {
            const kg = AIEngine.convertToKg(m.quantity, m.unit);
            const price = PriceManager.getPrice(m.name);
            if (price > 0) {
                pricedCount++;
                const itemValue = kg * price;
                totalValueBeforeDiscount += itemValue;
            }
        });
        
        // تطبيق الخصم 35%
        totalValue = totalValueBeforeDiscount * 0.65;
        
        const pricedEl = document.getElementById('pricedCount');
        if (pricedEl) pricedEl.textContent = pricedCount + '/' + materials.length;
        
        const totalEl = document.getElementById('priceTotalValue');
        if (totalEl) totalEl.textContent = this.formatCurrency(totalValue);
        
        const beforeDiscountEl = document.getElementById('priceBeforeDiscount');
        if (beforeDiscountEl) beforeDiscountEl.textContent = this.formatCurrency(totalValueBeforeDiscount);
        
        const discountEl = document.getElementById('priceDiscount');
        if (discountEl) discountEl.textContent = '35%';
    },
    
    // Save all prices
    saveAllPrices: function() {
        const priceData = {};
        const inputs = document.querySelectorAll('.price-item-input');
        let updated = 0;
        
        inputs.forEach(function(input) {
            const price = parseFloat(input.value);
            if (!isNaN(price) && price >= 0) {
                const item = input.closest('.price-item');
                const name = item ? item.dataset.material : null;
                if (name) {
                    priceData[name] = price;
                    updated++;
                }
            }
        });
        
        if (updated > 0) {
            this.updateMultiplePrices(priceData);
            this.renderPriceList();
        } else {
            UI.showNotification('لا توجد أسعار جديدة للحفظ', 'info');
        }
    },
    
    // تصدير الأسعار
    exportPrices: function() {
        const data = {
            version: '12.0',
            timestamp: Utils.getTimestamp(),
            prices: this.prices,
            totalPriced: Object.keys(this.prices).length
        };
        
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'prices_backup_' + new Date().toISOString().split('T')[0] + '.json';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        
        UI.showNotification('تم تصدير الأسعار بنجاح', 'success');
    },
    
    // استيراد الأسعار
    importPrices: function() {
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
                    if (data.prices) {
                        const count = PriceManager.updateMultiplePrices(data.prices);
                        UI.showNotification('تم استيراد ' + count + ' سعر', 'success');
                    } else {
                        UI.showNotification('ملف غير صالح', 'error');
                    }
                } catch (error) {
                    UI.showNotification('خطأ في قراءة الملف', 'error');
                }
            };
            reader.readAsText(file);
        };
        input.click();
    }
};

// Make PriceManager globally accessible
window.PriceManager = PriceManager;
