package cc.pscly.onememos.ui.feature.sharecard

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator
import cc.pscly.onememos.navigation.ShareCardKey

object ShareCardEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is ShareCardKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> {
        val shareKey = key as ShareCardKey
        return NavEntry(key) {
            val viewModel: ShareCardViewModel = hiltViewModel()
            LaunchedEffect(shareKey) {
                viewModel.bind(shareKey)
            }
            ShareCardScreen(
                onBack = { navigator.back() },
                viewModel = viewModel,
            )
        }
    }
}
