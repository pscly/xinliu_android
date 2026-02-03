package cc.pscly.onememos.domain.tag

import cc.pscly.onememos.domain.model.Memo

data class TagStat(
    val name: String,
    val count: Int,
)

object TagStats {
    /**
     * 从 memos 内容里统计标签：
     * - 去重：同一条 memo 内重复标签只算一次
     * - 排序：按数量倒序，其次按字典序
     */
    fun build(memos: List<Memo>): List<TagStat> {
        if (memos.isEmpty()) return emptyList()
        val counts = LinkedHashMap<String, Int>()
        for (memo in memos) {
            val tags =
                if (memo.tags.isNotEmpty()) {
                    memo.tags
                } else {
                    // 兼容升级阶段：派生字段回填前，仍可从内容里提取，避免标签面板“空白”。
                    TagExtractor.extractAll(memo.content)
                }
            for (t in tags.distinct()) {
                counts[t] = (counts[t] ?: 0) + 1
            }
        }
        return counts.entries
            .map { (name, count) -> TagStat(name = name, count = count) }
            .sortedWith(compareByDescending<TagStat> { it.count }.thenBy { it.name })
    }
}
