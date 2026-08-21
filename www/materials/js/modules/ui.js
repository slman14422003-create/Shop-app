// =========================================
// UI Module - مع دعم التمرير السلس
// =========================================

const UI = {
    // ... الكود السابق ...
    
    // التمرير إلى عنصر معين بسلاسة
    scrollToElement: function(elementId, offset) {
        const element = document.getElementById(elementId);
        if (!element) return;
        
        const rect = element.getBoundingClientRect();
        const elementTop = rect.top + window.scrollY;
        const targetScroll = elementTop - (offset || 80);
        const currentScroll = window.scrollY;
        const distance = targetScroll - currentScroll;
        
        if (Math.abs(distance) < 10) return;
        
        const duration = 400;
        const startTime = Date.now();
        
        const smoothScroll = function() {
            const elapsed = Date.now() - startTime;
            const progress = Math.min(elapsed / duration, 1);
            // دالة easeInOutCubic
            const ease = progress < 0.5 
                ? 4 * progress * progress * progress 
                : 1 - Math.pow(-2 * progress + 2, 3) / 2;
            
            window.scrollTo(0, currentScroll + distance * ease);
            
            if (progress < 1) {
                requestAnimationFrame(smoothScroll);
            }
        };
        
        smoothScroll();
    },
    
    // عرض إشعار مع أيقونة
    showNotification: function(message, type, duration) {
        const existing = document.querySelector('.notification');
        if (existing) {
            existing.remove();
        }
        
        const notification = document.createElement('div');
        notification.className = 'notification ' + (type || 'info');
        
        // إضافة أيقونة حسب النوع
        const icons = {
            success: '✅',
            error: '❌',
            warning: '⚠️',
            info: 'ℹ️'
        };
        
        notification.innerHTML = (icons[type] || 'ℹ️') + ' ' + message;
        notification.style.cssText = 
            'position:fixed;bottom:80px;left:50%;transform:translateX(-50%);' +
            'padding:14px 28px;border-radius:12px;background:var(--bg-card);' +
            'color:var(--text-primary);box-shadow:0 10px 40px rgba(0,0,0,0.2);' +
            'z-index:9999;font-weight:500;max-width:90%;' +
            'border-right:4px solid ' + (type === 'success' ? '#22c55e' : 
                                         type === 'error' ? '#ef4444' : 
                                         type === 'warning' ? '#f59e0b' : '#3b82f6') +
            ';font-family:Tajawal,sans-serif;' +
            'animation:slideUp 0.3s ease;' +
            'backdrop-filter:blur(10px);' +
            'background:var(--bg-secondary);';
        
        document.body.appendChild(notification);
        
        setTimeout(function() {
            notification.style.opacity = '0';
            notification.style.transform = 'translateX(-50%) translateY(20px)';
            notification.style.transition = 'all 0.3s ease';
            setTimeout(function() {
                notification.remove();
            }, 300);
        }, duration || 3000);
    }
};

// Make UI globally accessible
window.UI = UI;
