// =========================================
// AI Engine - Material Analysis
// =========================================

const AIEngine = {
    // Convert quantity to kilograms
    convertToKg: function(quantity, unit) {
        if (!quantity) return 0;
        const num = parseFloat(quantity);
        if (isNaN(num)) return 0;
        
        switch(unit) {
            case 'كغ': return num;
            case 'غ': return num / 1000;
            case 'كيس': return num * 0.5; // Assume 0.5kg per bag
            case 'حبة': return num * 0.05; // Assume 0.05kg per piece
            default: return num;
        }
    },
    
    // Format number
    formatNumber: function(value) {
        if (value === undefined || value === null || isNaN(value)) return '0';
        if (value >= 1000) {
            return value.toFixed(1) + 'K';
        }
        return value.toFixed(1);
    },
    
    // Format currency
    formatCurrency: function(value) {
        if (value === undefined || value === null || isNaN(value)) return '0 ل.س';
        return Math.round(value).toLocaleString() + ' ل.س';
    },
    
    // Analyze inventory
    analyzeInventory: function(materials, getPriceFunction) {
        if (!materials || !Array.isArray(materials) || materials.length === 0) {
            return this.getEmptyAnalysis();
        }
        
        let totalWeight = 0;
        let totalValueBeforeDiscount = 0;
        let pricedCount = 0;
        let lowStockList = [];
        const priceBreakdown = [];
        
        materials.forEach(function(material) {
            const kg = AIEngine.convertToKg(material.quantity, material.unit);
            totalWeight += kg;
            
            const price = getPriceFunction(material.name);
            if (price && price > 0) {
                const itemValue = kg * price;
                totalValueBeforeDiscount += itemValue;
                pricedCount++;
                priceBreakdown.push({
                    name: material.name,
                    quantity: kg,
                    price: price,
                    value: itemValue
                });
            }
            
            // Check low stock (less than 0.5kg)
            if (kg < 0.5 && kg > 0) {
                lowStockList.push(material.name);
            }
        });
        
        // Apply 35% discount
        const discountPercent = 35;
        const totalValue = totalValueBeforeDiscount * (1 - discountPercent / 100);
        
        // Generate insights
        const insights = this.generateInsights(materials.length, lowStockList.length, totalWeight);
        
        return {
            totalWeight: totalWeight,
            totalWeightFormatted: this.formatNumber(totalWeight) + ' كغ',
            totalValue: totalValue,
            totalValueFormatted: this.formatCurrency(totalValue),
            totalValueBeforeDiscount: totalValueBeforeDiscount,
            totalValueBeforeDiscountFormatted: this.formatCurrency(totalValueBeforeDiscount),
            discountPercent: discountPercent,
            pricedCount: pricedCount,
            totalMaterials: materials.length,
            lowStockCount: lowStockList.length,
            lowStockList: lowStockList,
            priceBreakdown: priceBreakdown,
            insights: insights
        };
    },
    
    // Get empty analysis
    getEmptyAnalysis: function() {
        return {
            totalWeight: 0,
            totalWeightFormatted: '0 كغ',
            totalValue: 0,
            totalValueFormatted: '0 ل.س',
            totalValueBeforeDiscount: 0,
            totalValueBeforeDiscountFormatted: '0 ل.س',
            discountPercent: 35,
            pricedCount: 0,
            totalMaterials: 0,
            lowStockCount: 0,
            lowStockList: [],
            priceBreakdown: [],
            insights: ['لا توجد مواد في المخزون']
        };
    },
    
    // Generate insights
    generateInsights: function(totalMaterials, lowStockCount, totalWeight) {
        const insights = [];
        
        if (totalMaterials === 0) {
            insights.push('📦 المخزون فارغ، ابدأ بإضافة المواد');
            return insights;
        }
        
        if (lowStockCount > 0) {
            insights.push('⚠️ هناك ' + lowStockCount + ' مواد ناقصة تحتاج إلى تجديد');
        }
        
        if (totalWeight > 10) {
            insights.push('📊 المخزون جيد، الوزن الكلي ' + this.formatNumber(totalWeight) + ' كغ');
        } else if (totalWeight > 5) {
            insights.push('📊 المخزون متوسط، الوزن الكلي ' + this.formatNumber(totalWeight) + ' كغ');
        } else {
            insights.push('📊 المخزون منخفض، الوزن الكلي ' + this.formatNumber(totalWeight) + ' كغ');
        }
        
        if (totalMaterials > 20) {
            insights.push('🏷️ لديك ' + totalMaterials + ' مادة، تنوع جيد');
        } else if (totalMaterials > 10) {
            insights.push('🏷️ لديك ' + totalMaterials + ' مادة، يمكنك إضافة المزيد');
        }
        
        insights.push('💡 قيمة المخزون بعد الخصم 35% محسوبة تلقائياً');
        
        return insights;
    }
};
