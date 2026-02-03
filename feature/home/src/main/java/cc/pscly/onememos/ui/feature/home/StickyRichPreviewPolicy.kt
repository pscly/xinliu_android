package cc.pscly.onememos.ui.feature.home

/**
 * "富预览粘住"：仅缓存轻量 memoId，用于减缓 rich/plain 预览切换导致的布局跳动。
 *
 * 约束：
 * - 纯 Kotlin（无 Android/Compose 依赖），便于 JVM 单测。
 * - LRU 只存 id，不存任何富对象/渲染结果。
 */
internal fun isStickyRichPreviewEnabled(buildType: String): Boolean {
    return buildType == "benchmark" || buildType == "release"
}

// 兼容旧调用点（如有）：limit=0 时强制禁用。
internal fun isStickyRichPreviewEnabled(buildType: String, limit: Int): Boolean {
    if (limit <= 0) return false
    return isStickyRichPreviewEnabled(buildType)
}

/**
 * 首页列表富预览“粘住”策略（仅运行时内存，无持久化）。
 *
 * 语义：
 * - limit == 0 时完全禁用：清空 + markSticky no-op + isSticky 恒 false。
 * - 调小 limit 需要立即裁剪。
 * - markSticky 已存在的 uuid 需要刷新最近性（LRU）。
 *
 * 约束：
 * - 只缓存 memo 的轻量标识（uuid 字符串），不缓存任何内容/渲染结果/附件。
 * - 纯 Kotlin/JVM 可运行：不依赖 Android runtime（例如 android.util.LruCache）。
 */
internal class StickyRichPreviewPolicy(initialLimit: Int) {
    // accessOrder=true: get/put(已存在) 会把 key 移到队尾（最新）。
    private val lru = LinkedHashMap<String, Unit>(16, 0.75f, true)

    var limit: Int = initialLimit.coerceAtLeast(0)
        set(value) {
            val normalized = value.coerceAtLeast(0)
            field = normalized
            if (normalized == 0) {
                lru.clear()
                return
            }
            trimToLimit(normalized)
        }

    fun markSticky(memoUuid: String) {
        if (limit == 0) return
        // 显式 remove+put，确保“刷新最近性”（即使底层实现细节变化）。
        lru.remove(memoUuid)
        lru[memoUuid] = Unit
        trimToLimit(limit)
    }

    fun isSticky(memoUuid: String): Boolean {
        if (limit == 0) return false
        // containsKey 不会触发 accessOrder 的访问更新，避免热路径 churn。
        return lru.containsKey(memoUuid)
    }

    fun clear() {
        lru.clear()
    }

    private fun trimToLimit(limit: Int) {
        while (lru.size > limit) {
            val it = lru.entries.iterator()
            if (!it.hasNext()) return
            it.next()
            it.remove()
        }
    }
}
