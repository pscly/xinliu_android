package cc.pscly.onememos.navigation

import kotlinx.serialization.Serializable

@Serializable
data class NavigationSnapshot(
    val activeSection: TopLevelSection = TopLevelSection.HOME,
    val stacks: Map<TopLevelSection, List<OneMemosNavKey>> =
        TopLevelSection.entries.associateWith { listOf(it.root) },
)

fun NavigationSnapshot.normalized(): NavigationSnapshot {
    val fixed =
        TopLevelSection.entries.associateWith { section ->
            val stack = stacks[section].orEmpty()
            when {
                stack.isEmpty() -> listOf(section.root)
                stack.first() != section.root -> listOf(section.root)
                else -> stack
            }
        }
    val active =
        if (activeSection in fixed) {
            activeSection
        } else {
            TopLevelSection.HOME
        }
    return NavigationSnapshot(activeSection = active, stacks = fixed)
}

fun NavigationSnapshot.isStructurallyValid(): Boolean {
    if (activeSection !in TopLevelSection.entries) return false
    for (section in TopLevelSection.entries) {
        val stack = stacks[section] ?: return false
        if (stack.isEmpty()) return false
        if (stack.first() != section.root) return false
    }
    return stacks.keys == TopLevelSection.entries.toSet()
}

fun freshNavigationSnapshot(): NavigationSnapshot = NavigationSnapshot().normalized()
