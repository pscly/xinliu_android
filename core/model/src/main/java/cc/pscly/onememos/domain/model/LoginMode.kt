package cc.pscly.onememos.domain.model

/**
 * 登录方式，仅用于 UI 展示与排障。
 *
 * 关键点：无论哪种方式，最终都要落在同一套 `serverUrl + token` 上，业务代码不分叉。
 */
enum class LoginMode {
    UNKNOWN,
    BACKEND,
    CUSTOM,
}
