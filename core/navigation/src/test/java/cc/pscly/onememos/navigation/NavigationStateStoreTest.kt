package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationStateStoreTest {
    private val store = JsonNavigationStateStore()

    @Test
    fun saveRestore_roundTripDifferentDepthStacks() {
        val snapshot =
            NavigationSnapshot(
                activeSection = TopLevelSection.TODO,
                stacks =
                    mapOf(
                        TopLevelSection.HOME to listOf(HomeKey, EditorKey("a"), ShareCardKey("s")),
                        TopLevelSection.COLLECTIONS to listOf(CollectionsKey),
                        TopLevelSection.TODO to listOf(TodoKey, TodoItemKey("i", "o")),
                        TopLevelSection.PROFILE to listOf(ProfileKey, EditorKey("p")),
                        TopLevelSection.ARCHIVED to listOf(ArchivedKey),
                        TopLevelSection.SETTINGS to
                            listOf(SettingsHubKey, AccountSyncSettingsKey, AccountManagementSettingsKey),
                    ),
            ).normalized()
        val encoded = store.save(snapshot)
        val restored = store.restore(encoded)
        assertTrue(restored is NavigationRestoreResult.Restored)
        assertEquals(snapshot, (restored as NavigationRestoreResult.Restored).snapshot)
    }

    @Test
    fun restore_rejectsMalformedUnknownKeyBadRootAndPartialDamage() {
        assertTrue(
            store.restore("not-json") is NavigationRestoreResult.Rejected,
        )
        assertTrue(
            store.restore("") is NavigationRestoreResult.Rejected,
        )

        val good = store.save(NavigationSnapshot().normalized())
        // inject unknown key into HOME stack
        val withUnknown =
            good.replace(
                store.save(
                    NavigationSnapshot(
                        stacks =
                            mapOf(
                                TopLevelSection.HOME to listOf(HomeKey),
                            ) +
                                TopLevelSection.entries
                                    .filter { it != TopLevelSection.HOME }
                                    .associateWith { listOf(it.root) },
                    ).normalized(),
                ).let { store.save(NavigationSnapshot().normalized()) },
                good,
            )
        // Craft invalid payload with unknown key encoding
        val codec = JsonNavKeyCodec()
        val wireUnknown =
            """
            {"activeSection":"HOME","stacks":{
              "HOME":["${codec.encode(HomeKey).replace("\"", "\\\"")}"],
              "COLLECTIONS":["${codec.encode(CollectionsKey).replace("\"", "\\\"")}"],
              "TODO":["${codec.encode(TodoKey).replace("\"", "\\\"")}"],
              "PROFILE":["${codec.encode(ProfileKey).replace("\"", "\\\"")}"],
              "ARCHIVED":["${codec.encode(ArchivedKey).replace("\"", "\\\"")}"],
              "SETTINGS":["${codec.encode(SettingsHubKey).replace("\"", "\\\"")}"]
            }}
            """.trimIndent()
        // simpler: decode path for unknown type
        val badKeyJson =
            """
            {"activeSection":"HOME","stacks":{
              "HOME":["{\"type\":\"cc.pscly.onememos.navigation.UnknownKey\"}"],
              "COLLECTIONS":["${escape(codec.encode(CollectionsKey))}"],
              "TODO":["${escape(codec.encode(TodoKey))}"],
              "PROFILE":["${escape(codec.encode(ProfileKey))}"],
              "ARCHIVED":["${escape(codec.encode(ArchivedKey))}"],
              "SETTINGS":["${escape(codec.encode(SettingsHubKey))}"]
            }}
            """.trimIndent()
        val unknown = store.restore(badKeyJson)
        assertTrue(unknown is NavigationRestoreResult.Rejected)
        assertEquals(
            NavigationRestoreRejection.UNKNOWN_KEY,
            (unknown as NavigationRestoreResult.Rejected).reason,
        )

        // bad root: HOME stack starts with TodoKey
        val badRoot =
            """
            {"activeSection":"HOME","stacks":{
              "HOME":["${escape(codec.encode(TodoKey))}"],
              "COLLECTIONS":["${escape(codec.encode(CollectionsKey))}"],
              "TODO":["${escape(codec.encode(TodoKey))}"],
              "PROFILE":["${escape(codec.encode(ProfileKey))}"],
              "ARCHIVED":["${escape(codec.encode(ArchivedKey))}"],
              "SETTINGS":["${escape(codec.encode(SettingsHubKey))}"]
            }}
            """.trimIndent()
        val badRootResult = store.restore(badRoot)
        assertTrue(badRootResult is NavigationRestoreResult.Rejected)
        assertEquals(
            NavigationRestoreRejection.INVALID_STRUCTURE,
            (badRootResult as NavigationRestoreResult.Rejected).reason,
        )

        // missing one section entirely
        val missingSection =
            """
            {"activeSection":"HOME","stacks":{
              "HOME":["${escape(codec.encode(HomeKey))}"],
              "COLLECTIONS":["${escape(codec.encode(CollectionsKey))}"],
              "TODO":["${escape(codec.encode(TodoKey))}"],
              "PROFILE":["${escape(codec.encode(ProfileKey))}"],
              "ARCHIVED":["${escape(codec.encode(ArchivedKey))}"]
            }}
            """.trimIndent()
        val missing = store.restore(missingSection)
        assertTrue(missing is NavigationRestoreResult.Rejected)
        assertEquals(
            NavigationRestoreRejection.INVALID_STRUCTURE,
            (missing as NavigationRestoreResult.Rejected).reason,
        )
    }

    private fun escape(encoded: String): String =
        encoded.replace("\\", "\\\\").replace("\"", "\\\"")
}
