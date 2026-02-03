package cc.pscly.onememos.ui

import android.net.Uri

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val PROFILE = "profile"
    const val ARCHIVED = "archived"
    const val SETTINGS = "settings"
    const val AUTH = "auth"

    const val EDITOR = "editor"
    const val SHARE_CARD = "share_card"
    const val ARG_UUID = "uuid"
    const val ARG_AUTH_MODE = "mode"

    fun editor(uuid: String? = null): String =
        if (uuid.isNullOrBlank()) {
            EDITOR
        } else {
            // uuid 可能包含 "/"（例如服务端 name: memos/123），必须编码，否则 Navigation 可能匹配失败甚至导致空白页/崩溃。
            "$EDITOR?$ARG_UUID=${Uri.encode(uuid)}"
        }

    fun shareCard(uuid: String): String = "$SHARE_CARD?$ARG_UUID=${Uri.encode(uuid)}"

    fun auth(mode: String? = null): String =
        if (mode.isNullOrBlank()) {
            AUTH
        } else {
            "$AUTH?$ARG_AUTH_MODE=${Uri.encode(mode)}"
        }
}
