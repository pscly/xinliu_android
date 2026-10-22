package cc.pscly.onememos.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Collections

/**
 * 按设置调整 DNS 解析结果的**顺序**，绝不丢弃地址。
 *
 * 背景（真机实测）：
 * 用户环境是「VPN + fake-ip」：同一域名 A 记录是 `198.18.x.x`（fake IPv4），
 * AAAA 记录是 `fdfe:dcba:9876::ab`（fake IPv6）。1.17.1 直接把 AAAA 过滤掉后，
 * 只剩 IPv4 路径，出现 `SocketTimeoutException: Read timed out`（TLS 握手/响应卡住），
 * 同步反复重试直到“已达重试上限”。
 *
 * 现行策略（[ipv6Enabled] = false，默认）：
 * 1. 按 [ipv6PreferredHosts] 里记录的历史成功族排序（自适应）；
 * 2. 没有历史时 **IPv4 优先**；
 * 3. 另一族始终保留在后面，作为 OkHttp 逐地址尝试时的兜底 ——
 *    IPv4 连不上时仍会自动退到 IPv6，不会像 1.17.1 那样把可用网络打死。
 *
 * [ipv6Enabled] = true 时完全保持系统解析顺序（通常 IPv6 优先）。
 */
class IpFamilyDns(
    private val upstream: Dns = Dns.SYSTEM,
) : Dns {
    @Volatile
    var ipv6Enabled: Boolean = false

    /** host -> 最近一次**成功连接**的地址族是否为 IPv6（自适应记忆）。 */
    private val ipv6PreferredHosts: MutableMap<String, Boolean> =
        Collections.synchronizedMap(HashMap<String, Boolean>())

    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = upstream.lookup(hostname)
        if (ipv6Enabled || resolved.size <= 1) return resolved

        val ipv4 = resolved.filter { it is Inet4Address }
        val ipv6 = resolved.filterNot { it is Inet4Address }
        // 纯 IPv4 / 纯 IPv6 站点：原样返回，不做任何重排。
        if (ipv4.isEmpty() || ipv6.isEmpty()) return resolved

        val preferIpv6 = ipv6PreferredHosts[hostname] == true
        return if (preferIpv6) ipv6 + ipv4 else ipv4 + ipv6
    }

    /**
     * 记录某个 host 实际连接成功的地址族。
     * 由 [IpFamilyEventListener] 在 OkHttp `connectEnd` 时调用。
     */
    fun recordConnectedAddress(
        hostname: String,
        address: InetAddress,
    ) {
        if (hostname.isBlank()) return
        ipv6PreferredHosts[hostname] = address is Inet6Address
    }

    /** 切换 IPv6 设置时清空自适应记忆，避免旧偏好影响新策略。 */
    fun resetPreferences() {
        ipv6PreferredHosts.clear()
    }
}
