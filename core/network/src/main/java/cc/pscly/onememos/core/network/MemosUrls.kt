package cc.pscly.onememos.core.network

import android.net.Uri

/**
 * Memos（上游 usememos/memos）REST 路径拼装。
 *
 * 对齐版本：**v0.31.0**（`/api/v1/...` 路径体系）。
 * 注意上游 `26.10` 起把路径整体迁移到 `/api/...`（去掉 v1 段）并让旧路径返回 410，
 * 届时只需改本文件里的 `API_PREFIX` 常量，其余调用点不用动。
 */
object MemosUrls {
    /** REST 版本前缀；26.10+ 迁移时改成 "api"。 */
    private const val API_PREFIX = "api/v1"

    // 默认 Memos 服务器（普通用户无感使用；仅开发者模式2允许自定义服务器）。
    const val DEFAULT_MEMOS_SERVER_URL: String = "https://me.pscly.cc/"

    /**
     * 将用户输入的 serverUrl 归一化为可拼接的 base（保证有 scheme，去掉尾部 /）。
     */
    fun normalizeServerBase(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        val withScheme =
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }

        val normalized = withScheme.trimEnd('/')
        return if (normalized.isBlank()) null else normalized
    }

    fun apiV1(
        base: String,
        path: String,
    ): String {
        val baseNorm = base.trimEnd('/')
        val p = path.trimStart('/')
        return "$baseNorm/$p"
    }

    // ----------------------------
    // 认证 / 实例
    // ----------------------------
    fun authMe(base: String): String = apiV1(base, "$API_PREFIX/auth/me")

    fun authStatus(base: String): String = apiV1(base, "$API_PREFIX/auth/status")

    fun currentUser(base: String): String = apiV1(base, "$API_PREFIX/users/me")

    fun instanceProfile(base: String): String = apiV1(base, "$API_PREFIX/instance/profile")

    // ----------------------------
    // Memo 主资源
    // ----------------------------
    fun listMemos(base: String): String = apiV1(base, "$API_PREFIX/memos")

    fun createMemo(base: String): String = apiV1(base, "$API_PREFIX/memos")

    /** `memoName` 形如 `memos/abc123`。 */
    fun memo(
        base: String,
        memoName: String,
    ): String = apiV1(base, "$API_PREFIX/$memoName")

    fun memoAttachments(
        base: String,
        memoName: String,
    ): String = apiV1(base, "$API_PREFIX/$memoName/attachments")

    /** 评论列表 / 新建评论（0.31：body 是 Memo）。 */
    fun memoComments(
        base: String,
        memoName: String,
    ): String = apiV1(base, "$API_PREFIX/$memoName/comments")

    /** 反应列表 / 新建反应。 */
    fun memoReactions(
        base: String,
        memoName: String,
    ): String = apiV1(base, "$API_PREFIX/$memoName/reactions")

    /** `reactionName` 形如 `memos/abc/reactions/xyz`。 */
    fun memoReaction(
        base: String,
        reactionName: String,
    ): String = apiV1(base, "$API_PREFIX/$reactionName")

    /** 关系列表 / 覆盖式设置关系（PATCH）。 */
    fun memoRelations(
        base: String,
        memoName: String,
    ): String = apiV1(base, "$API_PREFIX/$memoName/relations")

    /** 分享令牌列表 / 新建分享。 */
    fun memoShares(
        base: String,
        memoName: String,
    ): String = apiV1(base, "$API_PREFIX/$memoName/shares")

    /** `shareName` 形如 `memos/abc/shares/token`。 */
    fun memoShare(
        base: String,
        shareName: String,
    ): String = apiV1(base, "$API_PREFIX/$shareName")

    /** 免登录读取分享内容：`/api/v1/shares/{shareToken}/memo`。 */
    fun sharedMemo(
        base: String,
        shareToken: String,
    ): String = apiV1(base, "$API_PREFIX/shares/$shareToken/memo")

    /** 单个链接预览：`/api/v1/memos/-/linkMetadata?url=...`。 */
    fun linkMetadata(base: String): String = apiV1(base, "$API_PREFIX/memos/-/linkMetadata")

    // ----------------------------
    // 附件
    // ----------------------------
    fun createAttachment(base: String): String = apiV1(base, "$API_PREFIX/attachments")

    fun listAttachments(base: String): String = apiV1(base, "$API_PREFIX/attachments")

    /** `attachmentName` 形如 `attachments/xyz`。 */
    fun attachment(
        base: String,
        attachmentName: String,
    ): String = apiV1(base, "$API_PREFIX/$attachmentName")

    /**
     * 下载附件（图片）：
     * GET /file/{name=attachments/<wildcard>}/{filename}?thumbnail=true|false
     */
    fun attachmentFileUrl(
        base: String,
        attachmentName: String,
        filename: String,
        thumbnail: Boolean = false,
    ): String {
        val baseNorm = base.trimEnd('/')
        val encodedFilename = Uri.encode(filename)
        val url = "$baseNorm/file/$attachmentName/$encodedFilename"
        return if (thumbnail) "$url?thumbnail=true" else url
    }

    // ----------------------------
    // Space（0.31 协作空间）
    // ----------------------------
    fun spaces(base: String): String = apiV1(base, "$API_PREFIX/spaces")

    /** `spaceName` 形如 `spaces/default`。 */
    fun space(
        base: String,
        spaceName: String,
    ): String = apiV1(base, "$API_PREFIX/$spaceName")

    fun spaceMembers(
        base: String,
        spaceName: String,
    ): String = apiV1(base, "$API_PREFIX/$spaceName/members")

    /** `memberName` 形如 `spaces/default/members/1`。 */
    fun spaceMember(
        base: String,
        memberName: String,
    ): String = apiV1(base, "$API_PREFIX/$memberName")

    fun spaceInvitations(
        base: String,
        spaceName: String,
    ): String = apiV1(base, "$API_PREFIX/$spaceName/invitations")

    /** `invitationName` 形如 `spaces/default/invitations/xyz`。 */
    fun spaceInvitation(
        base: String,
        invitationName: String,
    ): String = apiV1(base, "$API_PREFIX/$invitationName")

    /** `invitationName` 后接 `:accept` / `:decline` 自定义方法。 */
    fun spaceInvitationAccept(
        base: String,
        invitationName: String,
    ): String = apiV1(base, "$API_PREFIX/$invitationName:accept")

    fun spaceInvitationDecline(
        base: String,
        invitationName: String,
    ): String = apiV1(base, "$API_PREFIX/$invitationName:decline")

    /** 当前用户收到的空间邀请：`/api/v1/users/{user}/spaceInvitations`。 */
    fun userSpaceInvitations(
        base: String,
        userResourceName: String,
    ): String = apiV1(base, "$API_PREFIX/$userResourceName/spaceInvitations")

    // ----------------------------
    // MemoView（0.31 由旧 Shortcut 迁移而来）
    // ----------------------------
    fun userMemoViews(
        base: String,
        userResourceName: String,
    ): String = apiV1(base, "$API_PREFIX/$userResourceName/views")

    /** `viewName` 形如 `users/1/views/xyz`。 */
    fun userMemoView(
        base: String,
        viewName: String,
    ): String = apiV1(base, "$API_PREFIX/$viewName")
}
