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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.ui.component.InkCard
import cc.pscly.onememos.ui.component.MarkdownPreview
import cc.pscly.onememos.ui.theme.InkBorder
import cc.pscly.onememos.ui.theme.InkShape
import cc.pscly.onememos.ui.theme.InkSpacing
import cc.pscly.onememos.ui.util.DateTimeFormatter
import cc.pscly.onememos.ui.util.rememberOneMemosHaptics
import java.time.LocalDate
import java.time.YearMonth
import cc.pscly.onememos.ui.theme.PaperInkTopAppBar
import cc.pscly.onememos.ui.theme.PaperInkAlertDialog

@Composable
fun ProfileScreen(
    onOpenDrawer: () -> Unit,
    onOpenMemo: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberOneMemosHaptics()

    ProfileScreenContent(
        uiState = uiState,
        onOpenDrawer = onOpenDrawer,
        onOpenMemo = onOpenMemo,
        onSetMonth = viewModel::setMonth,
        onPrevMonth = viewModel::prevMonth,
        onNextMonth = viewModel::nextMonth,
        onGoToToday = viewModel::goToToday,
        onSelectSingle = viewModel::selectSingle,
        onStartRange = { date ->
            haptics.tick()
            viewModel.startRange(date)
        },
        onUpdateRange = { date ->
            haptics.tick()
            viewModel.updateRange(date)
        },
    )
}

/**
 * 可测入口：无 Hilt。参数为 uiState + 9 个回调，共 10 个。
 */
@Composable
internal fun ProfileScreenContent(
    uiState: ProfileUiState,
    onOpenDrawer: () -> Unit,
    onOpenMemo: (String) -> Unit,
    onSetMonth: (YearMonth) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGoToToday: () -> Unit,
    onSelectSingle: (LocalDate) -> Unit,
    onStartRange: (LocalDate) -> Unit,
    onUpdateRange: (LocalDate) -> Unit,
) {
    val selection = uiState.selection
    val start = selection.start
    val end = selection.end
    val month = uiState.month
    // 标题保持简洁，不显示“选中天数”这类提示文案。
    val title = "个人中心"
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PaperInkTopAppBar(
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
                    onSetMonth(picked)
                },
            )
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            // 水平边距改由各 item 控制：热力图卡收紧至 X12，其余保持 X16
            contentPadding = PaddingValues(vertical = InkSpacing.X14),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X12),
        ) {
            item {
                InkCard(
                    modifier =
                        Modifier
                            .padding(horizontal = InkSpacing.X12)
                            .testTag("profile_heatmap_card"),
                    onClick = null,
                    // 零水平内边距：W360 下内容宽 360−24=336dp → 7×48 单元格
                    contentPadding = PaddingValues(vertical = InkSpacing.CardPadding),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.X10)) {
                        MonthHeader(
                            modifier = Modifier.padding(horizontal = InkSpacing.CardPadding),
                            month = month,
                            onPrev = onPrevMonth,
                            onNext = onNextMonth,
                            onPick = { showMonthPicker = true },
                            onToday = onGoToToday,
                        )

                        HeatmapGrid(
                            model = uiState.heatmap,
                            selection = selection,
                            onTapDate = onSelectSingle,
                            onDragStart = onStartRange,
                            onDragUpdate = onUpdateRange,
                        )

                        HeatmapLegend(
                            modifier = Modifier.padding(horizontal = InkSpacing.CardPadding),
                            maxCount = uiState.heatmap.maxCount,
                        )
                    }
                }
            }

            item {
                InkCard(
                    modifier =
                        Modifier
                            .padding(horizontal = InkSpacing.X16)
                            .testTag("profile_selection_card"),
                    onClick = null,
                ) {
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
                    InkCard(
                        modifier =
                            Modifier
                                .padding(horizontal = InkSpacing.X16)
                                .testTag("profile_empty_card"),
                        onClick = null,
                    ) {
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
                        modifier = Modifier.padding(horizontal = InkSpacing.X16),
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
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
    ) {
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
                // 结构常量：时间列固定宽，列表对齐用几何，非间距尺度
                modifier = Modifier.width(InkSpacing.CalendarCellMin),
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
internal fun HeatmapGrid(
    model: HeatmapUiModel,
    selection: DateRangeSelection,
    onTapDate: (LocalDate) -> Unit,
    onDragStart: (LocalDate) -> Unit,
    onDragUpdate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("profile_heatmap_grid"),
    ) {
        val rowGap = InkSpacing.X6
        // 结构常量：热力图单元格自适应上下界（44~56dp），组件特有几何，非间距尺度
        val maxCell = InkSpacing.CalendarCellMax
        val minCell = InkSpacing.CalendarCellMin
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
            modifier =
                Modifier
                    .wrapContentWidth()
                    .width(gridWidth)
                    .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.X10),
        ) {
            // 结构常量：热力图表头/网格零间距贴合，命中与对齐依赖 0dp（禁止令牌化 0/1）
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
                    // 结构常量：热力图行内零间距贴合，范围高亮连片依赖 0dp（禁止令牌化 0/1）
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
                                onTapDate = onTapDate,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HeatmapCell(
    size: Dp,
    date: LocalDate,
    count: Int,
    maxCount: Int,
    inMonth: Boolean,
    selected: Boolean,
    connectLeft: Boolean,
    connectRight: Boolean,
    onTapDate: (LocalDate) -> Unit = {},
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

    val cellDescription =
        if (inMonth) {
            "${date.monthValue}月${date.dayOfMonth}日，${count} 篇${if (selected) "，已选中" else ""}"
        } else {
            null
        }

    Box(
        modifier =
            Modifier
                .size(size)
                .minimumInteractiveComponentSize()
                .testTag("heatmap_cell_${date}")
                .then(
                    if (inMonth) {
                        Modifier.semantics {
                            onClick(
                                label = "选择 ${date.monthValue}月${date.dayOfMonth}日",
                                action = {
                                    onTapDate(date)
                                    true
                                },
                            )
                            contentDescription = cellDescription.orEmpty()
                        }
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
            // 结构常量：999dp 胶囊端点/整 pill 圆角，热力选中几何，无对应形状令牌
            if (selected) {
                val rangeShape =
                    when {
                        connectLeft && connectRight -> RectangleShape
                        connectLeft && !connectRight -> InkShape.PillEnd
                        !connectLeft && connectRight -> InkShape.PillStart
                        else -> InkShape.Pill
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
                        .padding(vertical = InkSpacing.X8, horizontal = InkSpacing.X2)
                        .background(bg, shape = InkShape.Card),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = InkSpacing.X6),
            ) {
                // 今天：描边圈（未选中时可见）
                // 结构常量：34dp 今日圈径、1.6dp 描边、999dp pill，热力图专用几何
                if (isToday && !selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(InkSpacing.CalendarDaySize)
                            .border(
                                width = InkBorder.CalendarRing,
                                color = primary.copy(alpha = 0.60f),
                                shape = InkShape.Pill,
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
private fun HeatmapLegend(
    maxCount: Int,
    modifier: Modifier = Modifier,
) {
    if (maxCount <= 0) return
    Row(
        modifier = modifier.fillMaxWidth(),
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
            // 结构常量：3dp 图例方块微圆角，无等值形状令牌
            .background(primary.copy(alpha = alpha.coerceIn(0f, 1f)), shape = InkShape.Legend),
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

    PaperInkAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "跳转月份") },
        text = {
            LazyColumn(
                // 结构常量：月份选择列表可视高度，对话框专用几何，非间距尺度
                modifier = Modifier.height(InkSpacing.ProfileCalendarHeight),
            ) {
                itemsIndexed(months, key = { _, m -> "${m.year}-${m.monthValue}" }) { _, m ->
                    val selected = m == current
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(m) }
                            .padding(vertical = InkSpacing.X12, horizontal = InkSpacing.X4),
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
