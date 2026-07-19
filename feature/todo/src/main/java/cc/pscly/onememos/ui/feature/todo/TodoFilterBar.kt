package cc.pscly.onememos.ui.feature.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import cc.pscly.onememos.domain.model.TodoList
import cc.pscly.onememos.domain.model.TodoStatuses
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.InkChip
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.theme.InkSpacing

@Composable
internal fun TodoFilterBar(
    lists: List<TodoList>,
    includeArchivedLists: Boolean,
    selectedListId: String?,
    statusFilter: String?,
    tagFilter: String,
    onSelectList: (String?) -> Unit,
    onSetStatusFilter: (String?) -> Unit,
    onSetTagFilter: (String) -> Unit,
    onToggleIncludeArchived: (Boolean) -> Unit,
    onOpenTagInput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "筛选",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                InkChip(
                    label = if (includeArchivedLists) "含归档：开" else "含归档：关",
                    selected = includeArchivedLists,
                    onClick = { onToggleIncludeArchived(!includeArchivedLists) },
                )
            }

            Text(
                text = "清单",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    InkChip(
                        label = "全部",
                        selected = selectedListId == null,
                        onClick = { onSelectList(null) },
                    )
                }
                items(lists, key = { it.id }) { list ->
                    InkChip(
                        label = if (list.archived) "${list.name}（归档）" else list.name,
                        selected = selectedListId == list.id,
                        onClick = { onSelectList(list.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(InkSpacing.X2))

            Text(
                text = "状态",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X8),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    InkChip(
                        label = "全部",
                        selected = statusFilter == null,
                        onClick = { onSetStatusFilter(null) },
                    )
                }
                item {
                    InkChip(
                        label = "未完成",
                        selected = statusFilter == TodoStatuses.OPEN,
                        onClick = { onSetStatusFilter(TodoStatuses.OPEN) },
                    )
                }
                item {
                    InkChip(
                        label = "已完成",
                        selected = statusFilter == TodoStatuses.DONE,
                        onClick = { onSetStatusFilter(TodoStatuses.DONE) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(InkSpacing.X2))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "标签",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))

                if (tagFilter.isNotBlank()) {
                    TagChip(
                        tag = tagFilter,
                        selected = true,
                        onClick = onOpenTagInput,
                    )
                    Spacer(modifier = Modifier.width(InkSpacing.X8))
                    InkChip(
                        label = "清除",
                        selected = false,
                        onClick = { onSetTagFilter("") },
                    )
                } else {
                    Text(
                        text = "未筛选",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.width(InkSpacing.X10))
                    InkChip(
                        label = "筛选标签",
                        selected = false,
                        onClick = onOpenTagInput,
                    )
                }
            }

            if (tagFilter.isNotBlank()) {
                Text(
                    modifier = Modifier.padding(top = InkSpacing.X2),
                    text = "提示：点击条目上的标签，可快速切换筛选。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

