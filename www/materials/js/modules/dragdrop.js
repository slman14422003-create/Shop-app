// =========================================
// Drag & Drop Module - نسخة محسنة للجوالات
// =========================================

const DragDrop = {
    // حالة السحب
    draggedId: null,
    draggedElement: null,
    isDragging: false,
    dragStartX: 0,
    dragStartY: 0,
    currentTarget: null,
    clonedElement: null,
    longPressTimer: null,
    isLongPress: false,
    scrollInterval: null,
    originalScrollY: 0,
    
    // تهيئة السحب والإفلات
    init: function() {
        // التحقق من دعم السحب الحديث
        const isModernDragDrop = 'draggable' in document.createElement('div');
        const isTouchDevice = 'ontouchstart' in window || navigator.maxTouchPoints > 0;
        
        if (isModernDragDrop && !isTouchDevice) {
            this.initModernDragDrop();
        } else {
            this.initTouchDragDrop();
        }
        
        // إعداد مناطق الإسقاط
        this.setupDropZones();
        
        // إضافة دعم النقر المزدوج للتحديد
        this.setupDoubleTapSupport();
    },
    
    // تهيئة السحب باللمس (للجوالات)
    initTouchDragDrop: function() {
        let startX = 0;
        let startY = 0;
        let isTouching = false;
        let touchElement = null;
        
        // بدء اللمس
        document.addEventListener('touchstart', function(e) {
            const card = e.target.closest('.material-card');
            if (!card) return;
            
            // منع التمرير أثناء السحب
            if (DragDrop.isDragging) {
                e.preventDefault();
                return;
            }
            
            const touch = e.touches[0];
            startX = touch.clientX;
            startY = touch.clientY;
            isTouching = true;
            touchElement = card;
            DragDrop.isLongPress = false;
            
            // إضافة تأثير بسيط عند اللمس
            card.style.transform = 'scale(0.97)';
            card.style.transition = 'transform 0.1s';
            
            // بدء مؤقت الضغط الطويل (300ms)
            clearTimeout(DragDrop.longPressTimer);
            DragDrop.longPressTimer = setTimeout(function() {
                if (isTouching && touchElement) {
                    DragDrop.isLongPress = true;
                    // اهتزاز للتأكيد
                    if (navigator.vibrate) {
                        navigator.vibrate(20);
                    }
                    DragDrop.startTouchDrag(touchElement, startX, startY);
                    
                    // منع التمرير
                    document.body.style.overflow = 'hidden';
                }
            }, 300);
        }, { passive: true });
        
        // تحريك الإصبع
        document.addEventListener('touchmove', function(e) {
            if (!isTouching || !touchElement) return;
            
            const touch = e.touches[0];
            const deltaX = Math.abs(touch.clientX - startX);
            const deltaY = Math.abs(touch.clientY - startY);
            
            // إذا تحرك الإصبع كثيراً، إلغاء الضغط الطويل
            if (deltaX > 15 || deltaY > 15) {
                clearTimeout(DragDrop.longPressTimer);
                
                if (DragDrop.isDragging) {
                    // منع التمرير أثناء السحب
                    e.preventDefault();
                    DragDrop.moveTouchDrag(touch.clientX, touch.clientY);
                    
                    // تمرير الشاشة تلقائياً عند الوصول للحواف
                    DragDrop.autoScroll(touch.clientY);
                } else {
                    // إلغاء تأثير الضغط
                    if (touchElement) {
                        touchElement.style.transform = 'scale(1)';
                    }
                }
            }
        }, { passive: false });
        
        // إنهاء اللمس
        document.addEventListener('touchend', function(e) {
            const touch = e.changedTouches[0];
            
            if (DragDrop.isDragging) {
                // إنهاء السحب
                DragDrop.endTouchDrag(touch.clientX, touch.clientY);
                document.body.style.overflow = '';
                
                // إيقاف التمرير التلقائي
                if (DragDrop.scrollInterval) {
                    clearInterval(DragDrop.scrollInterval);
                    DragDrop.scrollInterval = null;
                }
            } else if (isTouching && touchElement && !DragDrop.isLongPress) {
                // نقر عادي - فتح التعديل
                const card = touchElement;
                const id = card.dataset.id;
                if (id) {
                    Materials.editMaterial(id);
                }
            }
            
            // إعادة تعيين الحالة
            if (touchElement) {
                touchElement.style.transform = 'scale(1)';
                touchElement.style.transition = '';
            }
            
            isTouching = false;
            touchElement = null;
            clearTimeout(DragDrop.longPressTimer);
            
            // إزالة العنصر المنسوخ إذا كان موجوداً
            if (DragDrop.clonedElement) {
                DragDrop.clonedElement.remove();
                DragDrop.clonedElement = null;
            }
        }, { passive: true });
    },
    
    // بدء السحب باللمس
    startTouchDrag: function(card, clientX, clientY) {
        if (!card) return;
        
        const id = card.dataset.id;
        if (!id) return;
        
        this.draggedId = id;
        this.draggedElement = card;
        this.isDragging = true;
        
        // حفظ موقع التمرير الأصلي
        this.originalScrollY = window.scrollY;
        
        // إنشاء نسخة متحركة
        const rect = card.getBoundingClientRect();
        this.clonedElement = card.cloneNode(true);
        this.clonedElement.style.position = 'fixed';
        this.clonedElement.style.pointerEvents = 'none';
        this.clonedElement.style.zIndex = '9999';
        this.clonedElement.style.opacity = '0.9';
        this.clonedElement.style.transform = 'scale(1.08) rotate(-2deg)';
        this.clonedElement.style.width = rect.width + 'px';
        this.clonedElement.style.left = (clientX - rect.width / 2) + 'px';
        this.clonedElement.style.top = (clientY - rect.height / 2) + 'px';
        this.clonedElement.style.boxShadow = '0 20px 60px rgba(0,0,0,0.4)';
        this.clonedElement.style.borderRadius = '12px';
        this.clonedElement.style.transition = 'transform 0.2s ease';
        
        document.body.appendChild(this.clonedElement);
        
        // إخفاء العنصر الأصلي
        card.style.opacity = '0.2';
        card.style.transform = 'scale(0.95)';
        card.style.transition = 'all 0.3s ease';
        
        // رفع الشاشة قليلاً لإظهار الجدول
        this.scrollToRevealTable();
        
        // عرض رسالة مساعدة
        this.showDragHelper();
    },
    
    // تحريك السحب باللمس
    moveTouchDrag: function(clientX, clientY) {
        if (!this.clonedElement || !this.isDragging) return;
        
        // تحديث موقع العنصر المنسوخ
        const rect = this.clonedElement.getBoundingClientRect();
        this.clonedElement.style.left = (clientX - rect.width / 2) + 'px';
        this.clonedElement.style.top = (clientY - rect.height / 2) + 'px';
        
        // تدوير العنصر قليلاً حسب اتجاه الحركة
        const rotation = Math.min(Math.max((clientX - this.dragStartX) / 10, -5), 5);
        this.clonedElement.style.transform = 'scale(1.08) rotate(' + rotation + 'deg)';
        
        // البحث عن منطقة إسقاط تحت الإصبع
        const elementAtPoint = document.elementFromPoint(clientX, clientY);
        if (elementAtPoint) {
            // البحث عن قسم
            let sectionCard = elementAtPoint.closest('.section-card');
            
            // إذا لم يكن هناك قسم، ابحث في حاوية المواد
            if (!sectionCard) {
                const materialsContainer = elementAtPoint.closest('#materialsContainer');
                if (materialsContainer) {
                    // استخدام القسم الحالي
                    sectionCard = document.querySelector('.section-card.active');
                }
            }
            
            // إزالة الإبراز من جميع الأقسام
            document.querySelectorAll('.section-card').forEach(function(el) {
                el.classList.remove('drop-target');
                el.style.transform = 'scale(1)';
                el.style.boxShadow = 'none';
            });
            
            if (sectionCard) {
                sectionCard.classList.add('drop-target');
                sectionCard.style.transform = 'scale(1.08)';
                sectionCard.style.boxShadow = '0 0 30px rgba(59,130,246,0.4)';
                this.currentTarget = sectionCard;
                
                // إظهار اسم القسم المستهدف
                const sectionName = sectionCard.querySelector('span')?.textContent || '';
                this.showDropTarget(sectionName);
            } else {
                this.currentTarget = null;
                this.hideDropTarget();
            }
        }
    },
    
    // إنهاء السحب باللمس
    endTouchDrag: function(clientX, clientY) {
        this.isDragging = false;
        
        // إزالة العنصر المنسوخ
        if (this.clonedElement) {
            this.clonedElement.remove();
            this.clonedElement = null;
        }
        
        // إعادة العنصر الأصلي
        if (this.draggedElement) {
            this.draggedElement.style.opacity = '1';
            this.draggedElement.style.transform = 'scale(1)';
            this.draggedElement.style.transition = 'all 0.3s ease';
        }
        
        // إزالة الإبراز
        document.querySelectorAll('.section-card').forEach(function(el) {
            el.classList.remove('drop-target');
            el.style.transform = 'scale(1)';
            el.style.boxShadow = 'none';
        });
        
        // إخفاء المساعدات
        this.hideDragHelper();
        this.hideDropTarget();
        
        // إعادة التمرير إلى الموضع الأصلي
        this.restoreScrollPosition();
        
        // تنفيذ الإسقاط
        if (this.currentTarget && this.draggedId) {
            const section = this.currentTarget.dataset.section;
            if (section) {
                this.moveMaterial(this.draggedId, section);
            }
        }
        
        // إعادة تعيين الحالة
        this.currentTarget = null;
        this.draggedId = null;
        this.draggedElement = null;
        document.body.style.overflow = '';
        
        // إيقاف التمرير التلقائي
        if (this.scrollInterval) {
            clearInterval(this.scrollInterval);
            this.scrollInterval = null;
        }
    },
    
    // التمرير التلقائي عند الوصول للحواف
    autoScroll: function(clientY) {
        const windowHeight = window.innerHeight;
        const scrollThreshold = 60;
        
        // إيقاف التمرير السابق
        if (this.scrollInterval) {
            clearInterval(this.scrollInterval);
            this.scrollInterval = null;
        }
        
        // التحقق من الوصول للحافة
        if (clientY < scrollThreshold) {
            // تمرير لأعلى
            this.scrollInterval = setInterval(function() {
                window.scrollBy(0, -10);
            }, 16);
        } else if (clientY > windowHeight - scrollThreshold) {
            // تمرير لأسفل
            this.scrollInterval = setInterval(function() {
                window.scrollBy(0, 10);
            }, 16);
        }
    },
    
    // رفع الشاشة لإظهار الجدول
    scrollToRevealTable: function() {
        // البحث عن الجدول
        const table = document.getElementById('materialsContainer');
        if (!table) return;
        
        // الحصول على موقع الجدول
        const rect = table.getBoundingClientRect();
        const tableTop = rect.top + window.scrollY;
        const windowHeight = window.innerHeight;
        
        // إذا كان الجدول أسفل الشاشة، تمرير إليه
        if (rect.top > windowHeight * 0.6) {
            // تمرير سلس
            const targetScroll = tableTop - 100;
            const currentScroll = window.scrollY;
            const distance = targetScroll - currentScroll;
            const duration = 300;
            const startTime = Date.now();
            
            const smoothScroll = function() {
                const elapsed = Date.now() - startTime;
                const progress = Math.min(elapsed / duration, 1);
                // دالة easing
                const ease = 1 - Math.pow(1 - progress, 3);
                window.scrollTo(0, currentScroll + distance * ease);
                
                if (progress < 1) {
                    requestAnimationFrame(smoothScroll);
                }
            };
            
            smoothScroll();
        }
        
        // إضافة مؤشر بصري لإظهار مناطق الإسقاط
        document.querySelectorAll('.section-card').forEach(function(el) {
            el.style.transition = 'all 0.3s ease';
            el.style.borderWidth = '2px';
            el.style.borderStyle = 'dashed';
            el.style.borderColor = 'var(--primary)';
        });
        
        // إزالة المؤشر بعد 3 ثواني
        setTimeout(function() {
            document.querySelectorAll('.section-card').forEach(function(el) {
                if (!el.classList.contains('drop-target')) {
                    el.style.borderStyle = 'solid';
                    el.style.borderColor = 'var(--border-color)';
                }
            });
        }, 3000);
    },
    
    // استعادة موقع التمرير
    restoreScrollPosition: function() {
        // إعادة التمرير إلى الموضع الأصلي إذا كان المستخدم في الأسفل
        if (this.originalScrollY !== undefined) {
            // تمرير سلس
            const targetScroll = this.originalScrollY;
            const currentScroll = window.scrollY;
            const distance = targetScroll - currentScroll;
            
            if (Math.abs(distance) > 50) {
                const duration = 200;
                const startTime = Date.now();
                
                const smoothScroll = function() {
                    const elapsed = Date.now() - startTime;
                    const progress = Math.min(elapsed / duration, 1);
                    const ease = 1 - Math.pow(1 - progress, 3);
                    window.scrollTo(0, currentScroll + distance * ease);
                    
                    if (progress < 1) {
                        requestAnimationFrame(smoothScroll);
                    }
                };
                
                smoothScroll();
            }
        }
        
        // إزالة المؤشرات البصرية
        document.querySelectorAll('.section-card').forEach(function(el) {
            el.style.borderStyle = 'solid';
            el.style.borderColor = 'var(--border-color)';
            el.style.transition = '';
        });
    },
    
    // عرض مساعد السحب
    showDragHelper: function() {
        // إزالة المساعد القديم
        this.hideDragHelper();
        
        const helper = document.createElement('div');
        helper.id = 'dragHelper';
        helper.innerHTML = '<i class="fas fa-arrow-up"></i> اسحب إلى القسم المطلوب <i class="fas fa-arrow-down"></i>';
        helper.style.cssText = 
            'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);' +
            'background:rgba(0,0,0,0.8);color:white;padding:12px 24px;' +
            'border-radius:12px;z-index:10000;font-size:0.9rem;' +
            'font-family:Tajawal,sans-serif;text-align:center;' +
            'animation:fadeInUp 0.3s ease;box-shadow:0 10px 30px rgba(0,0,0,0.3);' +
            'pointer-events:none;max-width:90%;';
        
        document.body.appendChild(helper);
        
        // إضافة تأثير وميض
        setTimeout(function() {
            if (helper) {
                helper.style.opacity = '0.7';
                setTimeout(function() {
                    if (helper) helper.style.opacity = '1';
                }, 500);
            }
        }, 1000);
    },
    
    // إخفاء مساعد السحب
    hideDragHelper: function() {
        const helper = document.getElementById('dragHelper');
        if (helper) {
            helper.remove();
        }
    },
    
    // عرض هدف الإسقاط
    showDropTarget: function(sectionName) {
        let target = document.getElementById('dropTarget');
        if (!target) {
            target = document.createElement('div');
            target.id = 'dropTarget';
            target.style.cssText = 
                'position:fixed;top:50%;left:50%;transform:translate(-50%,-50%);' +
                'background:rgba(59,130,246,0.9);color:white;padding:16px 32px;' +
                'border-radius:16px;z-index:9998;font-size:1.2rem;' +
                'font-weight:600;font-family:Tajawal,sans-serif;' +
                'box-shadow:0 20px 60px rgba(0,0,0,0.3);' +
                'pointer-events:none;animation:scaleIn 0.2s ease;' +
                'backdrop-filter:blur(10px);';
            document.body.appendChild(target);
        }
        target.textContent = '📦 ' + sectionName;
        target.style.display = 'block';
    },
    
    // إخفاء هدف الإسقاط
    hideDropTarget: function() {
        const target = document.getElementById('dropTarget');
        if (target) {
            target.style.display = 'none';
        }
    },
    
    // إعداد دعم النقر المزدوج للتحديد
    setupDoubleTapSupport: function() {
        let lastTap = 0;
        let lastTapTarget = null;
        
        document.addEventListener('touchend', function(e) {
            const card = e.target.closest('.material-card');
            if (!card) return;
            
            const now = Date.now();
            const timeDiff = now - lastTap;
            
            if (timeDiff < 300 && lastTapTarget === card) {
                // نقر مزدوج - فتح التعديل
                const id = card.dataset.id;
                if (id) {
                    Materials.editMaterial(id);
                }
                e.preventDefault();
            }
            
            lastTap = now;
            lastTapTarget = card;
        }, { passive: true });
    },
    
    // تهيئة السحب الحديث (لأجهزة الكمبيوتر)
    initModernDragDrop: function() {
        document.addEventListener('dragstart', function(e) {
            const card = e.target.closest('.material-card');
            if (!card) return;
            
            const id = card.dataset.id;
            if (!id) return;
            
            DragDrop.draggedId = id;
            DragDrop.draggedElement = card;
            card.classList.add('dragging');
            
            e.dataTransfer.effectAllowed = 'move';
            e.dataTransfer.setData('text/plain', id);
            
            try {
                e.dataTransfer.setDragImage(card, 20, 20);
            } catch (err) {}
        });
        
        document.addEventListener('dragend', function(e) {
            const card = e.target.closest('.material-card');
            if (card) {
                card.classList.remove('dragging');
            }
            DragDrop.draggedId = null;
            DragDrop.draggedElement = null;
        });
    },
    
    // إعداد مناطق الإسقاط
    setupDropZones: function() {
        // إعداد الأقسام كمناطق إسقاط
        document.querySelectorAll('.section-card').forEach(function(el) {
            // للأجهزة الحديثة
            el.addEventListener('dragover', function(e) {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                this.classList.add('drop-target');
            });
            
            el.addEventListener('dragleave', function(e) {
                this.classList.remove('drop-target');
            });
            
            el.addEventListener('drop', function(e) {
                e.preventDefault();
                this.classList.remove('drop-target');
                
                const draggedId = e.dataTransfer.getData('text/plain');
                if (draggedId) {
                    const targetSection = this.dataset.section;
                    DragDrop.moveMaterial(draggedId, targetSection);
                }
            });
        });
        
        // إعداد حاوية المواد
        const container = document.getElementById('materialsContainer');
        if (container) {
            container.addEventListener('dragover', function(e) {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
            });
            
            container.addEventListener('drop', function(e) {
                e.preventDefault();
                const draggedId = e.dataTransfer.getData('text/plain');
                if (draggedId) {
                    DragDrop.moveMaterial(draggedId, Materials.currentSection);
                }
            });
        }
    },
    
    // نقل المادة
    moveMaterial: function(id, targetSection) {
        if (!isFirebaseReady()) {
            UI.showNotification('Firebase غير جاهز', 'error');
            return;
        }
        
        const db = getDB();
        if (!db) return;
        
        const material = Materials.allMaterials.find(function(m) {
            return m.id === id;
        });
        
        if (!material) {
            UI.showNotification('المادة غير موجودة', 'error');
            return;
        }
        
        if (material.section === targetSection) {
            UI.showNotification('المادة موجودة بالفعل في هذا القسم', 'info');
            return;
        }
        
        // الحصول على اسم القسم
        const sectionName = UI.sections.find(function(s) {
            return s.id === targetSection;
        })?.title || targetSection;
        
        db.collection(COLLECTION).doc(id).update({
            section: targetSection,
            timestamp: Utils.getTimestamp()
        })
        .then(function() {
            UI.showNotification('✅ تم نقل المادة إلى ' + sectionName, 'success');
            
            if (navigator.vibrate) {
                navigator.vibrate([30, 50, 30]);
            }
        })
        .catch(function(error) {
            console.error('Move material error:', error);
            UI.showNotification('❌ حدث خطأ أثناء نقل المادة', 'error');
        });
    }
};

// إضافة الأنماط الديناميكية للرسوم المتحركة
const styleSheet = document.createElement('style');
styleSheet.textContent = `
    @keyframes fadeInUp {
        from {
            opacity: 0;
            transform: translateX(-50%) translateY(20px);
        }
        to {
            opacity: 1;
            transform: translateX(-50%) translateY(0);
        }
    }
    
    @keyframes scaleIn {
        from {
            opacity: 0;
            transform: translate(-50%, -50%) scale(0.8);
        }
        to {
            opacity: 1;
            transform: translate(-50%, -50%) scale(1);
        }
    }
    
    .section-card.drop-target {
        border-color: var(--primary) !important;
        background: var(--primary) !important;
        color: white !important;
        transform: scale(1.08) !important;
        box-shadow: 0 0 30px rgba(59,130,246,0.4) !important;
        transition: all 0.3s ease !important;
    }
    
    .material-card.dragging {
        opacity: 0.3;
        transform: scale(0.95);
    }
    
    /* إضافة مساحة للتمرير */
    #materialsContainer {
        scroll-margin-top: 100px;
    }
`;

document.head.appendChild(styleSheet);

// Make DragDrop globally accessible
window.DragDrop = DragDrop;
