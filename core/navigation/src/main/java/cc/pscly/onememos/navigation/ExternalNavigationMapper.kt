package cc.pscly.onememos.navigation

class ExternalNavigationMapper {
    fun map(input: ExternalNavigationInput): ExternalNavigationResult =
        when (input) {
            is ExternalNavigationInput.SharedMemo -> input.uuid.editorResult()
            is ExternalNavigationInput.LegacyEditorExtra -> input.uuid.editorResult()
            is ExternalNavigationInput.TodoNotification -> input.todoResult()
            ExternalNavigationInput.OpenTodoRoot ->
                ExternalNavigationResult.Accepted(
                    section = TopLevelSection.TODO,
                    mutation = ExternalStackMutation.ResetToRoot,
                )
        }

    private fun String.editorResult(): ExternalNavigationResult {
        val uuid = trim()
        if (uuid.isEmpty()) {
            return ExternalNavigationResult.Rejected(ExternalNavigationRejection.INVALID_ARGUMENT)
        }
        return ExternalNavigationResult.Accepted(
            section = TopLevelSection.HOME,
            mutation =
                ExternalStackMutation.Push(
                    key = EditorKey(uuid = uuid),
                    duplicatePolicy = ExternalNavigationDuplicatePolicy.ALLOW,
                ),
        )
    }

    private fun ExternalNavigationInput.TodoNotification.todoResult(): ExternalNavigationResult {
        val id = itemId.trim()
        val owner = expectedOwnerKey.trim()
        if (id.isEmpty() || owner.isEmpty()) {
            return ExternalNavigationResult.Rejected(ExternalNavigationRejection.INVALID_ARGUMENT)
        }
        return try {
            ExternalNavigationResult.Accepted(
                section = TopLevelSection.TODO,
                mutation =
                    ExternalStackMutation.Push(
                        key = TodoItemKey(itemId = id, expectedOwnerKey = owner),
                        duplicatePolicy = ExternalNavigationDuplicatePolicy.IGNORE_IF_TOP,
                    ),
            )
        } catch (_: IllegalArgumentException) {
            ExternalNavigationResult.Rejected(ExternalNavigationRejection.INVALID_ARGUMENT)
        }
    }
}
