package cc.pscly.onememos.domain.location

import cc.pscly.onememos.domain.model.MemoLocation

/**
 * 设备当前位置提供者。
 *
 * 设计要点：
 * - **不阻塞保存**：界面打开时调用 [prime] 预热，保存时用 [latestOrNull] 取最近一次定位；
 * - **失败静默**：没有权限、定位关闭、超时都返回 null，由调用方跳过位置而不是打断保存；
 * - 实现放在 app 模块（Android LocationManager），domain/feature 只依赖本接口。
 */
interface MemoLocationProvider {
    /** 预热一次定位（异步、无回调）；重复调用应尽量廉价。 */
    fun prime()

    /**
     * 取最近一次可用定位。
     *
     * @param timeoutMs 允许等待的最长时间；<=0 表示只取缓存，不等待。
     * @return 没有可用定位时返回 null。
     */
    suspend fun latestOrNull(timeoutMs: Long = 0L): MemoLocation?
}

/**
 * 空实现：用于测试或未注入定位能力的场景，永远返回 null。
 */
object NoOpMemoLocationProvider : MemoLocationProvider {
    override fun prime() = Unit

    override suspend fun latestOrNull(timeoutMs: Long): MemoLocation? = null
}
