package com.salman.herbalencyclopedia.ui.screens.blends

import com.salman.herbalencyclopedia.ui.theme.staggeredEntrance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.ui.components.BlendCard
import com.salman.herbalencyclopedia.ui.components.EmptyView
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.TopBarBrandTitle
import com.salman.herbalencyclopedia.ui.util.tr

/**
 * شاشة "الخلطات": تعرض كل خلطات الأعشاب لكل المستخدمين (قراءة فقط)، وتضيف
 * زر إنشاء خلطة جديدة للأدمن فقط — نفس نموذج صلاحيات الأعشاب تماماً
 * (قراءة عامة، كتابة بحساب الأدمن الوحيد فقط، عبر Firestore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlendsScreen(
    blends: List<Blend>,
    isAdmin: Boolean,
    onBack: () -> Unit,
    onBlendClick: (Blend) -> Unit,
    onAddNew: () -> Unit
) {
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                large = true,
                scrollBehavior = topBarScroll,
                title = {
                    TopBarBrandTitle(
                        icon = Icons.Filled.Blender,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = tr("الخلطات"),
                        subtitle = "${blends.size} خلطة من الأعشاب"
                    )
                },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = onAddNew,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(tr("إضافة خلطة")) }
                )
            }
        }
    ) { padding ->
        if (blends.isEmpty()) {
            EmptyView(
                message = if (isAdmin) tr("أضف أول خلطة لتظهر هنا للجميع") else tr("ستظهر هنا الخلطات العشبية فور إضافتها"),
                modifier = Modifier.padding(padding).fillMaxSize(),
                icon = Icons.Filled.Blender,
                title = tr("لا توجد خلطات بعد"),
                actionLabel = if (isAdmin) tr("إضافة خلطة") else null,
                onAction = if (isAdmin) onAddNew else null,
                accent = MaterialTheme.colorScheme.tertiary
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(blends, key = { _, b -> b.id }) { index, blend ->
                    BlendCard(
                        blend = blend,
                        ingredientCount = blend.herbIds.size,
                        onClick = { onBlendClick(blend) },
                        modifier = Modifier.staggeredEntrance(index)
                    )
                }
            }
        }
    }
}
