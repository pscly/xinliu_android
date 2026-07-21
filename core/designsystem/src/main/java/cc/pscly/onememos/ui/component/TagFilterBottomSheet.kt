@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cc.pscly.onememos.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.domain.tag.TagStat
import cc.pscly.onememos.ui.filter.TagMatchMode
import cc.pscly.onememos.ui.theme.PaperInkModalBottomSheet

@Composable
fun TagFilterBottomSheet(
    title: String,
    allTags: List<TagStat>,
    selectedTags: Set<String>,
    showTagCounts: Boolean,
    tagMatchMode: TagMatchMode,
    excludeTags: Boolean,
    onExcludeTagsChange: (Boolean) -> Unit,
    onToggleTag: (String) -> Unit,
    onTagMatchModeChange: (TagMatchMode) -> Unit,
    onClear: () -> Unit,
    onApply: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 排除开启时强制 OR；避免重组死循环，仅在“排除 + AND”时做一次纠正。
    LaunchedEffect(excludeTags, tagMatchMode) {
        if (excludeTags && tagMatchMode == TagMatchMode.AND) {
            onTagMatchModeChange(TagMatchMode.OR)
        }
    }

    PaperInkModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = InkSpacing.SheetMarginH),
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            modifier = Modifier
                .padding(horizontal = InkSpacing.SheetMarginH)
                .padding(top = InkSpacing.SheetTitleTopGap),
            text = "排除：含任意所选标签的记录会被隐藏",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        Spacer(modifier = Modifier.height(InkSpacing.SheetGapM))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.SheetMarginH),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "排除/不含",
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                modifier = Modifier.semantics { contentDescription = "排除" },
                checked = excludeTags,
                onCheckedChange = onExcludeTagsChange,
            )
        }

        Spacer(modifier = Modifier.height(InkSpacing.SheetGapM))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = InkSpacing.SheetMarginH),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.SheetGapS)) {
                OutlinedButton(
                    onClick = { onTagMatchModeChange(TagMatchMode.OR) },
                    enabled = tagMatchMode != TagMatchMode.OR,
                ) {
                    Text("或")
                }
                OutlinedButton(
                    onClick = { onTagMatchModeChange(TagMatchMode.AND) },
                    enabled = !excludeTags && tagMatchMode != TagMatchMode.AND,
                ) {
                    Text("与")
                }
            }

            TextButton(onClick = onClear) {
                Text("清空")
            }
        }

        Spacer(modifier = Modifier.height(InkSpacing.SheetGapS))

        if (allTags.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = InkSpacing.SheetMarginH, vertical = InkSpacing.SheetEmptyPaddingV),
                text = "还没有任何标签。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            FlowRow(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.SheetMarginH),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.SheetGapS),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.SheetGapS),
            ) {
                allTags.forEach { stat ->
                    TagChip(
                        tag = stat.name,
                        label = if (showTagCounts) "${stat.name} (${stat.count})" else stat.name,
                        selected = selectedTags.contains(stat.name),
                        onClick = { onToggleTag(stat.name) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(InkSpacing.SheetGapL))

        if (onApply != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = InkSpacing.SheetMarginH),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onApply) {
                    Text("应用筛选")
                }
            }
            Spacer(modifier = Modifier.height(InkSpacing.SheetGapS))
        }
    }
}
