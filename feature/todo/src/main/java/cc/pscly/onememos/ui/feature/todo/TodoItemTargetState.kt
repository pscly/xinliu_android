package cc.pscly.onememos.ui.feature.todo

import cc.pscly.onememos.domain.model.TodoItem
import cc.pscly.onememos.navigation.TodoItemKey

sealed interface TodoItemTargetState {
    data object Idle : TodoItemTargetState

    data class Loading(val key: TodoItemKey) : TodoItemTargetState

    data class Ready(val key: TodoItemKey, val item: TodoItem) : TodoItemTargetState

    data class Unavailable(
        val key: TodoItemKey,
        val reason: TodoItemUnavailableReason,
    ) : TodoItemTargetState
}

enum class TodoItemUnavailableReason {
    DISABLED,
    DELETED,
    NOT_FOUND_OR_ACCOUNT_MISMATCH,
}
