package cc.pscly.onememos.navigation

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

private const val TAG = "AppNavigationHost"

/**
 * Navigation 3 主机：
 * - 持有唯一 [NavigationStateMachine]
 * - 只把 active 分区栈交给 [NavDisplay]
 * - 通过 [JsonNavigationStateStore] 保存六栈
 * - 消费外部输入并支持欢迎页优先级
 */
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

    NavDisplay(
        entries = decoratedEntries,
        onBack = ::handleBack,
    )
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
