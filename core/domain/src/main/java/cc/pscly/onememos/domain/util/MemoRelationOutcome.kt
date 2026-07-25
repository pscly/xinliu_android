package cc.pscly.onememos.domain.util

/** 待办转换 / 收藏切换的结果（ADR 0013），供 UI Toast 映射。 */
enum class MemoRelationOutcome {
    /** 新建实体并写注释 */
    ADDED,
    /** 强取消：删实体并剥注释 */
    REMOVED,
    /** 有实体无注释：仅补写注释 */
    MARKED,
    /** 注释失效后清注释并重新建立 */
    READDED,
}

fun MemoRelationOutcome.todoToast(): String =
    when (this) {
        MemoRelationOutcome.ADDED -> "已加入待办"
        MemoRelationOutcome.REMOVED -> "已取消待办"
        MemoRelationOutcome.MARKED -> "已标记为待办"
        MemoRelationOutcome.READDED -> "已重新加入待办"
    }

fun MemoRelationOutcome.favoriteToast(): String =
    when (this) {
        MemoRelationOutcome.ADDED -> "已收藏"
        MemoRelationOutcome.REMOVED -> "已取消收藏"
        MemoRelationOutcome.MARKED -> "已标记为收藏"
        MemoRelationOutcome.READDED -> "已重新收藏"
    }
