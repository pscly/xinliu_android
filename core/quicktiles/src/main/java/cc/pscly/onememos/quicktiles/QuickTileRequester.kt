package cc.pscly.onememos.quicktiles

/**
 * Core-owned 快捷开关添加请求端口。
 * 使用 application Context 封装 StatusBarManager，不经过 app dispatcher。
 */
enum class QuickTileKind {
    QUICK_CAPTURE,
    SCREENSHOT_CAPTURE,
}

sealed interface QuickTileRequestResult {
    data class Completed(val statusCode: Int) : QuickTileRequestResult

    data object PlatformUnavailable : QuickTileRequestResult
}

interface QuickTileRequester {
    suspend fun request(kind: QuickTileKind): QuickTileRequestResult
}
