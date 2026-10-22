package cc.pscly.onememos.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * 按设置过滤 DNS 解析结果的 [Dns] 实现。
 *
 * 背景：部分网络环境下域名会同时返回 AAAA（IPv6）与 A（IPv4），而设备的 IPv6
 * 路由实际不可用，OkHttp 尝试 IPv6 时会一直等到连接超时，表现为“同步失败/很慢”。
 *
 * 策略：
 * - [ipv6Enabled] = false（默认）：只保留 IPv4 地址；
 * - [ipv6Enabled] = true：完全按系统原始顺序返回；
 * - 域名只有 AAAA 没有 A 时**回退为原始结果**，避免把纯 IPv6 站点直接打死。
 *
 * 开关由设置项 `ipv6_enabled` 驱动，App 启动后持续同步（见 OneMemosApplication）。
 */
class IpFamilyDns(
    private val upstream: Dns = Dns.SYSTEM,
) : Dns {
    @Volatile
    var ipv6Enabled: Boolean = false

    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = upstream.lookup(hostname)
        if (ipv6Enabled) return resolved
        val ipv4Only = resolved.filter { it is Inet4Address }
        return if (ipv4Only.isNotEmpty()) ipv4Only else resolved
    }
}
