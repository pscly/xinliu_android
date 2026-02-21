package cc.pscly.onememos.ui.feature.home

import cc.pscly.onememos.domain.model.Memo
import cc.pscly.onememos.ui.util.DateTimeFormatter

internal object HomeShareTextBuilder {
    private const val EMPTY_PLACEHOLDER = "(分享内容为空)"
    private const val DELIMITER = "\n\n---\n\n"

    fun build(memos: List<Memo>): String {
        if (memos.isEmpty()) return EMPTY_PLACEHOLDER

        return buildString {
            memos.forEachIndexed { index, memo ->
                if (index > 0) append(DELIMITER)
                append(DateTimeFormatter.formatYmdHm(memo.createdAt))
                append('\n')
                append(memo.content)
            }
        }
    }
}
