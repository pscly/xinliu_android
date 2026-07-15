package cc.pscly.onememos.quicktiles

interface OverlayPermissionGateway {
    val packageName: String

    fun isGranted(): Boolean
}
