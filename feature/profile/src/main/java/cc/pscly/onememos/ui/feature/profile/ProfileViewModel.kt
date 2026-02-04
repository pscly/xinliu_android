@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package cc.pscly.onememos.ui.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.domain.model.MemoVisibility
import cc.pscly.onememos.domain.repository.MemoRepository
import cc.pscly.onememos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.NavigableMap
import java.util.TreeMap
import javax.inject.Inject

data class ProfileUiState(
    val heatmap: HeatmapUiModel,
    val month: YearMonth,
    val selection: DateRangeSelection,
    val sections: List<ProfileDaySection>,
    val selectedMemoCount: Int,
) {
    companion object {
        fun initial(zoneId: ZoneId): ProfileUiState {
            val today = LocalDate.now(zoneId)
            val month = YearMonth.from(today)
            val selection = DateRangeSelection(anchor = today, current = today)
            return ProfileUiState(
                heatmap = buildHeatmapUiModel(memos = emptyList(), zoneId = zoneId, month = month),
                month = month,
                selection = selection,
                sections = emptyList(),
                selectedMemoCount = 0,
            )
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val memoRepository: MemoRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val zoneId: ZoneId = ZoneId.systemDefault()
    private var creatorHintPushed: Boolean = false
    private val initialToday: LocalDate = LocalDate.now(zoneId)

    private val _month = MutableStateFlow(YearMonth.from(initialToday))

    private val _selection =
        MutableStateFlow(
            DateRangeSelection(
                anchor = initialToday,
                current = initialToday,
            ),
        )

    private data class VisibilityContext(
        val loggedIn: Boolean,
        val showPublicWorkspace: Boolean,
        val creator: String,
    )

    private val visibilityContext: StateFlow<VisibilityContext> =
        combine(
            // 只用“最近一部分记录”推断 creator，避免全量订阅。
            memoRepository.observeRecentMemos(limit = 1000),
            settingsRepository.settings,
        ) { recent, settings ->
            val loggedIn = settings.token.isNotBlank()
            val showPublicWorkspace = settings.dev2Unlocked && settings.dev2ShowPublicWorkspaceMemos
            val myCreator = settings.currentUserCreator.trim()

            val inferredCreator =
                if (loggedIn && !showPublicWorkspace && myCreator.isBlank()) {
                    val candidates =
                        recent.asSequence()
                            .filter { !it.serverId.isNullOrBlank() }
                            .filter { it.visibility != MemoVisibility.PUBLIC }
                            .mapNotNull { it.creator?.trim()?.takeIf { s -> s.isNotBlank() } }
                            .distinct()
                            .take(2)
                            .toList()
                    if (candidates.size == 1) candidates.first() else ""
                } else {
                    myCreator
                }

            if (loggedIn && !showPublicWorkspace && myCreator.isBlank() && inferredCreator.isNotBlank() && !creatorHintPushed) {
                creatorHintPushed = true
                viewModelScope.launch {
                    settingsRepository.setCurrentUserCreator(inferredCreator)
                }
            }

            VisibilityContext(
                loggedIn = loggedIn,
                showPublicWorkspace = showPublicWorkspace,
                creator = inferredCreator,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VisibilityContext(loggedIn = false, showPublicWorkspace = false, creator = ""),
            )

    private val monthMemos: StateFlow<List<Memo>> =
        _month
            .flatMapLatest { month ->
                // 只订阅“当前月”范围：Profile 的热力图与列表都按 month/selection 设计，不需要全量 memos。
                val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                memoRepository.observeMemosByCreatedAtRange(startInclusive = start, endExclusive = end)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /**
     * 性能关键：将 Memo 按天聚合与排序，避免在“拖动选择范围”时对全量 memos 反复过滤/排序导致卡顿。
     * - memos 变化时才重建索引
     * - 选择范围变化时只在索引上做 subMap（O(天数)）
     */
    private val dayIndex: StateFlow<NavigableMap<LocalDate, List<Memo>>> =
        combine(monthMemos, visibilityContext) { memos, ctx ->
            fun visible(m: Memo): Boolean {
                if (ctx.showPublicWorkspace) return true
                if (m.serverId.isNullOrBlank()) return true
                if (m.visibility == MemoVisibility.PUBLIC) return false
                if (ctx.creator.isBlank()) return false
                return m.creator == ctx.creator
            }

            memos.filter(::visible)
        }
            .map { memos -> buildMemoDayIndex(memos = memos, zoneId = zoneId) }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TreeMap(),
            )

    val uiState: StateFlow<ProfileUiState> =
        combine(
            dayIndex,
            _month,
            _selection,
        ) { dayIndex, month, selection ->
            val clampedSelection = clampSelectionToMonth(selection, month)
            val heatmap = buildHeatmapUiModel(dayIndex = dayIndex, month = month)
            val sections = buildSelectedSections(dayIndex = dayIndex, selection = clampedSelection)
            val count = sections.sumOf { it.memos.size }
            ProfileUiState(
                heatmap = heatmap,
                month = month,
                selection = clampedSelection,
                sections = sections,
                selectedMemoCount = count,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ProfileUiState.initial(zoneId),
            )

    fun selectSingle(date: LocalDate) {
        _selection.value = DateRangeSelection(anchor = date, current = date)
    }

    fun startRange(date: LocalDate) {
        _selection.value = DateRangeSelection(anchor = date, current = date)
    }

    fun updateRange(date: LocalDate) {
        _selection.update { it.copy(current = date) }
    }

    fun prevMonth() {
        val next = _month.value.minusMonths(1)
        setMonth(next)
    }

    fun nextMonth() {
        val next = _month.value.plusMonths(1)
        setMonth(next)
    }

    fun setMonth(month: YearMonth) {
        _month.value = month
        // 切换月份时，默认选中该月 1 号，避免落在不可选日期。
        val d = month.atDay(1)
        _selection.value = DateRangeSelection(anchor = d, current = d)
    }

    fun goToToday() {
        val today = LocalDate.now(zoneId)
        val month = YearMonth.from(today)
        _month.value = month
        _selection.value = DateRangeSelection(anchor = today, current = today)
    }
}

private fun clampSelectionToMonth(
    selection: DateRangeSelection,
    month: YearMonth,
): DateRangeSelection {
    val start = month.atDay(1)
    val end = month.atEndOfMonth()
    val anchor = selection.anchor.coerceIn(start, end)
    val current = selection.current.coerceIn(start, end)
    return DateRangeSelection(anchor = anchor, current = current)
}
