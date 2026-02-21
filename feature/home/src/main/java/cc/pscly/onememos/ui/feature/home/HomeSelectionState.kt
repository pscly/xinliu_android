package cc.pscly.onememos.ui.feature.home

data class HomeSelectionState(
    val selectedIds: Set<String> = emptySet(),
) {
    val selectionMode: Boolean
        get() = selectedIds.isNotEmpty()

    fun enter(id: String): HomeSelectionState {
        val stableId = id.trim()
        if (stableId.isBlank()) return this
        return copy(selectedIds = setOf(stableId))
    }

    fun start(id: String): HomeSelectionState = enter(id)

    fun toggle(id: String): HomeSelectionState {
        val stableId = id.trim()
        if (stableId.isBlank()) return this

        val nextSelectedIds = if (stableId in selectedIds) selectedIds - stableId else selectedIds + stableId
        return copy(selectedIds = nextSelectedIds)
    }

    fun clear(): HomeSelectionState = if (selectedIds.isEmpty()) this else copy(selectedIds = emptySet())

    fun exit(): HomeSelectionState = clear()
}
