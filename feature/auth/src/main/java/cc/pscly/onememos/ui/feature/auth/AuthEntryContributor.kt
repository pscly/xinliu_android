package cc.pscly.onememos.ui.feature.auth

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.AuthKey
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator

object AuthEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is AuthKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> {
        val authKey = key as AuthKey
        return NavEntry(key) {
            val viewModel: AuthViewModel = hiltViewModel()
            LaunchedEffect(authKey) {
                viewModel.bind(authKey)
            }
            AuthScreen(
                onBack = { navigator.back() },
                onAuthed = { navigator.back() },
                viewModel = viewModel,
            )
        }
    }
}
