package cc.pscly.onememos.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import cc.pscly.onememos.ui.accessibility.ReducedMotion

private const val TAG = "AppNavigationHost"

/**
 * Navigation 3 主机：
 * - 持有唯一 [NavigationStateMachine]
 * - 只把 active 分区栈交给 [NavDisplay]
 * - 通过 [JsonNavigationStateStore] 保存六栈
 * - 消费外部输入并支持欢迎页优先级
 * - 转场编排（ADR 0012 / M2.9）：[SharedTransitionLayout] 承载共享元素作用域，
 *   页面级 fade + 共享轴转场；[ReducedMotion] 生效（系统“移除动画”或设置
 *   `pageTransitionsEnabled=false`）时全部转场零动画直切，共享元素作用域一并摘除
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavigationHost(
    pendingExternalInput: ExternalNavigationInput?,
    onExternalInputConsumed: () -> Unit,
    showWelcome: Boolean,
    onOpenDrawer: () -> Unit,
    requestedSection: TopLevelSection?,
    onRequestedSectionHandled: () -> Unit,
    onActiveSectionChanged: (TopLevelSection) -> Unit,
    onExitApplication: () -> Unit = {},
    contributors: List<FeatureEntryContributor> = appEntryContributors,
) {
    val context = LocalContext.current
    val stateStore = remember { JsonNavigationStateStore() }
    val mapper = remember { ExternalNavigationMapper() }

    var encodedSnapshot by rememberSaveable { mutableStateOf<String?>(null) }
    var welcomeHandled by rememberSaveable { mutableStateOf(false) }

    val machine =
        remember {
            val initial =
                encodedSnapshot
                    ?.let { encoded ->
                        when (val result = stateStore.restore(encoded)) {
                            is NavigationRestoreResult.Restored -> result.snapshot
                            is NavigationRestoreResult.Rejected -> {
                                Log.w(TAG, "导航恢复被拒绝：${result.reason}")
                                null
                            }
                        }
                    }
                    ?: freshNavigationSnapshot()
            NavigationStateMachine(initial = initial)
        }

    val snapshot by machine.state.collectAsStateWithLifecycle()
    LaunchedEffect(snapshot) {
        encodedSnapshot = stateStore.save(snapshot)
        onActiveSectionChanged(snapshot.activeSection)
    }

    val host =
        remember(onOpenDrawer) {
            object : FeatureEntryHost {
                override fun openDrawer() = onOpenDrawer()
            }
        }

    val knownKeys =
        remember {
            listOf(
                HomeKey,
                CollectionsKey,
                TodoKey,
                TodoItemKey(itemId = "item-1", expectedOwnerKey = "owner-1"),
                ProfileKey,
                ArchivedKey,
                SettingsHubKey,
                WelcomeKey,
                EditorKey(uuid = null),
                EditorKey(uuid = "memos/123"),
                ShareCardKey(uuid = "share/1"),
                AuthKey(mode = null),
                AuthKey(mode = AuthMode.CUSTOM_TOKEN),
                AccountSyncSettingsKey,
                AccountManagementSettingsKey,
                AdvancedSyncSettingsKey,
                RecordEditingSettingsKey,
                ReminderCalendarSettingsKey,
                StorageOfflineSettingsKey,
                AppearanceInteractionSettingsKey,
                AboutAdvancedSettingsKey,
            )
        }
    LaunchedEffect(Unit) {
        validateEntryContributors(keys = knownKeys, contributors = contributors)
    }

    LaunchedEffect(pendingExternalInput) {
        val input = pendingExternalInput ?: return@LaunchedEffect
        when (val mapped = mapper.map(input)) {
            is ExternalNavigationResult.Accepted -> machine.applyExternal(mapped)
            is ExternalNavigationResult.Rejected ->
                Log.w(TAG, "外部导航被拒绝：input=$input reason=${mapped.reason}")
        }
        onExternalInputConsumed()
    }

    LaunchedEffect(requestedSection) {
        val section = requestedSection ?: return@LaunchedEffect
        machine.switchSection(section)
        onRequestedSectionHandled()
    }

    LaunchedEffect(showWelcome, pendingExternalInput, welcomeHandled) {
        if (welcomeHandled || !showWelcome || pendingExternalInput != null) return@LaunchedEffect
        val homeStack = machine.state.value.stacks.getValue(TopLevelSection.HOME)
        if (homeStack.none { it is WelcomeKey }) {
            if (machine.state.value.activeSection != TopLevelSection.HOME) {
                machine.switchSection(TopLevelSection.HOME)
            }
            machine.push(WelcomeKey)
        }
        welcomeHandled = true
    }

    fun handleBack() {
        when (machine.back()) {
            BackResult.Consumed -> Unit
            BackResult.ExitApplication -> {
                val activity = context as? Activity
                if (activity != null) activity.finish() else onExitApplication()
            }
        }
    }

    BackHandler(onBack = ::handleBack)

    val backStack = snapshot.stacks.getValue(snapshot.activeSection)
    val decoratedEntries =
        rememberDecoratedNavEntries(
            backStack,
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        ) { key ->
            val contributor = resolveEntryContributor(key = key, contributors = contributors)
            contributor.entry(key = key, navigator = machine, host = host)
        }

    val reducedMotion = ReducedMotion.current

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            entries = decoratedEntries,
            onBack = ::handleBack,
            sharedTransitionScope = if (reducedMotion) null else this,
            transitionSpec = pageTransitionSpec(reducedMotion),
            popTransitionSpec = pagePopTransitionSpec(reducedMotion),
            predictivePopTransitionSpec = pagePredictivePopTransitionSpec(reducedMotion),
        )
    }
}

/** 页面级转场时长（毫秒）：fade + 共享轴（Material shared-axis X 方向感）。 */
internal const val PageTransitionDurationMs = 300

/** 页面转场滑动方向（共享轴 X）。 */
internal enum class PageSlideDirection {
    Left,
    Right,
}

/**
 * 页面转场决策（ADR 0012）：
 * - reducedMotion 为真（系统“移除动画”或设置 `pageTransitionsEnabled=false`）→ 零动画直切
 * - 否则 fade + 共享轴 X：前进（push）向左，返回（pop）向右
 */
internal data class PageTransitionPlan(
    val reducedMotion: Boolean,
    val slideDirection: PageSlideDirection?,
)

internal fun planPageTransition(
    reducedMotion: Boolean,
    pop: Boolean,
): PageTransitionPlan =
    if (reducedMotion) {
        PageTransitionPlan(reducedMotion = true, slideDirection = null)
    } else {
        PageTransitionPlan(
            reducedMotion = false,
            slideDirection = if (pop) PageSlideDirection.Right else PageSlideDirection.Left,
        )
    }

private fun pageTransform(
    scope: AnimatedContentTransitionScope<Scene<OneMemosNavKey>>,
    plan: PageTransitionPlan,
): ContentTransform =
    with(scope) {
        when (plan.slideDirection) {
            null -> ContentTransform(EnterTransition.None, ExitTransition.None)
            PageSlideDirection.Left ->
                ContentTransform(
                    targetContentEnter =
                        fadeIn(animationSpec = tween(PageTransitionDurationMs)) +
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(PageTransitionDurationMs),
                            ),
                    initialContentExit =
                        fadeOut(animationSpec = tween(PageTransitionDurationMs)) +
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                                animationSpec = tween(PageTransitionDurationMs),
                            ),
                )
            PageSlideDirection.Right ->
                ContentTransform(
                    targetContentEnter =
                        fadeIn(animationSpec = tween(PageTransitionDurationMs)) +
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(PageTransitionDurationMs),
                            ),
                    initialContentExit =
                        fadeOut(animationSpec = tween(PageTransitionDurationMs)) +
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                                animationSpec = tween(PageTransitionDurationMs),
                            ),
                )
        }
    }

/** 前进（push）页面转场。 */
internal fun pageTransitionSpec(
    reducedMotion: Boolean,
): AnimatedContentTransitionScope<Scene<OneMemosNavKey>>.() -> ContentTransform = {
    pageTransform(this, planPageTransition(reducedMotion, pop = false))
}

/** 返回（pop）页面转场。 */
internal fun pagePopTransitionSpec(
    reducedMotion: Boolean,
): AnimatedContentTransitionScope<Scene<OneMemosNavKey>>.() -> ContentTransform = {
    pageTransform(this, planPageTransition(reducedMotion, pop = true))
}

/** 预测式返回转场：与 pop 一致（忽略 SwipeEdge 参数）。 */
internal fun pagePredictivePopTransitionSpec(
    reducedMotion: Boolean,
): AnimatedContentTransitionScope<Scene<OneMemosNavKey>>.(Int) -> ContentTransform = { _ ->
    pageTransform(this, planPageTransition(reducedMotion, pop = true))
}

/**
 * 纯逻辑控制器：供进程恢复与外部输入消费单测使用，不依赖 Compose。
 */
class AppNavigationController(
    initial: NavigationSnapshot = freshNavigationSnapshot(),
    private val mapper: ExternalNavigationMapper = ExternalNavigationMapper(),
    private val stateStore: NavigationStateStore = JsonNavigationStateStore(),
) {
    val machine = NavigationStateMachine(initial = initial)

    var initialIntentConsumed: Boolean = false
        private set

    fun encode(): String = stateStore.save(machine.state.value)

    fun snapshot(): NavigationSnapshot = machine.state.value

    fun applyPendingExternal(
        input: ExternalNavigationInput?,
        forceNewDelivery: Boolean = false,
    ): ExternalNavigationResult? {
        if (input == null) return null
        if (initialIntentConsumed && !forceNewDelivery) {
            return null
        }
        val mapped = mapper.map(input)
        machine.applyExternal(mapped)
        initialIntentConsumed = true
        return mapped
    }

    fun markNewIntentDelivery() {
        initialIntentConsumed = false
    }

    fun maybePushWelcome(
        showWelcome: Boolean,
        hasPendingExternal: Boolean,
    ) {
        if (!showWelcome || hasPendingExternal) return
        val snap = machine.state.value
        val homeStack = snap.stacks.getValue(TopLevelSection.HOME)
        if (homeStack.any { it is WelcomeKey }) return
        if (snap.activeSection != TopLevelSection.HOME) {
            machine.switchSection(TopLevelSection.HOME)
        }
        machine.push(WelcomeKey)
    }

    fun switchSection(section: TopLevelSection) = machine.switchSection(section)

    fun push(key: OneMemosNavKey) = machine.push(key)

    fun back(): BackResult = machine.back()
}
