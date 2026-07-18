@file:OptIn(ExperimentalMaterial3Api::class)

package cc.pscly.onememos.ui.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.MarkdownPreview
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ProfileScreen(
    onOpenDrawer: () -> Unit,
    onOpenMemo: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberOneMemosHaptics()

    val selection = uiState.selection
    val start = selection.start
    val end = selection.end
    val month = uiState.month
    // 标题保持简洁，不显示“选中天数”这类提示文案。
    val title = "个人中心"
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Filled.Menu, contentDescription = "菜单")
                    }
                },
            )
        },
    ) { padding ->
        if (showMonthPicker) {
            MonthPickerDialog(
                current = month,
                onDismiss = { showMonthPicker = false },
                onPick = { picked ->
                    showMonthPicker = false
                    viewModel.setMonth(picked)
                },
            )
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = InkSpacing.X16, vertical = InkSpacing.X14),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X12),
        ) {
            item {
                InkCard(onClick = null) {
                    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                        MonthHeader(
                            month = month,
                            onPrev = viewModel::prevMonth,
                            onNext = viewModel::nextMonth,
                            onPick = { showMonthPicker = true },
                            onToday = viewModel::goToToday,
                        )

                        HeatmapGrid(
                            model = uiState.heatmap,
                            selection = selection,
                            onTapDate = { date -> viewModel.selectSingle(date) },
                            onDragStart = { date ->
                                haptics.tick()
                                viewModel.startRange(date)
                            },
                            onDragUpdate = { date ->
                                haptics.tick()
                                viewModel.updateRange(date)
                            },
                        )

                        HeatmapLegend(maxCount = uiState.heatmap.maxCount)
                    }
                }
            }

            item {
                InkCard(onClick = null) {
                    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X6)) {
                        Text(
                            text = "选中范围：${start} ～ ${end}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "记录：${uiState.selectedMemoCount} 条（按时间正序）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            if (uiState.sections.isEmpty()) {
                item {
                    InkCard(onClick = null) {
                        Text(
                            text = "这段时间没有记录。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                items(uiState.sections, key = { it.date.toString() }) { section ->
                    DaySection(
                        section = section,
                        onOpenMemo = onOpenMemo,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(imageVector = Icons.Filled.ChevronLeft, contentDescription = "上个月")
        }
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = onPick,
        ) {
            Text(
                text = "${month.year}年${month.monthValue.toString().padStart(2, '0')}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        IconButton(onClick = onNext) {
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = "下个月")
        }
        TextButton(onClick = onToday) {
            Text(text = "今天")
        }
    }
}

@Composable
private fun DaySection(
    section: ProfileDaySection,
    onOpenMemo: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
        Text(
            text = "${section.date}（周${section.date.dayOfWeek.toChinese()}）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
            section.memos.forEach { memo ->
                MemoRow(
                    memo = memo,
                    onClick = { onOpenMemo(memo.uuid) },
                )
            }
        }
    }
}

@Composable
private fun MemoRow(
    memo: Memo,
    onClick: () -> Unit,
) {
    InkCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.X12),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = DateTimeFormatter.formatHm(memo.createdAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.width(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                MarkdownPreview(
                    markdown = memo.content,
                    placeholder = "(空内容)",
                    maxBlocks = 3,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (memo.attachments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(InkSpacing.X6))
                    Text(
                        text = "附件：${memo.attachments.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapGrid(
    model: HeatmapUiModel,
    selection: DateRangeSelection,
    onTapDate: (LocalDate) -> Unit,
    onDragStart: (LocalDate) -> Unit,
    onDragUpdate: (LocalDate) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val rowGap = InkSpacing.X6
        // 让格子“像系统日历一样大”：按屏宽自适应，目标 44~56dp。
        val maxCell = 56.dp
        val minCell = 44.dp
        val computed = maxWidth / 7
        val cellSize = computed.coerceIn(minCell, maxCell)

        // 网格真实宽度：用于居中对齐（避免 header 与格子“散开”对不齐）。
        val gridWidth = (cellSize * 7)

        val density = LocalDensity.current
        val cellPx = with(density) { cellSize.toPx() }
        val rowGapPx = with(density) { rowGap.toPx() }
        val stepX = cellPx
        val stepY = cellPx + rowGapPx

        fun dateAt(position: Offset): LocalDate? {
            // 点击命中必须“按格子边界”计算，否则会出现点 12 号却命中 11/13/上一行的体验问题。
            if (position.x < 0f || position.y < 0f) return null
            val col = (position.x / stepX).toInt()
            val row = (position.y / stepY).toInt()
            if (col !in 0..6) return null
            if (row !in 0 until model.rows) return null

            // 行间距区域不命中（防止落在两行之间时“跳到上一行/下一行”）。
            val yInRow = position.y - (row * stepY)
            if (yInRow > cellPx) return null

            val index = row * 7 + col
            val date = model.gridStart.plusDays(index.toLong())
            // 仅允许选择“本月”日期。
            if (date.isBefore(model.activeStart) || date.isAfter(model.activeEnd)) return null
            return date
        }

        Column(
            modifier = Modifier.width(gridWidth).align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Box(
                        modifier = Modifier.width(cellSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .pointerInput(model, cellPx) {
                        // 交互优化：不再依赖“长按”才能拖动选择，让单选/多选更跟手；
                        // 同时避免误触滚动：只有超过 touchSlop 才进入“拖动选区”模式并消费事件。
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val anchor = dateAt(down.position) ?: return@awaitEachGesture

                            var lastDate: LocalDate = anchor
                            var dragging = false

                            val firstDrag =
                                awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                    dragging = true
                                    change.consume()
                                    onDragStart(anchor)
                                }

                            if (!dragging) {
                                // 未发生拖动：按“点击”处理。
                                onTapDate(anchor)
                                return@awaitEachGesture
                            }

                            // 已进入拖动：持续更新日期，并消费事件避免 LazyColumn 抢走滚动。
                            var current = firstDrag
                            while (current != null && current.pressed) {
                                val d = dateAt(current.position)
                                if (d != null && d != lastDate) {
                                    lastDate = d
                                    onDragUpdate(d)
                                }
                                current.consume()

                                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                                current =
                                    event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull()
                            }
                        }
                    },
                // 行间距交给单元格内部的 padding（更像 MIUI：范围高亮连成片但上下留白）
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                for (r in 0 until model.rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        for (c in 0..6) {
                            val date = model.gridStart.plusDays((r * 7L) + c.toLong())
                            val inMonth = !date.isBefore(model.activeStart) && !date.isAfter(model.activeEnd)
                            val count = if (inMonth) (model.counts[date] ?: 0) else 0
                            val selected = inMonth && !date.isBefore(selection.start) && !date.isAfter(selection.end)
                            val connectLeft =
                                selected &&
                                    c > 0 &&
                                    date.minusDays(1) >= selection.start &&
                                    date.minusDays(1) <= selection.end
                            val connectRight =
                                selected &&
                                    c < 6 &&
                                    date.plusDays(1) >= selection.start &&
                                    date.plusDays(1) <= selection.end

                            HeatmapCell(
                                size = cellSize,
                                date = date,
                                count = count,
                                maxCount = model.maxCount,
                                inMonth = inMonth,
                                selected = selected,
                                connectLeft = connectLeft,
                                connectRight = connectRight,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    size: Dp,
    date: LocalDate,
    count: Int,
    maxCount: Int,
    inMonth: Boolean,
    selected: Boolean,
    connectLeft: Boolean,
    connectRight: Boolean,
) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val strength =
        if (!inMonth || count <= 0 || maxCount <= 0) {
            0f
        } else {
            (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
        }

    val heatAlpha =
        if (!inMonth || count <= 0 || maxCount <= 0) {
            0f
        } else {
            (0.14f + 0.76f * strength).coerceIn(0f, 1f)
        }

    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (inMonth) {
                    Modifier
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (inMonth) {
            val isToday = date == LocalDate.now()
            val dayColor = if (selected) onContainer else MaterialTheme.colorScheme.onSurface

            // MIUI 风格：范围选中时，背景连成片（按左右是否相邻决定“圆角端点”）。
            if (selected) {
                val rangeShape =
                    when {
                        connectLeft && connectRight -> RectangleShape
                        connectLeft && !connectRight -> RoundedCornerShape(topEnd = 999.dp, bottomEnd = 999.dp)
                        !connectLeft && connectRight -> RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp)
                        else -> RoundedCornerShape(999.dp)
                    }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = InkSpacing.X8)
                        .background(container, shape = rangeShape),
                )
            } else {
                // 热力图：本月的“热度”用方格底色体现，而不是一个点。
                val bg =
                    if (heatAlpha > 0f) {
                        primary.copy(alpha = heatAlpha)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
                    }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = 8.dp, horizontal = 2.dp)
                        .background(bg, shape = InkShape.Card),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = InkSpacing.X6),
            ) {
                // 今天：描边圈（未选中时可见）
                if (isToday && !selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                            .border(
                                width = 1.6.dp,
                                color = primary.copy(alpha = 0.60f),
                                shape = RoundedCornerShape(999.dp),
                            ),
                    )
                }

                Text(
                    modifier = Modifier.align(Alignment.Center),
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = dayColor.copy(alpha = 0.92f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HeatmapLegend(maxCount: Int) {
    if (maxCount <= 0) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "少",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.width(InkSpacing.X8))
        HeatLegendSquare(alpha = 0.22f)
        Spacer(modifier = Modifier.width(InkSpacing.X6))
        HeatLegendSquare(alpha = 0.42f)
        Spacer(modifier = Modifier.width(InkSpacing.X6))
        HeatLegendSquare(alpha = 0.62f)
        Spacer(modifier = Modifier.width(InkSpacing.X6))
        HeatLegendSquare(alpha = 0.88f)
        Spacer(modifier = Modifier.width(InkSpacing.X8))
        Text(
            text = "多",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun HeatLegendSquare(alpha: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(InkSpacing.X10)
            .background(primary.copy(alpha = alpha.coerceIn(0f, 1f)), shape = RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun MonthPickerDialog(
    current: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth) -> Unit,
) {
    // 最近 60 个月（含当前月），足够跨年跳转；按“从新到旧”排列，当前月靠前更好找。
    val months =
        remember(current) {
            buildList {
                add(current)
                for (i in 1..60) add(current.minusMonths(i.toLong()))
            }
        }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "跳转月份") },
        text = {
            LazyColumn(
                modifier = Modifier.height(380.dp),
            ) {
                itemsIndexed(months, key = { _, m -> "${m.year}-${m.monthValue}" }) { _, m ->
                    val selected = m == current
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(m) }
                            .padding(vertical = InkSpacing.X12, horizontal = 4.dp),
                        text = "${m.year}年${m.monthValue.toString().padStart(2, '0')}月",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        },
    )
}

private fun java.time.DayOfWeek.toChinese(): String =
    when (this) {
        java.time.DayOfWeek.MONDAY -> "一"
        java.time.DayOfWeek.TUESDAY -> "二"
        java.time.DayOfWeek.WEDNESDAY -> "三"
        java.time.DayOfWeek.THURSDAY -> "四"
        java.time.DayOfWeek.FRIDAY -> "五"
        java.time.DayOfWeek.SATURDAY -> "六"
        java.time.DayOfWeek.SUNDAY -> "日"
    }
