package com.salman.herbalencyclopedia.ui.screens.favorites

import com.salman.herbalencyclopedia.ui.theme.staggeredEntrance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.LocalBottomBarInset
import com.salman.herbalencyclopedia.ui.components.TopBarBrandTitle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.ui.components.EmptyView
import com.salman.herbalencyclopedia.ui.components.HerbCard
import com.salman.herbalencyclopedia.ui.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoriteHerbs: List<Herb>,
    onHerbClick: (Herb) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onBrowseClick: () -> Unit = {}
) {
    // Bottom-nav root destination — no back arrow, same as HomeScreen.
    // Title now mirrors HomeScreen's "brand badge" style (icon + title +
    // live subtitle) instead of a bare Text() — the plain version looked
    // empty/unfinished next to Home's richer top bar and the floating
    // bottom nav's polish.
    val subtitle = if (favoriteHerbs.isEmpty()) {
        ""
    } else {
        tr("${favoriteHerbs.size} عشبة محفوظة")
    }
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(topBarScroll.nestedScrollConnection),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        // انظر التعليق بنفس المكان في HomeScreen: هذه شاشة جذر أيضاً، وترك
        // الحافة السفلية الافتراضية هنا يُضاعف الفراغ فوق الشريط العائم.
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            GlassTopBar(
                large = true,
                scrollBehavior = topBarScroll,
                title = {
                    TopBarBrandTitle(
                        icon = Icons.Filled.Favorite,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = tr("المفضلة"),
                        subtitle = subtitle
                    )
                }
            )
        }
    ) { padding ->
        if (favoriteHerbs.isEmpty()) {
            EmptyView(
                message = tr("اضغط على القلب في أي عشبة لتجدها هنا بسرعة"),
                modifier = Modifier.padding(padding),
                icon = Icons.Filled.Favorite,
                title = tr("مفضلتك فارغة"),
                actionLabel = tr("تصفّح الأعشاب"),
                onAction = onBrowseClick,
                accent = MaterialTheme.colorScheme.error
            )
        } else {
            // نفس إصلاح AllHerbsScreen: عمود واحد ثابت كان يمدّد البطاقة
            // بعرض الشاشة كاملاً على تابلت. Adaptive يحسب عدد الأعمدة من
            // العرض الفعلي المتاح بدل قيمة مكتوبة يدوياً.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, top = 16.dp, end = 16.dp,
                    bottom = 16.dp + LocalBottomBarInset.current
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(favoriteHerbs, key = { _, h -> h.id }) { index, herb ->
                    HerbCard(
                        herb = herb,
                        isFavorite = true,
                        onClick = { onHerbClick(herb) },
                        onToggleFavorite = { onToggleFavorite(herb.id) },
                        modifier = Modifier.animateItem().staggeredEntrance(index)
                    )
                }
            }
        }
    }
}
