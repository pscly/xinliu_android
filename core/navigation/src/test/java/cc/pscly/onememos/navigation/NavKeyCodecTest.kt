package cc.pscly.onememos.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavKeyCodecTest {
    private val codec = JsonNavKeyCodec()

    @Test
    fun roundTrip_allConcreteKeys() {
        val keys =
            listOf(
                HomeKey,
                CollectionsKey,
                TodoKey,
                TodoItemKey(itemId = "item-1", expectedOwnerKey = "owner-1"),
                TodoItemKey(itemId = "memos/中文 item", expectedOwnerKey = "owner/key"),
                ProfileKey,
                ArchivedKey,
                SettingsHubKey,
                WelcomeKey,
                EditorKey(uuid = null),
                EditorKey(uuid = "memos/123 with space"),
                EditorKey(uuid = "uuid/中文"),
                ShareCardKey(uuid = "share/card 中文"),
                AuthKey(mode = null),
                AuthKey(mode = AuthMode.CUSTOM_TOKEN),
                AccountSyncSettingsKey,
                AccountManagementSettingsKey,
                AdvancedSyncSettingsKey,
                RecordEditingSettingsKey,
                ReminderCalendarSettingsKey,
                StorageOfflineSettingsKey,
                AppearanceInteractionSettingsKey,
                AboutAdvancedSettingsKey,
            )
        keys.forEach { key ->
            val encoded = codec.encode(key)
            val decoded = codec.decode(encoded)
            assertTrue("decode failed for $key: $decoded", decoded is NavKeyDecodeResult.Success)
            assertEquals(key, (decoded as NavKeyDecodeResult.Success).key)
        }
    }

    @Test
    fun todoItemKey_blankOrUntrimmedReturnsInvalidArgument() {
        val blankItem = """{"type":"cc.pscly.onememos.navigation.TodoItemKey","itemId":" ","expectedOwnerKey":"owner"}"""
        val blankOwner = """{"type":"cc.pscly.onememos.navigation.TodoItemKey","itemId":"item","expectedOwnerKey":" owner "}"""
        val emptyItem = """{"type":"cc.pscly.onememos.navigation.TodoItemKey","itemId":"","expectedOwnerKey":"owner"}"""

        assertEquals(
            NavKeyDecodeRejection.INVALID_ARGUMENT,
            (codec.decode(blankItem) as NavKeyDecodeResult.Rejected).reason,
        )
        assertEquals(
            NavKeyDecodeRejection.INVALID_ARGUMENT,
            (codec.decode(blankOwner) as NavKeyDecodeResult.Rejected).reason,
        )
        assertEquals(
            NavKeyDecodeRejection.INVALID_ARGUMENT,
            (codec.decode(emptyItem) as NavKeyDecodeResult.Rejected).reason,
        )
    }

    @Test
    fun unknownType_missingFields_andRawRoute_areRejected() {
        val unknown =
            codec.decode("""{"type":"cc.pscly.onememos.navigation.UnknownKey"}""")
        assertTrue(unknown is NavKeyDecodeResult.Rejected)
        assertEquals(
            NavKeyDecodeRejection.UNKNOWN_TYPE,
            (unknown as NavKeyDecodeResult.Rejected).reason,
        )

        val missing =
            codec.decode("""{"type":"cc.pscly.onememos.navigation.ShareCardKey"}""")
        assertTrue(missing is NavKeyDecodeResult.Rejected)
        assertEquals(
            NavKeyDecodeRejection.MALFORMED_PAYLOAD,
            (missing as NavKeyDecodeResult.Rejected).reason,
        )

        val rawRoute = codec.decode("home")
        assertTrue(rawRoute is NavKeyDecodeResult.Rejected)
        assertEquals(
            NavKeyDecodeRejection.MALFORMED_PAYLOAD,
            (rawRoute as NavKeyDecodeResult.Rejected).reason,
        )
    }

    @Test
    fun contracts_compileAndTodoRootIsTodoKey() {
        assertEquals(TodoKey, TopLevelSection.TODO.root)
        val input: ExternalNavigationInput = ExternalNavigationInput.OpenTodoRoot
        val mutation: ExternalStackMutation =
            ExternalStackMutation.Push(
                key = TodoItemKey("i", "o"),
                duplicatePolicy = ExternalNavigationDuplicatePolicy.IGNORE_IF_TOP,
            )
        val accepted: ExternalNavigationResult =
            ExternalNavigationResult.Accepted(
                section = TopLevelSection.TODO,
                mutation = mutation,
            )
        val rejected: ExternalNavigationResult =
            ExternalNavigationResult.Rejected(ExternalNavigationRejection.UNKNOWN_VALUE)
        assertTrue(input is ExternalNavigationInput.OpenTodoRoot)
        assertTrue(accepted is ExternalNavigationResult.Accepted)
        assertTrue(rejected is ExternalNavigationResult.Rejected)
        assertEquals("cc.pscly.onememos.action.OPEN_TODO", TodoNavigationIntentContract.ACTION_OPEN_TODO)

        val navigator: OneMemosNavigator =
            object : OneMemosNavigator {
                override fun push(key: OneMemosNavKey) = Unit

                override fun back(): BackResult = BackResult.Consumed

                override fun switchSection(section: TopLevelSection) = Unit
            }
        assertEquals(BackResult.Consumed, navigator.back())

        val host =
            object : FeatureEntryHost {
                override fun openDrawer() = Unit
            }
        // compile-only touch
        host.openDrawer()
    }
}
