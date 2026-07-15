package cc.pscly.onememos.domain.repository

sealed interface MemoBrowseScope {
    data object All : MemoBrowseScope

    /**
     * 登录态下但 creator 未解析时的保守模式：只显示本地未上传记录，避免误展示工作区/公开内容。
     */
    data object LocalOnly : MemoBrowseScope

    data class Creator(val creator: String) : MemoBrowseScope
}
