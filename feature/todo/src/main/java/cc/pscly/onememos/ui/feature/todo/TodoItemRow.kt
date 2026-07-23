@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.todo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.TagChip
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing

@Composable
internal fun TodoItemRow(
    p: TodoItemPresentation,
    listName: String?,
    onOpen: (TodoItem) -> Unit,
    onToggleDone: (TodoItem, Boolean) -> Unit,
    onDelete: (TodoItem) -> Unit,
    onTagClick: (String) -> Unit,
    onStamp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = p.item
    val isDone = p.isDone

    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { full -> full * 0.35f },
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        if (item.isRecurring) {
                            // 循环任务：右滑代表“完成下次发生”（不会做反向撤销）。
                            onToggleDone(item, true)
                            onStamp()
                        } else {
                            val newDone = !isDone
                            onToggleDone(item, newDone)
                            if (newDone) onStamp()
                        }
                        // 不真正 dismiss，回弹即可；数据流变化会自行更新列表位置/分组。
                        false
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        onDelete(item)
                        // 删除也不强行 dismiss：让 item 由数据流移除，避免状态与列表不同步。
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> true
                }
            },
        )

    SwipeToDismissBox(
        modifier = modifier.fillMaxWidth(),
        state = dismissState,
        backgroundContent = {
            val bgColor =
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surface
                }
            val fgColor =
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            val text =
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> "已办"
                    SwipeToDismissBoxValue.EndToStart -> "删除"
                    SwipeToDismissBoxValue.Settled -> ""
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(InkShape.Card)
                        .background(bgColor)
                        .padding(horizontal = InkSpacing.X16),
                contentAlignment =
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    },
            ) {
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = fgColor.copy(alpha = 0.90f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) {
        InkCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onOpen(item) },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TodoDoneMark(
                    isDone = isDone,
                    isRecurring = item.isRecurring,
                    onClick = {
                        if (item.isRecurring) {
                            onToggleDone(item, true)
                            onStamp()
                        } else {
                            val newDone = !isDone
                            onToggleDone(item, newDone)
                            if (newDone) onStamp()
                        }
                    },
                )

                Spacer(modifier = Modifier.size(InkSpacing.X10))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(InkSpacing.X4),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isDone) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface,
                    )

                    TodoMetaLines(
                        p = p,
                        listName = listName,
                    )

                    if (item.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(InkSpacing.X4))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(InkSpacing.X6)) {
                            items(item.tags, key = { it }) { tag ->
                                TagChip(
                                    tag = tag,
                                    onClick = { onTagClick(tag) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TodoDoneMark(
    isDone: Boolean,
    isRecurring: Boolean,
    onClick: () -> Unit,
) {
    val shape = InkShape.Tag
    val borderColor =
        if (isDone) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        }
    val bgColor =
        if (isDone) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        }

    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(
                    minHeight = InkSpacing.TouchTargetMin,
                    minWidth = InkSpacing.TouchTargetMin,
                ).then(
                    if (isRecurring) {
                        Modifier
                            .clickable(role = Role.Button, onClick = onClick)
                            .semantics { contentDescription = "完成下次循环任务" }
                    } else {
                        Modifier
                            .toggleable(
                                value = isDone,
                                role = Role.Checkbox,
                                onValueChange = { onClick() },
                            ).semantics { stateDescription = if (isDone) "已完成" else "未完成" }
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Surface(
            modifier =
                Modifier
                    .size(InkSpacing.TodoStatusIconSize)
                    .clearAndSetSemantics {},
            shape = shape,
            color = bgColor,
            border = BorderStroke(InkBorder.Hairline, borderColor),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isDone -> {
                        androidx.compose.material3.Icon(
                            modifier = Modifier.clearAndSetSemantics {},
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
                        )
                    }
                    isRecurring -> {
                        Text(
                            modifier = Modifier.clearAndSetSemantics {},
                            text = "循环",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun TodoMetaLines(
    p: TodoItemPresentation,
    listName: String?,
) {
    val item = p.item
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant

    val effDue = p.effectiveDueAtLocal?.trim().orEmpty()
    val dueLine =
        when {
            effDue.isBlank() -> null
            item.dueAtLocal?.trim().orEmpty().isNotBlank() -> "到期：$effDue"
            item.isRecurring -> "下次：$effDue"
            else -> "到期：$effDue"
        }

    if (!dueLine.isNullOrBlank()) {
        Text(
            text = dueLine,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (!p.isDone && p.effectiveDueLocal != null && p.effectiveDueLocal.toLocalDate().isBefore(p.nowDate)) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.90f)
                } else {
                    metaColor
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else if (item.isRecurring) {
        Text(
            text = "循环任务",
            style = MaterialTheme.typography.bodySmall,
            color = metaColor,
        )
    }

    val reminderSummary = p.reminderSummary?.trim().orEmpty()
    if (reminderSummary.isNotBlank()) {
        Text(
            text = reminderSummary,
            style = MaterialTheme.typography.bodySmall,
            color = metaColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (!listName.isNullOrBlank()) {
        Text(
            text = "清单：$listName",
            style = MaterialTheme.typography.bodySmall,
            color = metaColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
