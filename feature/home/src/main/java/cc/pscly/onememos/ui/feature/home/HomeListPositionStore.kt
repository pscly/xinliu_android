package cc.pscly.onememos.ui.feature.home

/**
 * 首页列表位置缓存：
 * - 进入详情/编辑前 capture
 * - 返回列表后在数据加载到位时 restore（成功后再清除 pending）
 *
 * 这样可以避免“列表还没加载到对应 index 就把 pending 消耗掉”，导致偶现回到顶部。
 */
internal class HomeListPositionStore {
    private var index: Int = 0
    private var offset: Int = 0
    private var pendingRestore: Boolean = false

    fun capture(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        index = firstVisibleItemIndex.coerceAtLeast(0)
        offset = firstVisibleItemScrollOffset.coerceAtLeast(0)
        pendingRestore = true
    }

    fun peek(): Pair<Int, Int> = index to offset

    fun pending(): Pair<Int, Int>? = if (pendingRestore) index to offset else null

    fun markRestored() {
        pendingRestore = false
    }
}
