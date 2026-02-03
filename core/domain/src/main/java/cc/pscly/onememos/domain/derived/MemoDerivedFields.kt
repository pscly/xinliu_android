package cc.pscly.onememos.domain.derived

import cc.pscly.onememos.domain.tag.TagExtractor

data class MemoDerivedFields(
    val plainPreview: String,
    val tagsText: String,
    val derivedVersion: Int,
    val derivedAt: Long,
) {
    val tags: List<String> get() = TagsTextCodec.decode(tagsText)
}

object MemoDerivedFieldsDeriver {
    const val CURRENT_VERSION: Int = 1

    fun derive(
        content: String,
        now: Long = System.currentTimeMillis(),
        previewMaxChars: Int = 320,
    ): MemoDerivedFields {
        val preview = MarkdownDeriver.plainPreview(markdown = content, maxChars = previewMaxChars)
        val tagsText = TagsTextCodec.encode(TagExtractor.extractAll(content))
        return MemoDerivedFields(
            plainPreview = preview,
            tagsText = tagsText,
            derivedVersion = CURRENT_VERSION,
            derivedAt = now,
        )
    }
}

object TagsTextCodec {
    fun encode(tags: List<String>): String {
        val normalized =
            tags
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        if (normalized.isEmpty()) return ""

        return buildString {
            append('\n')
            for (t in normalized) {
                append(t)
                append('\n')
            }
        }
    }

    fun decode(tagsText: String): List<String> {
        if (tagsText.isBlank()) return emptyList()
        return tagsText
            .replace("\r\n", "\n")
            .split('\n')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }
}

object MarkdownDeriver {
    fun plainPreview(
        markdown: String,
        maxChars: Int = 320,
    ): String {
        if (markdown.isBlank()) return ""

        val limit = maxChars.coerceAtLeast(0)
        if (limit == 0) return ""
        val sb = StringBuilder(minOf(limit, markdown.length))
        var lastWasSpace = false
        var truncated = false

        for (ch in markdown) {
            if (ch == '\r') continue

            val c =
                when (ch) {
                    '\n', '\t' -> ' '
                    else -> ch
                }

            if (c.isWhitespace()) {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(c)
                lastWasSpace = false
            }

            if (sb.length >= limit) {
                truncated = true
                break
            }
        }

        val normalized = sb.toString().trim()
        if (!truncated) return normalized
        return normalized.take(maxChars).trimEnd() + "…"
    }

    /**
     * 生成“纯文本预览”，并跳过以关键字结尾的“元数据行”（例如 `标签: __Atags`）。
     *
     * 说明：
     * - 这里的匹配规则需要与 [cc.pscly.onememos.ui.util.AutoTagLineHider] 保持一致：
     *   行尾命中关键字，且关键字前是空白或冒号（: / ：）。
     * - 为了避免 split 带来的额外分配，这里按行扫描。
     */
    fun plainPreviewSkippingLinesEndingWithKeywords(
        markdown: String,
        keywords: List<String>,
        maxChars: Int = 320,
    ): String {
        if (markdown.isBlank()) return ""

        val keys =
            keywords
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .toList()
        if (keys.isEmpty()) return plainPreview(markdown, maxChars)

        val limit = maxChars.coerceAtLeast(0)
        if (limit == 0) return ""

        val sb = StringBuilder(minOf(limit, markdown.length))
        var lastWasSpace = false
        var truncated = false

        var i = 0
        val len = markdown.length
        while (i < len) {
            val lineStart = i
            var lineEnd = lineStart
            while (lineEnd < len && markdown[lineEnd] != '\n') {
                lineEnd++
            }

            var endExclusive = lineEnd
            if (endExclusive > lineStart && markdown[endExclusive - 1] == '\r') {
                endExclusive--
            }

            val hidden = lineEndsWithAnyKeyword(markdown, lineStart, endExclusive, keys)
            if (!hidden) {
                var j = lineStart
                while (j < endExclusive) {
                    val ch = markdown[j]
                    if (ch == '\r') {
                        j++
                        continue
                    }

                    val c =
                        when (ch) {
                            '\t' -> ' '
                            else -> ch
                        }

                    if (c.isWhitespace()) {
                        if (!lastWasSpace) {
                            sb.append(' ')
                            lastWasSpace = true
                            if (sb.length >= limit) {
                                truncated = true
                                break
                            }
                        }
                    } else {
                        sb.append(c)
                        lastWasSpace = false
                        if (sb.length >= limit) {
                            truncated = true
                            break
                        }
                    }
                    j++
                }
            }

            if (truncated) break

            // 把换行视作空白（与 plainPreview 行为一致），并在跳过元数据行时保留段落间隔感。
            if (lineEnd < len && sb.isNotEmpty() && !lastWasSpace) {
                sb.append(' ')
                lastWasSpace = true
                if (sb.length >= limit) {
                    truncated = true
                    break
                }
            }

            i = if (lineEnd < len) lineEnd + 1 else lineEnd
        }

        val normalized = sb.toString().trim()
        if (!truncated) return normalized
        return normalized.take(maxChars).trimEnd() + "…"
    }

    private fun lineEndsWithAnyKeyword(
        text: String,
        start: Int,
        endExclusive: Int,
        keys: List<String>,
    ): Boolean {
        if (start >= endExclusive) return false

        var trimmedEnd = endExclusive
        while (trimmedEnd > start && text[trimmedEnd - 1].isWhitespace()) {
            trimmedEnd--
        }
        if (trimmedEnd <= start) return false

        for (key in keys) {
            if (trimmedEnd - start < key.length) continue
            val keyStart = trimmedEnd - key.length
            if (!text.regionMatches(keyStart, key, 0, key.length, ignoreCase = false)) continue
            if (keyStart == start) return true
            val prev = text[keyStart - 1]
            if (prev.isWhitespace() || prev == ':' || prev == '：') return true
        }
        return false
    }
}
