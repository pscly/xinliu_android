package cc.pscly.onememos.domain.model

/**
 * 笔记定位信息，对应上游 memos v0.31.0 的 `Location` 资源（placeholder/latitude/longitude）。
 *
 * 约定：
 * - 经纬度来自系统定位；拿不到定位时整个对象为 null，不做 0,0 之类的占位。
 * - placeholder 预留给逆地理编码后的地址文本；当前实现不下发，保持为空。
 */
data class MemoLocation(
    val latitude: Double,
    val longitude: Double,
    val placeholder: String = "",
) {
    /** 是否能用于展示/上送（过滤非法坐标与 0,0 哨兵值）。 */
    val isUsable: Boolean
        get() = latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

    companion object {
        /** 坐标无效时返回 null，避免把脏数据写进数据库。 */
        fun of(
            latitude: Double?,
            longitude: Double?,
            placeholder: String = "",
        ): MemoLocation? {
            if (latitude == null || longitude == null) return null
            val location = MemoLocation(latitude = latitude, longitude = longitude, placeholder = placeholder)
            return location.takeIf { it.isUsable }
        }
    }
}
