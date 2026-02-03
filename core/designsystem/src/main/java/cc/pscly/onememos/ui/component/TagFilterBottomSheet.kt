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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.pscly.onememos.domain.tag.TagStat
import cc.pscly.onememos.ui.filter.TagMatchMode

@Composable
fun TagFilterBottomSheet(
    title: String,
    allTags: List<TagStat>,
    selectedTags: Set<String>,
    showTagCounts: Boolean,
    tagMatchMode: TagMatchMode,
    onToggleTag: (String) -> Unit,
    onTagMatchModeChange: (TagMatchMode) -> Unit,
    onClear: () -> Unit,
    onApply: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onTagMatchModeChange(TagMatchMode.OR) },
                    enabled = tagMatchMode != TagMatchMode.OR,
                ) {
                    Text("或")
                }
                OutlinedButton(
                    onClick = { onTagMatchModeChange(TagMatchMode.AND) },
                    enabled = tagMatchMode != TagMatchMode.AND,
                ) {
                    Text("与")
                }
            }

            TextButton(onClick = onClear) {
                Text("清空")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (allTags.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                text = "还没有任何标签。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            FlowRow(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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

        Spacer(modifier = Modifier.height(18.dp))

        if (onApply != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onApply) {
                    Text("应用筛选")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
