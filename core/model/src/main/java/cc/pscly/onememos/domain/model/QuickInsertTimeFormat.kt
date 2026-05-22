package cc.pscly.onememos.domain.model

/**
 * “一键插入时间”支持的固定格式。
 *
 * 约定：
 * - FULL_DATETIME：yyyy-MM-dd HH:mm:ss
 * - TIME_ONLY：HH:mm:ss
 */
enum class QuickInsertTimeFormat {
    FULL_DATETIME,
    TIME_ONLY,
    ;

    companion object {
        fun fromStorage(raw: String?): QuickInsertTimeFormat =
            runCatching { if (raw.isNullOrBlank()) null else valueOf(raw) }
                .getOrNull()
                ?: FULL_DATETIME
    }
}
