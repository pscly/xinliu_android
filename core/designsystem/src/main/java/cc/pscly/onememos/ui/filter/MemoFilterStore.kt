package cc.pscly.onememos.ui.filter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoFilterStore @Inject constructor() {
    private val _state = MutableStateFlow(MemoFilter())
    val state: StateFlow<MemoFilter> = _state.asStateFlow()

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setTagMatchMode(mode: TagMatchMode) {
        _state.update { it.copy(tagMatchMode = mode) }
    }

    fun setExcludeTags(enabled: Boolean) {
        _state.update { it.copy(excludeTags = enabled) }
    }

    fun toggleTag(tag: String) {
        _state.update { current ->
            val next =
                if (current.selectedTags.contains(tag)) {
                    current.selectedTags - tag
                } else {
                    current.selectedTags + tag
                }
            current.copy(selectedTags = next)
        }
    }

    fun setSelectedTags(tags: Set<String>) {
        _state.update { it.copy(selectedTags = tags) }
    }

    fun setFilter(filter: MemoFilter) {
        _state.value = filter
    }

    fun clear() {
        _state.value = MemoFilter()
    }
}
