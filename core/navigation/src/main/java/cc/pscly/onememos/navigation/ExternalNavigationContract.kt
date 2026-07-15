package cc.pscly.onememos.navigation

sealed interface ExternalNavigationInput {
    data class SharedMemo(val uuid: String) : ExternalNavigationInput

    data class TodoNotification(
        val itemId: String,
        val expectedOwnerKey: String,
    ) : ExternalNavigationInput

    data object OpenTodoRoot : ExternalNavigationInput

    data class LegacyEditorExtra(val uuid: String) : ExternalNavigationInput

    data class LegacyRouteExtra(val value: String) : ExternalNavigationInput
}

enum class ExternalNavigationDuplicatePolicy {
    ALLOW,
    IGNORE_IF_TOP,
}

sealed interface ExternalStackMutation {
    data object ResetToRoot : ExternalStackMutation

    data class Push(
        val key: OneMemosNavKey,
        val duplicatePolicy: ExternalNavigationDuplicatePolicy =
            ExternalNavigationDuplicatePolicy.ALLOW,
    ) : ExternalStackMutation
}

sealed interface ExternalNavigationResult {
    data class Accepted(
        val section: TopLevelSection,
        val mutation: ExternalStackMutation,
    ) : ExternalNavigationResult

    data class Rejected(val reason: ExternalNavigationRejection) : ExternalNavigationResult
}

enum class ExternalNavigationRejection {
    EMPTY_VALUE,
    UNKNOWN_VALUE,
    INVALID_ARGUMENT,
}

object TodoNavigationIntentContract {
    const val ACTION_OPEN_TODO = "cc.pscly.onememos.action.OPEN_TODO"
    const val EXTRA_TODO_ITEM_ID = "cc.pscly.onememos.extra.TODO_ITEM_ID"
    const val EXTRA_TODO_OWNER_KEY = "cc.pscly.onememos.extra.TODO_OWNER_KEY"
}
