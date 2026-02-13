package cc.pscly.onememos.collections

import cc.pscly.onememos.core.network.FlowSyncPullResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FlowSyncCollectionsParsingTest {
    private val gson = Gson()

    @Test
    fun parsePull_folderAndNoteRef() {
        val json =
            """
            {
              "cursor": 0,
              "next_cursor": 10,
              "has_more": false,
              "changes": {
                "todo_lists": [],
                "todo_items": [],
                "todo_occurrences": [],
                "collection_items": [
                  {
                    "id": "c1",
                    "item_type": "folder",
                    "parent_id": null,
                    "name": "cooking",
                    "color": "#3FA45B",
                    "ref_type": null,
                    "ref_id": null,
                    "sort_order": 10,
                    "client_updated_at_ms": 1730000000000,
                    "created_at": "2026-02-12T00:00:00Z",
                    "updated_at": "2026-02-12T00:00:00Z",
                    "deleted_at": null
                  },
                  {
                    "id": "c2",
                    "item_type": "note_ref",
                    "parent_id": "c1",
                    "name": "",
                    "color": null,
                    "ref_type": "memos_memo",
                    "ref_id": "memos/123",
                    "sort_order": 20,
                    "client_updated_at_ms": 1730000000500,
                    "created_at": "2026-02-12T00:00:00Z",
                    "updated_at": "2026-02-12T00:00:00Z",
                    "deleted_at": null
                  }
                ]
              }
            }
            """.trimIndent()

        val resp = gson.fromJson(json, FlowSyncPullResponse::class.java)
        assertEquals(10L, resp.nextCursor)
        assertEquals(2, resp.changes.collectionItems.size)

        val folder = resp.changes.collectionItems[0]
        assertEquals("c1", folder.id)
        assertEquals("folder", folder.itemType)
        assertEquals("cooking", folder.name)
        assertEquals(10, folder.sortOrder)

        val note = resp.changes.collectionItems[1]
        assertEquals("note_ref", note.itemType)
        assertEquals("memos_memo", note.refType)
        assertEquals("memos/123", note.refId)
    }

    @Test
    fun parsePull_tombstone() {
        val json =
            """
            {
              "cursor": 10,
              "next_cursor": 11,
              "has_more": false,
              "changes": {
                "todo_lists": [],
                "todo_items": [],
                "todo_occurrences": [],
                "collection_items": [
                  {
                    "id": "c1",
                    "item_type": "folder",
                    "parent_id": null,
                    "name": "cooking",
                    "color": null,
                    "ref_type": null,
                    "ref_id": null,
                    "sort_order": 10,
                    "client_updated_at_ms": 1730000000600,
                    "created_at": "2026-02-12T00:00:00Z",
                    "updated_at": "2026-02-12T00:00:00Z",
                    "deleted_at": "2026-02-12T00:01:00Z"
                  }
                ]
              }
            }
            """.trimIndent()

        val resp = gson.fromJson(json, FlowSyncPullResponse::class.java)
        val item = resp.changes.collectionItems.single()
        assertNotNull(item.deletedAt)
        assertEquals("2026-02-12T00:01:00Z", item.deletedAt)
    }
}
