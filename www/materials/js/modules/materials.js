// =========================================
// Materials Module
// =========================================

const Materials = {
    // All materials cache
    allMaterials: [],
    currentSection: 'main',
    listener: null,
    
    // Load materials from Firebase
    loadMaterials: function(section) {
        this.currentSection = section || this.currentSection;
        
        if (!isFirebaseReady()) {
            console.warn('Firebase not ready, loading from cache');
            this.loadFromCache();
            return;
        }
        
        try {
            const db = getDB();
            if (!db) return;
            
            // Detach previous listener
            if (this.listener) {
                this.listener();
                this.listener = null;
            }
            
            // Listen to real-time updates
            this.listener = db.collection(COLLECTION)
                .where('section', '==', this.currentSection)
                .orderBy('name')
                .onSnapshot(function(snapshot) {
                    const materials = [];
                    snapshot.forEach(function(doc) {
                        const data = doc.data();
                        materials.push({
                            id: doc.id,
                            name: data.name || '',
                            quantity: data.quantity || 0,
                            unit: data.unit || 'كغ',
                            section: data.section || 'main',
                            timestamp: data.timestamp || null
                        });
                    });
                    
                    Materials.allMaterials = materials;
                    Materials.saveToCache(materials);
                    UI.renderMaterials(materials);
                    
                    // Update AI analysis
                    Materials.updateAnalysis();
                }, function(error) {
                    console.error('Firestore listener error:', error);
                    Materials.loadFromCache();
                });
        } catch (error) {
            console.error('Error loading materials:', error);
            this.loadFromCache();
        }
    },
    
    // Load from cache
    loadFromCache: function() {
        try {
            const cached = localStorage.getItem('materials_cache');
            if (cached) {
                const data = JSON.parse(cached);
                if (data && data.section === this.currentSection) {
                    this.allMaterials = data.materials || [];
                    UI.renderMaterials(this.allMaterials);
                    this.updateAnalysis();
                    return;
                }
            }
        } catch (e) {
            console.warn('Cache load error:', e);
        }
        
        UI.renderMaterials([]);
        this.updateAnalysis();
    },
    
    // Save to cache
    saveToCache: function(materials) {
        try {
            localStorage.setItem('materials_cache', JSON.stringify({
                materials: materials,
                section: this.currentSection,
                timestamp: Date.now()
            }));
        } catch (e) {
            console.warn('Cache save error:', e);
        }
    },
    
    // Update AI analysis
    updateAnalysis: function() {
        const analysis = AIEngine.analyzeInventory(
            this.allMaterials,
            PriceManager.getPrice.bind(PriceManager)
        );
        
        AIAssistant.updateStats(analysis);
    },
    
    // Add material
    addMaterial: function(name, quantity, unit, section) {
        if (!isFirebaseReady()) {
            UI.showNotification('Firebase غير جاهز، يرجى المحاولة لاحقاً', 'error');
            return;
        }
        
        const db = getDB();
        if (!db) return;
        
        const data = {
            name: name.trim(),
            quantity: parseFloat(quantity) || 0,
            unit: unit || 'كغ',
            section: section || this.currentSection,
            timestamp: Utils.getTimestamp()
        };
        
        db.collection(COLLECTION).add(data)
            .then(function(docRef) {
                UI.showNotification('تمت إضافة المادة بنجاح', 'success');
                // Close modal
                UI.hideModal('addModal');
                // Reset form
                document.getElementById('addForm').reset();
            })
            .catch(function(error) {
                console.error('Add material error:', error);
                UI.showNotification('حدث خطأ أثناء إضافة المادة', 'error');
            });
    },
    
    // Edit material
    editMaterial: function(id) {
        const material = this.allMaterials.find(function(m) {
            return m.id === id;
        });
        
        if (!material) {
            UI.showNotification('المادة غير موجودة', 'error');
            return;
        }
        
        // Fill edit form
        document.getElementById('editId').value = material.id;
        document.getElementById('editName').value = material.name;
        document.getElementById('editQuantity').value = material.quantity;
        document.getElementById('editUnit').value = material.unit;
        document.getElementById('editSection').value = material.section;
        
        UI.showModal('editModal');
    },
    
    // Save edit
    saveEdit: function(id, name, quantity, unit, section) {
        if (!isFirebaseReady()) {
            UI.showNotification('Firebase غير جاهز', 'error');
            return;
        }
        
        const db = getDB();
        if (!db) return;
        
        const data = {
            name: name.trim(),
            quantity: parseFloat(quantity) || 0,
            unit: unit || 'كغ',
            section: section || this.currentSection,
            timestamp: Utils.getTimestamp()
        };
        
        db.collection(COLLECTION).doc(id).update(data)
            .then(function() {
                UI.showNotification('تم تعديل المادة بنجاح', 'success');
                UI.hideModal('editModal');
            })
            .catch(function(error) {
                console.error('Edit material error:', error);
                UI.showNotification('حدث خطأ أثناء التعديل', 'error');
            });
    },
    
    // Delete material
    deleteMaterial: function(id) {
        if (!confirm('هل أنت متأكد من حذف هذه المادة؟')) return;
        
        if (!isFirebaseReady()) {
            UI.showNotification('Firebase غير جاهز', 'error');
            return;
        }
        
        const db = getDB();
        if (!db) return;
        
        db.collection(COLLECTION).doc(id).delete()
            .then(function() {
                UI.showNotification('تم حذف المادة بنجاح', 'success');
            })
            .catch(function(error) {
                console.error('Delete material error:', error);
                UI.showNotification('حدث خطأ أثناء الحذف', 'error');
            });
    },
    
    // Clear all materials
    clearAll: function() {
        if (!confirm('⚠️ هل أنت متأكد من حذف جميع المواد؟ هذا الإجراء لا يمكن التراجع عنه!')) return;
        
        if (!isFirebaseReady()) {
            UI.showNotification('Firebase غير جاهز', 'error');
            return;
        }
        
        const db = getDB();
        if (!db) return;
        
        // Get all materials in current section
        db.collection(COLLECTION)
            .where('section', '==', this.currentSection)
            .get()
            .then(function(snapshot) {
                const batch = db.batch();
                snapshot.forEach(function(doc) {
                    batch.delete(doc.ref);
                });
                return batch.commit();
            })
            .then(function() {
                UI.showNotification('تم حذف جميع المواد بنجاح', 'success');
            })
            .catch(function(error) {
                console.error('Clear all error:', error);
                UI.showNotification('حدث خطأ أثناء الحذف', 'error');
            });
    },
    
    // Get all materials (for backup)
    getAllMaterials: function() {
        return this.allMaterials;
    }
};

// Make Materials globally accessible
window.Materials = Materials;
