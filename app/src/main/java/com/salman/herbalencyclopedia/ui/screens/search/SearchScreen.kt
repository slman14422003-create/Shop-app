package com.salman.herbalencyclopedia.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Blend
import com.salman.herbalencyclopedia.data.model.Category
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.data.search.MatchField
import com.salman.herbalencyclopedia.data.search.MatchTier
import com.salman.herbalencyclopedia.data.search.SearchEngine
import com.salman.herbalencyclopedia.data.search.SearchHit
import com.salman.herbalencyclopedia.data.search.SearchResults
import com.salman.herbalencyclopedia.ui.components.BlendCard
import com.salman.herbalencyclopedia.ui.components.CategoryCard
import com.salman.herbalencyclopedia.ui.components.EmptyView
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.HerbCard
import com.salman.herbalencyclopedia.ui.util.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SEARCH_DEBOUNCE_MS = 150L

private sealed interface SearchState {
    data object Idle : SearchState
    data class Ready(val results: SearchResults) : SearchState
}

private enum class SearchSection { HERBS, BLENDS, CATEGORIES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    herbs: List<Herb>,
    blends: List<Blend>,
    categories: List<Category>,
    favoriteIds: Set<String>,
    onBack: () -> Unit,
    onHerbClick: (Herb) -> Unit,
    onBlendClick: (Blend) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val trimmed = query.trim()

    val state by produceState<SearchState>(SearchState.Idle, trimmed, herbs, blends, categories) {
        if (trimmed.isEmpty()) {
            value = SearchState.Idle
            return@produceState
        }
        delay(SEARCH_DEBOUNCE_MS)
        val results = withContext(Dispatchers.Default) {
            SearchEngine.searchAll(trimmed, herbs, blends, categories)
        }
        value = SearchState.Ready(results)
    }

    val herbCountByCategory = remember(herbs) { herbs.groupingBy { it.categoryId }.eachCount() }

    val herbsTitle = tr("الأعشاب")
    val blendsTitle = tr("الخلطات")
    val categoriesTitle = tr("التصنيفات")
    val suggestionsTitle = tr("اقتراحات")

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassTopBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(tr("ابحث عن عشبة أو خلطة أو فائدة...")) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                GlassIconButton(onClick = { query = "" }, size = 34.dp) {
                                    Icon(Icons.Filled.Clear, contentDescription = tr("مسح"))
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        }
    ) { padding ->
        val current = state
        when {
            trimmed.isEmpty() -> EmptyView(
                message = tr("اكتب اسم عشبة أو خلطة أو فائدة للبحث"),
                modifier = Modifier.padding(padding)
            )
            current !is SearchState.Ready -> Box(Modifier.padding(padding).fillMaxSize())
            current.results.isEmpty -> EmptyView(
                message = tr("لا توجد نتائج لـ \"$trimmed\""),
                modifier = Modifier.padding(padding)
            )
            else -> {
                val results = current.results
                val order = remember(results) { orderedSections(results) }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (section in order) {
                        when (section) {
                            SearchSection.HERBS -> sectionItems(
                                keyPrefix = "herbs",
                                title = herbsTitle,
                                suggestionsTitle = suggestionsTitle,
                                hits = results.herbs,
                                idOf = { it.id }
                            ) { hit ->
                                val herb = hit.item
                                Column(modifier = Modifier.animateItem()) {
                                    HerbCard(
                                        herb = herb,
                                        isFavorite = herb.id in favoriteIds,
                                        onClick = { onHerbClick(herb) },
                                        onToggleFavorite = { onToggleFavorite(herb.id) }
                                    )
                                    MatchSnippet(hit)
                                }
                            }

                            SearchSection.BLENDS -> sectionItems(
                                keyPrefix = "blends",
                                title = blendsTitle,
                                suggestionsTitle = suggestionsTitle,
                                hits = results.blends,
                                idOf = { it.id }
                            ) { hit ->
                                val blend = hit.item
                                Column(modifier = Modifier.animateItem()) {
                                    BlendCard(
                                        blend = blend,
                                        ingredientCount = blend.herbIds.size,
                                        onClick = { onBlendClick(blend) }
                                    )
                                    MatchSnippet(hit)
                                }
                            }

                            SearchSection.CATEGORIES -> sectionItems(
                                keyPrefix = "categories",
                                title = categoriesTitle,
                                suggestionsTitle = suggestionsTitle,
                                hits = results.categories,
                                idOf = { it.id }
                            ) { hit ->
                                val category = hit.item
                                CategoryCard(
                                    category = category,
                                    herbCount = herbCountByCategory[category.id] ?: 0,
                                    onClick = { onCategoryClick(category) },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun orderedSections(results: SearchResults): List<SearchSection> {
    val candidates = listOf(
        SearchSection.HERBS to results.herbs.firstOrNull(),
        SearchSection.BLENDS to results.blends.firstOrNull(),
        SearchSection.CATEGORIES to results.categories.firstOrNull()
    ).mapNotNull { (section, hit) -> hit?.let { Triple(section, it.tier.ordinal, it.score) } }
    return candidates
        .sortedWith(compareBy<Triple<SearchSection, Int, Int>> { it.second }.thenByDescending { it.third })
        .map { it.first }
}

private fun <T> LazyGridScope.sectionItems(
    keyPrefix: String,
    title: String,
    suggestionsTitle: String,
    hits: List<SearchHit<T>>,
    idOf: (T) -> String,
    content: @Composable LazyGridItemScope.(SearchHit<T>) -> Unit
) {
    if (hits.isEmpty()) return
    val matches = hits.filter { it.tier == MatchTier.MATCH }
    val suggestions = hits.filter { it.tier == MatchTier.SUGGESTION }
    item(key = "$keyPrefix-header", span = { GridItemSpan(maxLineSpan) }) {
        SectionHeader(title = title, count = hits.size)
    }
    items(matches, key = { "$keyPrefix-m-${idOf(it.item)}" }) { hit -> content(this, hit) }
    if (suggestions.isNotEmpty()) {
        item(key = "$keyPrefix-suggestions", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = suggestionsTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
        items(suggestions, key = { "$keyPrefix-s-${idOf(it.item)}" }) { hit -> content(this, hit) }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MatchSnippet(hit: SearchHit<*>) {
    val field = hit.field ?: return
    val snippet = hit.snippet?.takeIf { it.isNotBlank() } ?: return
    Text(
        text = "${tr("ورد في")} ${fieldLabel(field)}: $snippet",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp)
    )
}

@Composable
private fun fieldLabel(field: MatchField): String = when (field) {
    MatchField.BENEFITS -> tr("الفوائد")
    MatchField.USAGE -> tr("طريقة الاستخدام")
    MatchField.WARNINGS -> tr("التحذيرات")
    MatchField.HARMS -> tr("الأضرار المحتملة")
    MatchField.NOTES -> tr("ملاحظات إضافية")
    MatchField.CATEGORY -> tr("التصنيف")
    MatchField.INGREDIENTS -> tr("المكونات")
}
