package cc.pscly.onememos.ui.feature.editor

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.EditorKey
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator
import cc.pscly.onememos.navigation.ShareCardKey

object EditorEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is EditorKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> {
        val editorKey = key as EditorKey
        return NavEntry(key) {
            val viewModel: EditorViewModel = hiltViewModel()
            LaunchedEffect(editorKey) {
                viewModel.bind(editorKey)
            }
            EditorScreen(
                onBack = { navigator.back() },
                onOpenShareCard = { uuid -> navigator.push(ShareCardKey(uuid = uuid)) },
                viewModel = viewModel,
            )
        }
    }
}
