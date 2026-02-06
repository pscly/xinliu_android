package cc.pscly.onememos.domain.model

/**
 * Todo 的常用状态值。
 *
 * 注意：服务端 status 类型为字符串（并非枚举），这里仅提供“约定俗成”的默认值，方便 UI 与本地逻辑统一。
 */
object TodoStatuses {
    const val OPEN: String = "open"
    const val DONE: String = "done"
}

