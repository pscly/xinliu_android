package cc.pscly.onememos.ui.feature.collections

import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.CollectionsKey
import cc.pscly.onememos.navigation.EditorKey
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator

object CollectionsEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is CollectionsKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> =
        NavEntry(key) {
            CollectionsScreen(
                onOpenDrawer = host::openDrawer,
                onOpenMemo = { uuid -> navigator.push(EditorKey(uuid = uuid)) },
            )
        }
}
