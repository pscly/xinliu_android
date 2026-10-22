package cc.pscly.onememos.core.network.dto

import com.google.gson.annotations.SerializedName

/**
 * 上游 memos v0.31.0 `Space`（协作空间）。
 *
 * 字段说明：
 * - [currentUserRole]/[memberCount] 由服务端计算，只读；
 * - [icon] 是 `Space.Icon`，emoji 与 lucide 二选一。
 */
data class SpaceDto(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val currentUserRole: String? = null,
    val memberCount: Int? = null,
    val icon: SpaceIconDto? = null,
)

/** `Space.Icon`：Unicode emoji 或 Lucide 图标名。 */
data class SpaceIconDto(
    val emoji: String? = null,
    val lucide: String? = null,
)

/** `SpaceMember`：空间成员与治理角色（ADMIN/USER）。 */
data class SpaceMemberDto(
    val name: String? = null,
    val user: String? = null,
    val role: String? = null,
)

/** `SpaceInvitation`：发给已有用户的待接受邀请。 */
data class SpaceInvitationDto(
    val name: String? = null,
    val invitee: String? = null,
    val role: String? = null,
    val space: SpaceDto? = null,
)

data class ListSpacesResponseDto(
    val spaces: List<SpaceDto> = emptyList(),
    val nextPageToken: String? = null,
)

data class ListSpaceMembersResponseDto(
    val members: List<SpaceMemberDto> = emptyList(),
    val nextPageToken: String? = null,
)

data class ListSpaceInvitationsResponseDto(
    val invitations: List<SpaceInvitationDto> = emptyList(),
    val nextPageToken: String? = null,
)

data class ListUserSpaceInvitationsResponseDto(
    val spaceInvitations: List<SpaceInvitationDto> = emptyList(),
    val nextPageToken: String? = null,
)

/**
 * `MemoView`：可复用的列表视图（0.31 由旧 Shortcut 迁移而来）。
 *
 * [filter] 直接复用 ListMemos 的 filter 表达式。
 */
data class MemoViewDto(
    val name: String? = null,
    val title: String? = null,
    val filter: String? = null,
    val icon: MemoViewIconDto? = null,
)

/** `MemoView.Icon`：与 Space.Icon 同构。 */
data class MemoViewIconDto(
    val emoji: String? = null,
    val lucide: String? = null,
)

data class ListMemoViewsResponseDto(
    @SerializedName("memoViews")
    val memoViews: List<MemoViewDto> = emptyList(),
)
