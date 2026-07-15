package cc.pscly.onememos.ui.feature.todo

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator
import cc.pscly.onememos.navigation.TodoItemKey
import cc.pscly.onememos.navigation.TodoKey

object TodoEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is TodoKey || key is TodoItemKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> =
        when (key) {
            is TodoKey ->
                NavEntry(key) {
                    TodoScreen(onOpenDrawer = host::openDrawer)
                }
            is TodoItemKey ->
                NavEntry(key) {
                    val viewModel: TodoViewModel = hiltViewModel()
                    LaunchedEffect(key) {
                        viewModel.bind(key)
                    }
                    TodoScreen(
                        onOpenDrawer = host::openDrawer,
                        targetKey = key,
                        onTargetDismiss = {
                            viewModel.clearTarget()
                            navigator.back()
                        },
                        viewModel = viewModel,
                    )
                }
            else -> error("TodoEntryContributor does not own $key")
        }
}
