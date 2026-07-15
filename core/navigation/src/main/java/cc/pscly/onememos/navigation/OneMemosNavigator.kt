package cc.pscly.onememos.navigation

sealed interface BackResult {
    data object Consumed : BackResult

    data object ExitApplication : BackResult
}

interface OneMemosNavigator {
    fun push(key: OneMemosNavKey)

    fun back(): BackResult

    fun switchSection(section: TopLevelSection)
}

sealed interface NavigationCommand {
    data class Push(val key: OneMemosNavKey) : NavigationCommand

    data object Back : NavigationCommand

    data class SwitchSection(val section: TopLevelSection) : NavigationCommand
}
