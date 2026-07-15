package cc.pscly.onememos.ui.feature.home

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import cc.pscly.onememos.navigation.ArchivedKey
import cc.pscly.onememos.navigation.EditorKey
import cc.pscly.onememos.navigation.FeatureEntryContributor
import cc.pscly.onememos.navigation.FeatureEntryHost
import cc.pscly.onememos.navigation.HomeKey
import cc.pscly.onememos.navigation.OneMemosNavKey
import cc.pscly.onememos.navigation.OneMemosNavigator
import cc.pscly.onememos.navigation.ShareCardKey
import cc.pscly.onememos.navigation.AuthKey
import cc.pscly.onememos.navigation.AuthMode

object HomeEntryContributor : FeatureEntryContributor {
    override fun owns(key: OneMemosNavKey): Boolean = key is HomeKey || key is ArchivedKey

    override fun entry(
        key: OneMemosNavKey,
        navigator: OneMemosNavigator,
        host: FeatureEntryHost,
    ): NavEntry<OneMemosNavKey> {
        val mode =
            when (key) {
                is HomeKey -> HomeScreenMode.ACTIVE
                is ArchivedKey -> HomeScreenMode.ARCHIVED
                else -> error("HomeEntryContributor does not own $key")
            }
        val title = if (mode == HomeScreenMode.ACTIVE) "随笔" else "归档"
        return NavEntry(key) {
            HomeScreen(
                title = title,
                mode = mode,
                onOpenDrawer = host::openDrawer,
                onOpenAuth = { navigator.push(AuthKey(mode = AuthMode.CUSTOM_TOKEN)) },
                onCreateMemo = { navigator.push(EditorKey(uuid = null)) },
                onOpenMemo = { uuid -> navigator.push(EditorKey(uuid = uuid)) },
                onOpenShareCard = { uuid -> navigator.push(ShareCardKey(uuid = uuid)) },
            )
        }
    }
}
