package cc.pscly.onememos.core.network

import cc.pscly.onememos.core.network.dto.CreateMemoRequestDto
import cc.pscly.onememos.core.network.dto.MemoDto
import cc.pscly.onememos.core.network.dto.MemoLocationDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上游 memos v0.31.0 协议对齐的契约测试。
 *
 * 目的：把“字段名/路径常量”钉在测试里，避免以后升级时凭记忆改错。
 * 数据来源：v0.31.0 `proto/gen/openapi.yaml` 与 `proto/api/v1/memo_service.proto`。
 */
class MemosDtoContractTest {
    private val gson = Gson()

    @Test
    fun memoDto_parsesUpstream031Fields() {
        val json =
            """
            {
              "name": "memos/abc",
              "state": "NORMAL",
              "content": "hi",
              "visibility": "PRIVATE",
              "tags": ["a", "b"],
              "snippet": "hi",
              "pinned": true,
              "property": {
                "hasLink": false,
                "hasTaskList": true,
                "hasCode": false,
                "hasIncompleteTasks": true,
                "title": "标题"
              },
              "location": {"placeholder": "公司", "latitude": 22.5431, "longitude": 114.0579},
              "parent": "memos/parent",
              "relations": [
                {
                  "memo": {"name": "memos/abc"},
                  "relatedMemo": {"name": "memos/other", "snippet": "s"},
                  "type": "REFERENCE"
                }
              ],
              "reactions": [
                {"name": "memos/abc/reactions/r1", "creator": "users/1", "reactionType": "👍"}
              ]
            }
            """.trimIndent()

        val dto = gson.fromJson(json, MemoDto::class.java)

        assertEquals("memos/abc", dto.name)
        assertEquals("NORMAL", dto.state)
        assertEquals("PRIVATE", dto.visibility)
        assertEquals(listOf("a", "b"), dto.tags)
        assertEquals("hi", dto.snippet)
        assertEquals(true, dto.pinned)
        assertEquals("标题", dto.property?.title)
        assertEquals(true, dto.property?.hasTaskList)
        assertEquals("公司", dto.location?.placeholder)
        assertEquals(22.5431, dto.location?.latitude ?: 0.0, 1e-9)
        assertEquals(114.0579, dto.location?.longitude ?: 0.0, 1e-9)
        assertEquals("memos/parent", dto.parent)
        assertEquals("REFERENCE", dto.relations?.firstOrNull()?.type)
        assertEquals("memos/other", dto.relations?.firstOrNull()?.relatedMemo?.name)
        assertEquals("👍", dto.reactions?.firstOrNull()?.reactionType)
    }

    @Test
    fun createMemoRequest_omitsNullLocationToStayCompatibleWithOldServers() {
        val json =
            gson.toJson(
                CreateMemoRequestDto(
                    state = "NORMAL",
                    content = "hello",
                    visibility = "PRIVATE",
                ),
            )

        // Gson 默认不序列化 null；老服务端（<0.31）看到的就是原来的三个字段。
        assertFalse("未开启位置时不应出现 location 字段：$json", json.contains("location"))
        assertFalse("未设置置顶时不应出现 pinned 字段：$json", json.contains("pinned"))
        assertTrue(json.contains("\"content\":\"hello\""))
    }

    @Test
    fun createMemoRequest_serializesLocationWhenPresent() {
        val json =
            gson.toJson(
                CreateMemoRequestDto(
                    state = "NORMAL",
                    content = "hello",
                    visibility = "PRIVATE",
                    location = MemoLocationDto(latitude = 22.5431, longitude = 114.0579),
                ),
            )

        assertTrue("应携带 location：$json", json.contains("\"location\""))
        assertTrue(json.contains("\"latitude\":22.5431"))
        assertTrue(json.contains("\"longitude\":114.0579"))
    }

    @Test
    fun memosUrls_matchUpstream031Paths() {
        val base = "https://me.pscly.cc/"

        assertEquals("https://me.pscly.cc/api/v1/memos", MemosUrls.listMemos(base))
        assertEquals("https://me.pscly.cc/api/v1/memos/abc", MemosUrls.memo(base, "memos/abc"))
        assertEquals(
            "https://me.pscly.cc/api/v1/memos/abc/attachments",
            MemosUrls.memoAttachments(base, "memos/abc"),
        )
        // 0.31：评论/反应/关系/分享都以 {memo} 作为父资源
        assertEquals(
            "https://me.pscly.cc/api/v1/memos/abc/comments",
            MemosUrls.memoComments(base, "memos/abc"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/memos/abc/reactions",
            MemosUrls.memoReactions(base, "memos/abc"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/memos/abc/relations",
            MemosUrls.memoRelations(base, "memos/abc"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/memos/abc/shares",
            MemosUrls.memoShares(base, "memos/abc"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/shares/token1/memo",
            MemosUrls.sharedMemo(base, "token1"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/memos/-/linkMetadata",
            MemosUrls.linkMetadata(base),
        )
        // Space / View
        assertEquals("https://me.pscly.cc/api/v1/spaces", MemosUrls.spaces(base))
        assertEquals(
            "https://me.pscly.cc/api/v1/spaces/default/members",
            MemosUrls.spaceMembers(base, "spaces/default"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/spaces/default/invitations/inv1:accept",
            MemosUrls.spaceInvitationAccept(base, "spaces/default/invitations/inv1"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/users/1/views",
            MemosUrls.userMemoViews(base, "users/1"),
        )
        assertEquals(
            "https://me.pscly.cc/api/v1/users/1/spaceInvitations",
            MemosUrls.userSpaceInvitations(base, "users/1"),
        )
    }
}
