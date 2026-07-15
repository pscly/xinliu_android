package cc.pscly.onememos.ui.feature.profile

import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.EditorKey
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator
import cc.pscly.onememos.navigation.ProfileKey

object ProfileEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is ProfileKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> =
        NavEntry(key) {
            ProfileScreen(
                onOpenDrawer = host::openDrawer,
                onOpenMemo = { uuid -> navigator.push(EditorKey(uuid = uuid)) },
            )
        }
}
