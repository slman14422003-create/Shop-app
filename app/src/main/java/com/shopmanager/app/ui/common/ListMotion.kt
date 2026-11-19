package com.shopmanager.app.ui.common

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shopmanager.app.data.performance.LocalPerformanceTier
import com.shopmanager.app.data.performance.MotionSpecs
import com.shopmanager.app.data.performance.PerformanceTier

/**
 * حركة عناصر القوائم (إدراج/ترتيب/حذف) في المستوى HIGH فقط؛ في LOW يُعاد Modifier فارغ فلا
 * يُنشأ أي تتبّع لمواضع العناصر.
 */
@Composable
fun LazyItemScope.tierAnimateItem(): Modifier =
    if (LocalPerformanceTier.current == PerformanceTier.HIGH) {
        Modifier.animateItem(
            fadeInSpec = MotionSpecs.contentTween(),
            placementSpec = MotionSpecs.reorderSpring(),
            fadeOutSpec = MotionSpecs.listItemFadeOut()
        )
    } else Modifier
