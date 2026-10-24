package com.salman.herbalencyclopedia.ui.screens.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import com.salman.herbalencyclopedia.ui.components.GlassIconButton
import com.salman.herbalencyclopedia.ui.components.GlassTopBar
import com.salman.herbalencyclopedia.ui.components.LiquidGlassSurface
import com.salman.herbalencyclopedia.ui.components.LocalGlassBackdrop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salman.herbalencyclopedia.data.model.Herb
import com.salman.herbalencyclopedia.ui.util.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminListScreen(
    herbs: List<Herb>,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onEdit: (Herb) -> Unit,
    onDelete: (Herb) -> Unit,
    onTools: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<Herb?>(null) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            GlassTopBar(
                title = { Text(tr("لوحة تحكم الأدمن")) },
                navigationIcon = {
                    GlassIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("رجوع"))
                    }
                }
            )
        },
        floatingActionButton = {
            // ═══ إصلاح خلل حقيقي أُبلغ عنه: زرّا "أدوات"/"إضافة عشبة" (كانا
            // ExtendedFloatingActionButton مكدَّسين عمودياً) يغطّيان فعلياً
            // زرّي التعديل/الحذف لآخر عنصر أو عنصرين بالقائمة، فيتعذّر
            // الوصول إليهما إطلاقاً ═══
            // الحل هنا مزدوج: (1) شريط أفقي زجاجي واحد بنفس أسلوب الشريط
            // السفلي العائم في الشاشة الرئيسية (راجع OneUiFloatingNavBar في
            // OneUiBars.kt — نفس التلوين والزجاجية هنا) بدل عمودين منفصلين،
            // فارتفاعه الكلي أقصر بكثير؛ و(2) contentPadding سفلي إضافي على
            // الشبكة أدناه يحجز مساحة كافية له فلا يتراكب مع أي عنصر مهما
            // طالت القائمة، بدل الاعتماد فقط على تقصير الشريط.
            val container = androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.primary,
                0.14f
            )
            val barShape = RoundedCornerShape(30.dp)
            LiquidGlassSurface(
                shape = barShape,
                tint = container,
                glowColor = MaterialTheme.colorScheme.tertiary,
                borderAlpha = 0.16f,
                blurBubbles = false,
                backdrop = LocalGlassBackdrop.current,
                backdropTintAlphaTop = 0.74f,
                backdropTintAlphaBottom = 0.60f
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdminBarAction(icon = Icons.Filled.Settings, label = tr("أدوات"), onClick = onTools)
                    AdminBarAction(icon = Icons.Filled.Add, label = tr("إضافة عشبة"), onClick = onAddNew)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { padding ->
        // شبكة متكيّفة بدل عمود واحد ثابت: نفس إصلاح شاشات قوائم الأعشاب،
        // مهم هنا خصوصاً لأن لوحة التحكم قد تُستخدم من تابلت إداري.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            // حشوة سفلية أكبر بوضوح من بقية الجهات (16dp) لحجز مساحة كافية
            // للشريط الزجاجي العائم أعلاه (راجع توثيقه) — تكفي أي طول شاشة،
            // بدل الاعتماد على قِصَره فقط لتفادي التراكب.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(herbs, key = { it.id }) { herb ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        // كان زرّا التعديل والحذف يُوضعان مباشرة بجوار بعضهما
                        // بلا أي مسافة فاصلة (Arrangement الافتراضي بلا
                        // spacedBy)، فتتلامس حدودهما الدائرية فعلياً ويسهل
                        // الضغط على الزر الخطأ (خصوصاً زر الحذف المجاور
                        // مباشرة لزر التعديل). هذه المسافة تفصلهما بوضوح عن
                        // بعضهما وعن النص المجاور.
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = herb.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        GlassIconButton(onClick = { onEdit(herb) }) {
                            Icon(Icons.Filled.Edit, contentDescription = tr("تعديل"))
                        }
                        GlassIconButton(onClick = { pendingDelete = herb }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = tr("حذف"),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { herb ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("حذف \"${herb.name}\"؟")) },
            text = { Text(tr("لا يمكن التراجع عن هذا الإجراء.")) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(herb)
                    pendingDelete = null
                }) { Text(tr("حذف"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(tr("إلغاء")) }
            }
        )
    }
}

/** عنصر واحد (أيقونة + نص) داخل الشريط الزجاجي العائم أعلاه في [AdminListScreen]. */
@Composable
private fun AdminBarAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}
