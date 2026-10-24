package com.salman.herbalencyclopedia.ui.screens.herbdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.ui.components.HerbThumbnail
import com.salman.herbalencyclopedia.ui.util.ResponsiveScreenContent
import com.salman.herbalencyclopedia.ui.util.rememberWindowSizeInfo
import com.salman.herbalencyclopedia.ui.util.tr

private data class InfoSection(
    val title: String,
    val content: String,
    val icon: ImageVector,
    val tint: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbDetailScreen(
    herb: Herb,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReportIssue: () -> Unit
) {
    // عناوين الأقسام هنا تمر عبر tr(): إن كانت لغة التطبيق إنجليزية تُترجَم
    // فوراً من القاموس المحلي (بلا شبكة)، أما محتوى العشبة نفسه (herb.benefits
    // وغيره) فيصل مُترجَماً مسبقاً من AppViewModel (انظر translateHerbs) عبر
    // ترجمة جوجل المجانية عند اختيار الإنجليزية، فلا حاجة لتمريره على tr() هنا.
    val sections = listOf(
        InfoSection(tr("الفوائد"), herb.benefits, Icons.Filled.Favorite, Color(0xFF2E7D32)),
        InfoSection(tr("طريقة الاستخدام"), herb.usage, Icons.Filled.LocalPharmacy, Color(0xFF1565C0)),
        InfoSection(tr("التحذيرات"), herb.warnings, Icons.Filled.WarningAmber, Color(0xFFEF6C00)),
        InfoSection(tr("الأضرار المحتملة"), herb.harms, Icons.Filled.ReportProblem, Color(0xFFC62828)),
        InfoSection(tr("ملاحظات إضافية"), herb.notes, Icons.Filled.StickyNote2, Color(0xFF6A1B9A))
    ).filter { it.content.isNotBlank() && it.content != "—" }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(herb.name) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                },
                actions = {
                    GlassIconButton(onClick = onReportIssue) {
                        Icon(Icons.Filled.Feedback, contentDescription = tr("الإبلاغ عن خطأ بالمعلومات"))
                    }
                    GlassIconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current
                        )
                    }
                }
            )
        }
    ) { padding ->
        ResponsiveScreenContent(windowInfo = rememberWindowSizeInfo(), modifier = Modifier.padding(padding)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // previewOnClick=true: الضغط على صورة العشبة هنا (وليس على
                    // الصف بأكمله، فهو ليس قابلاً للنقر أصلاً في هذه الشاشة)
                    // يفتح معاينة بحجم الشاشة الكاملة — انظر HerbThumbnail وImagePreviewDialog.
                    HerbThumbnail(imageUrl = herb.imageUrl, size = 72.dp, previewOnClick = true)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = herb.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            items(sections) { section ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(section.tint.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(section.icon, contentDescription = null, tint = section.tint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = section.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        }
    }
}
