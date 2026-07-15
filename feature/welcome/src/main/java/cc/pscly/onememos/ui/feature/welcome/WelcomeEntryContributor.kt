package cc.pscly.onememos.ui.feature.welcome

import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.AuthKey
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator
import cc.pscly.onememos.navigation.WelcomeKey

object WelcomeEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is WelcomeKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> =
        NavEntry(key) {
            WelcomeScreen(
                onEnterLocal = { navigator.back() },
                onGoBindServer = { navigator.push(AuthKey()) },
            )
        }
}
