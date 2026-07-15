package cc.pscly.onememos.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 六个顶层分区独立返回栈的唯一 Core 实现。
 * app Host 只持有一个实例，不创建平行 adapter 或第二份状态。
 */
class NavigationStateMachine(
    initial: NavigationSnapshot = NavigationSnapshot(),
) : OneMemosNavigator {
    private val _state = MutableStateFlow(initial.normalized())
    val state: StateFlow<NavigationSnapshot> = _state.asStateFlow()

    override fun push(key: OneMemosNavKey) {
        _state.update { snapshot ->
            val section = snapshot.activeSection
            val stack = snapshot.stacks.getValue(section)
            snapshot.copy(
                stacks = snapshot.stacks + (section to (stack + key)),
            ).normalized()
        }
    }

    override fun switchSection(section: TopLevelSection) {
        _state.update { snapshot ->
            if (snapshot.activeSection == section) {
                // 重复选择当前分区：不清栈、不回根
                snapshot
            } else {
                snapshot.copy(activeSection = section).normalized()
            }
        }
    }

    override fun back(): BackResult {
        var result: BackResult = BackResult.Consumed
        _state.update { snapshot ->
            val section = snapshot.activeSection
            val stack = snapshot.stacks.getValue(section)
            when {
                stack.size > 1 -> {
                    result = BackResult.Consumed
                    snapshot.copy(
                        stacks = snapshot.stacks + (section to stack.dropLast(1)),
                    ).normalized()
                }
                section != TopLevelSection.HOME -> {
                    // 非 HOME 根返回：激活并恢复 HOME 栈现场
                    result = BackResult.Consumed
                    snapshot.copy(activeSection = TopLevelSection.HOME).normalized()
                }
                else -> {
                    // HOME 根返回：退出应用
                    result = BackResult.ExitApplication
                    snapshot
                }
            }
        }
        return result
    }

    fun applyExternal(result: ExternalNavigationResult) {
        when (result) {
            is ExternalNavigationResult.Rejected -> Unit
            is ExternalNavigationResult.Accepted -> {
                _state.update { snapshot ->
                    val section = result.section
                    val currentStack = snapshot.stacks.getValue(section)
                    val newStack =
                        when (val mutation = result.mutation) {
                            ExternalStackMutation.ResetToRoot -> listOf(section.root)
                            is ExternalStackMutation.Push -> {
                                when (mutation.duplicatePolicy) {
                                    ExternalNavigationDuplicatePolicy.ALLOW ->
                                        currentStack + mutation.key
                                    ExternalNavigationDuplicatePolicy.IGNORE_IF_TOP -> {
                                        if (currentStack.lastOrNull() == mutation.key) {
                                            currentStack
                                        } else {
                                            currentStack + mutation.key
                                        }
                                    }
                                }
                            }
                        }
                    snapshot.copy(
                        activeSection = section,
                        stacks = snapshot.stacks + (section to newStack),
                    ).normalized()
                }
            }
        }
    }
}
