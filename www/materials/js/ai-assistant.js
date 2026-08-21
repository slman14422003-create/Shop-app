// =========================================
// AI Assistant - UI Integration
// =========================================

const AIAssistant = {
    // Current analysis data
    analysis: null,
    
    // Update AI stats display
    updateStats: function(analysis) {
        this.analysis = analysis;
        
        // Update total weight
        const weightEl = document.getElementById('totalWeight');
        if (weightEl) weightEl.textContent = analysis.totalWeightFormatted || '0 كغ';
        
        // Update total value
        const valueEl = document.getElementById('totalValue');
        if (valueEl) valueEl.textContent = analysis.totalValueFormatted || '0 ل.س';
        
        // Update low stock count
        const lowEl = document.getElementById('lowStockCount');
        if (lowEl) lowEl.textContent = analysis.lowStockCount || 0;
        
        // Update total materials
        const totalEl = document.getElementById('totalMaterials');
        if (totalEl) totalEl.textContent = analysis.totalMaterials || 0;
        
        // Update insights
        this.updateInsights(analysis.insights);
    },
    
    // Update insights display
    updateInsights: function(insights) {
        const container = document.getElementById('insightsContent');
        if (!container) return;
        
        if (!insights || insights.length === 0) {
            container.innerHTML = '<p class="insight-placeholder">لا توجد رؤى لعرضها</p>';
            return;
        }
        
        container.innerHTML = insights.map(function(insight) {
            return '<div class="insight-item"><i class="fas fa-circle"></i> ' + insight + '</div>';
        }).join('');
    },
    
    // Show low stock warning
    showLowStockWarning: function(lowStockList) {
        if (!lowStockList || lowStockList.length === 0) return;
        
        const container = document.getElementById('insightsContent');
        if (!container) return;
        
        const warning = document.createElement('div');
        warning.className = 'insight-item warning';
        warning.innerHTML = '<i class="fas fa-exclamation-triangle" style="color: #ef4444;"></i> ' +
            'مواد ناقصة: ' + lowStockList.join(', ');
        
        container.prepend(warning);
    }
};
