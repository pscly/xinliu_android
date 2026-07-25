package cc.pscly.onememos.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XinliuRelationMarkerTest {
    @Test
    fun parse_and_upsert_todo_and_fav_independently() {
        val body = "你好世界\n第二行"
        val withTodo =
            XinliuRelationMarker.upsert(
                body,
                XinliuRelationMarker.Kind.TODO,
                "2026-07-20",
                "todo-1",
            )
        assertTrue(withTodo.contains("<!-- xinliu:2026-07-20:TODO id=todo-1 -->"))
        val withBoth =
            XinliuRelationMarker.upsert(
                withTodo,
                XinliuRelationMarker.Kind.FAV,
                "2026-07-21",
                "fav-1",
            )
        assertEquals("todo-1", XinliuRelationMarker.find(withBoth, XinliuRelationMarker.Kind.TODO)!!.id)
        assertEquals("fav-1", XinliuRelationMarker.find(withBoth, XinliuRelationMarker.Kind.FAV)!!.id)

        val strippedTodo = XinliuRelationMarker.strip(withBoth, XinliuRelationMarker.Kind.TODO)
        assertNull(XinliuRelationMarker.find(strippedTodo, XinliuRelationMarker.Kind.TODO))
        assertEquals("fav-1", XinliuRelationMarker.find(strippedTodo, XinliuRelationMarker.Kind.FAV)!!.id)
    }

    @Test
    fun stripAllForRead_removes_markers_from_preview_source() {
        val raw =
            "正文\n<!-- xinliu:2026-07-20:TODO id=abc -->\n<!-- xinliu:2026-07-20:FAV id=x -->"
        val read = XinliuRelationMarker.stripAllForRead(raw)
        assertEquals("正文", read)
        assertFalse(read.contains("xinliu"))
    }

    @Test
    fun upsert_replaces_same_kind() {
        val once =
            XinliuRelationMarker.upsert("a", XinliuRelationMarker.Kind.TODO, "2026-01-01", "old")
        val twice =
            XinliuRelationMarker.upsert(once, XinliuRelationMarker.Kind.TODO, "2026-02-02", "new")
        assertEquals(1, XinliuRelationMarker.parseAll(twice).count { it.kind == XinliuRelationMarker.Kind.TODO })
        assertEquals("new", XinliuRelationMarker.find(twice, XinliuRelationMarker.Kind.TODO)!!.id)
    }
}
