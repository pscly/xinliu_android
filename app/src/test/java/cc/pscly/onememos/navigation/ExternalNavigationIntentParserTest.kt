package cc.pscly.onememos.navigation

import android.content.Intent
import cc.pscly.onememos.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExternalNavigationIntentParserTest {
    @Test
    fun startEditorUuid_mapsToLegacyEditorExtra() {
        val intent =
            Intent().putExtra(MainActivity.EXTRA_START_EDITOR_UUID, "memos/中文-1")
        val parsed = ExternalNavigationIntentParser.parse(intent)
        assertEquals(ExternalNavigationInput.LegacyEditorExtra("memos/中文-1"), parsed)
    }

    @Test
    fun openTodo_withItemAndOwner_mapsToTodoNotification() {
        val intent =
            Intent(TodoNavigationIntentContract.ACTION_OPEN_TODO)
                .putExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID, "item-1")
                .putExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY, "owner-1")
        val parsed = ExternalNavigationIntentParser.parse(intent)
        assertEquals(
            ExternalNavigationInput.TodoNotification(
                itemId = "item-1",
                expectedOwnerKey = "owner-1",
            ),
            parsed,
        )
    }

    @Test
    fun openTodo_withoutExtras_mapsToOpenTodoRoot() {
        val intent = Intent(TodoNavigationIntentContract.ACTION_OPEN_TODO)
        val parsed = ExternalNavigationIntentParser.parse(intent)
        assertEquals(ExternalNavigationInput.OpenTodoRoot, parsed)
    }

    @Test
    fun openTodo_missingOneExtra_keepsTypedIncompleteInput() {
        val missingOwner =
            Intent(TodoNavigationIntentContract.ACTION_OPEN_TODO)
                .putExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID, "item-1")
        val missingItem =
            Intent(TodoNavigationIntentContract.ACTION_OPEN_TODO)
                .putExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY, "owner-1")
        val blankBoth =
            Intent(TodoNavigationIntentContract.ACTION_OPEN_TODO)
                .putExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID, " ")
                .putExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY, "")

        assertEquals(
            ExternalNavigationInput.TodoNotification(itemId = "item-1", expectedOwnerKey = ""),
            ExternalNavigationIntentParser.parse(missingOwner),
        )
        assertEquals(
            ExternalNavigationInput.TodoNotification(itemId = "", expectedOwnerKey = "owner-1"),
            ExternalNavigationIntentParser.parse(missingItem),
        )
        assertEquals(
            ExternalNavigationInput.TodoNotification(itemId = " ", expectedOwnerKey = ""),
            ExternalNavigationIntentParser.parse(blankBoth),
        )

        val mapper = ExternalNavigationMapper()
        listOf(missingOwner, missingItem, blankBoth).forEach { intent ->
            val result = mapper.map(ExternalNavigationIntentParser.parse(intent)!!)
            assertTrue(result is ExternalNavigationResult.Rejected)
            assertEquals(
                ExternalNavigationRejection.INVALID_ARGUMENT,
                (result as ExternalNavigationResult.Rejected).reason,
            )
        }
    }

    @Test
    fun legacyStartRouteTodo_mapsToLegacyRouteExtra() {
        val intent = Intent().putExtra(MainActivity.EXTRA_START_ROUTE, "todo")
        assertEquals(
            ExternalNavigationInput.LegacyRouteExtra("todo"),
            ExternalNavigationIntentParser.parse(intent),
        )
    }

    @Test
    fun unknownRoute_stillParsedAsLegacyRouteExtra() {
        val intent = Intent().putExtra(MainActivity.EXTRA_START_ROUTE, "settings")
        val parsed = ExternalNavigationIntentParser.parse(intent)
        assertEquals(ExternalNavigationInput.LegacyRouteExtra("settings"), parsed)
        val mapped = ExternalNavigationMapper().map(parsed!!)
        assertTrue(mapped is ExternalNavigationResult.Rejected)
        assertEquals(
            ExternalNavigationRejection.UNKNOWN_VALUE,
            (mapped as ExternalNavigationResult.Rejected).reason,
        )
    }

    @Test
    fun emptyIntent_returnsNull() {
        assertNull(ExternalNavigationIntentParser.parse(Intent()))
        assertNull(ExternalNavigationIntentParser.parse(null))
    }

    @Test
    fun editorExtraTakesPriorityOverStartRoute() {
        val intent =
            Intent()
                .putExtra(MainActivity.EXTRA_START_EDITOR_UUID, "uuid-1")
                .putExtra(MainActivity.EXTRA_START_ROUTE, "todo")
        assertEquals(
            ExternalNavigationInput.LegacyEditorExtra("uuid-1"),
            ExternalNavigationIntentParser.parse(intent),
        )
    }
}
