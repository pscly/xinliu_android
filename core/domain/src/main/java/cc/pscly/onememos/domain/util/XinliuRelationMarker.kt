package cc.pscly.onememos.domain.util

/**
 * 随笔关系注释（ADR 0013）：文末 HTML 注释，记录待办/收藏关联。
 * 规范：`<!-- xinliu:YYYY-MM-DD:TODO id=<id> -->` / `...:FAV id=<id> -->`
 */
object XinliuRelationMarker {
    enum class Kind { TODO, FAV }

    data class Marker(
        val kind: Kind,
        val date: String,
        val id: String,
        /** 匹配到的整行（含可选前后空白所在行）。 */
        val rawLine: String,
    )

    private val lineRegex =
        Regex(
            """^\s*<!--\s*xinliu:(\d{4}-\d{2}-\d{2}):(TODO|FAV)\s+id=([^\s>]+)\s*-->\s*$""",
        )

    fun parseAll(content: String): List<Marker> {
        if (content.isBlank()) return emptyList()
        return content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .mapNotNull { line ->
                val m = lineRegex.matchEntire(line) ?: return@mapNotNull null
                val kind =
                    when (m.groupValues[2]) {
                        "TODO" -> Kind.TODO
                        "FAV" -> Kind.FAV
                        else -> return@mapNotNull null
                    }
                Marker(
                    kind = kind,
                    date = m.groupValues[1],
                    id = m.groupValues[3].trim(),
                    rawLine = line,
                )
            }
            .toList()
    }

    fun find(content: String, kind: Kind): Marker? =
        parseAll(content).lastOrNull { it.kind == kind && it.id.isNotBlank() }

    fun has(content: String, kind: Kind): Boolean = find(content, kind) != null

    fun format(kind: Kind, date: String, id: String): String {
        val type = if (kind == Kind.TODO) "TODO" else "FAV"
        return "<!-- xinliu:$date:$type id=$id -->"
    }

    /** 去掉指定类型的关系注释行（可多条同类型时全剥）。 */
    fun strip(content: String, kind: Kind? = null): String {
        if (content.isEmpty()) return content
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val kept =
            normalized.lineSequence().filterNot { line ->
                val m = lineRegex.matchEntire(line) ?: return@filterNot false
                if (kind == null) return@filterNot true
                val lineKind = if (m.groupValues[2] == "TODO") Kind.TODO else Kind.FAV
                lineKind == kind
            }
        return kept.joinToString("\n").trimEnd()
    }

    /**
     * 写入或替换某一类型的注释（保证同类型仅一条，置于文末）。
     * 不改变其它类型注释。
     */
    fun upsert(content: String, kind: Kind, date: String, id: String): String {
        val base = strip(content, kind).trimEnd()
        val line = format(kind, date, id)
        return if (base.isEmpty()) line else base + "\n" + line
    }

    /** 读路径：剥除全部关系注释后的正文（预览/搜索/标题派生）。 */
    fun stripAllForRead(content: String): String = strip(content, kind = null).trimEnd()
}
