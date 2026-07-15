package cc.pscly.onememos.navigation

import androidx.navigation3.runtime.NavEntry

interface FeatureEntryHost {
    fun openDrawer()
}

interface FeatureEntryContributor {
    fun owns(key: OneMemosNavKey): Boolean

    fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey>
}
