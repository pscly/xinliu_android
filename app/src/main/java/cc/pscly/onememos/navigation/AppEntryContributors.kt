package cc.pscly.onememos.navigation

import cc.pscly.onememos.ui.feature.auth.AuthEntryContributor
import cc.pscly.onememos.ui.feature.collections.CollectionsEntryContributor
import cc.pscly.onememos.ui.feature.editor.EditorEntryContributor
import cc.pscly.onememos.ui.feature.home.HomeEntryContributor
import cc.pscly.onememos.ui.feature.profile.ProfileEntryContributor
import cc.pscly.onememos.ui.feature.settings.SettingsEntryContributor
import cc.pscly.onememos.ui.feature.sharecard.ShareCardEntryContributor
import cc.pscly.onememos.ui.feature.todo.TodoEntryContributor
import cc.pscly.onememos.ui.feature.welcome.WelcomeEntryContributor

/**
 * app 组合根显式聚合全部 entry contributor。
 * 启动时必须验证每个合法键恰有一个 owner，禁止静默覆盖。
 */
val appEntryContributors: List<FeatureEntryContributor> =
    listOf(
        HomeEntryContributor,
        CollectionsEntryContributor,
        TodoEntryContributor,
        ProfileEntryContributor,
        EditorEntryContributor,
        ShareCardEntryContributor,
        AuthEntryContributor,
        WelcomeEntryContributor,
        SettingsEntryContributor,
    )

fun resolveEntryContributor(
    key: OneMemosNavKey,
    contributors: List<FeatureEntryContributor> = appEntryContributors,
): FeatureEntryContributor {
    val owners = contributors.filter { it.owns(key) }
    require(owners.size == 1) {
        "导航键 $key 必须恰有一个 owner，实际 owners=${owners.map { it::class.simpleName }}"
    }
    return owners.single()
}

fun validateEntryContributors(
    keys: List<OneMemosNavKey>,
    contributors: List<FeatureEntryContributor> = appEntryContributors,
) {
    keys.forEach { key ->
        resolveEntryContributor(key = key, contributors = contributors)
    }
}
