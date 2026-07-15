package cc.pscly.onememos.worker

import android.content.Intent
import cc.pscly.onememos.navigation.TodoNavigationIntentContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TodoReminderNavigationIntentTest {
    @Test
    fun factory_writesActionItemOwnerAndStableData() {
        val context = RuntimeEnvironment.getApplication()
        val intent =
            TodoReminderLaunchIntentFactory.createOpenTodoIntent(
                context = context,
                itemId = "item-1",
                ownerKey = "owner-a",
            )
        assertEquals(TodoNavigationIntentContract.ACTION_OPEN_TODO, intent.action)
        assertEquals(
            "item-1",
            intent.getStringExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID),
        )
        assertEquals(
            "owner-a",
            intent.getStringExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY),
        )
        assertNotNull(intent.data)
        assertEquals("onememos", intent.data!!.scheme)
        assertEquals("todo", intent.data!!.authority)
        assertEquals("/owner-a/item-1", intent.data!!.path)
    }

    @Test
    fun factory_withoutItem_onlyWritesAction() {
        val context = RuntimeEnvironment.getApplication()
        val intent = TodoReminderLaunchIntentFactory.createOpenTodoIntent(context)
        assertEquals(TodoNavigationIntentContract.ACTION_OPEN_TODO, intent.action)
        assertFalse(intent.hasExtra(TodoNavigationIntentContract.EXTRA_TODO_ITEM_ID))
        assertFalse(intent.hasExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY))
        assertNull(intent.data)
    }

    @Test
    fun differentOwners_produceDifferentPendingIntentIdentity() {
        val context = RuntimeEnvironment.getApplication()
        val a =
            TodoReminderLaunchIntentFactory.createOpenTodoIntent(
                context = context,
                itemId = "same-item",
                ownerKey = "owner-a",
            )
        val b =
            TodoReminderLaunchIntentFactory.createOpenTodoIntent(
                context = context,
                itemId = "same-item",
                ownerKey = "owner-b",
            )
        assertNotEquals(a.data, b.data)
        assertNotEquals(
            a.getStringExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY),
            b.getStringExtra(TodoNavigationIntentContract.EXTRA_TODO_OWNER_KEY),
        )
    }

    @Test
    fun uniqueWorkName_includesOwner() {
        val a =
            TodoReminderNotifyWorker.uniqueWorkName(
                ownerKey = "owner-a",
                itemId = "item-1",
                triggerAtMs = 1000L,
                minutes = 5,
            )
        val b =
            TodoReminderNotifyWorker.uniqueWorkName(
                ownerKey = "owner-b",
                itemId = "item-1",
                triggerAtMs = 1000L,
                minutes = 5,
            )
        assertNotEquals(a, b)
        assertTrue(a.contains("owner-a"))
        assertTrue(b.contains("owner-b"))
    }

    @Test
    fun notifyWorker_keyOwnerConstantExists() {
        assertEquals("ownerKey", TodoReminderNotifyWorker.KEY_OWNER_KEY)
    }
}
