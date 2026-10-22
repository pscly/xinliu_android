package cc.pscly.onememos.core.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IPv4/IPv6 策略：设置关闭时只返回 IPv4，开启时保持系统原始结果，
 * 只有 AAAA 没有 A 时回退为原始结果（避免纯 IPv6 站点不可用）。
 */
class IpFamilyDnsTest {
    private val ipv4a = InetAddress.getByAddress(byteArrayOf(1, 2, 3, 4))
    private val ipv4b = InetAddress.getByAddress(byteArrayOf(5, 6, 7, 8))
    private val ipv6 = InetAddress.getByAddress(ByteArray(16) { 0x20 })

    private fun dnsReturning(vararg addresses: InetAddress): Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = addresses.toList()
        }

    @Test
    fun ipv6Disabled_filtersOutIpv6AndKeepsIpv4() {
        val resolver = IpFamilyDns(dnsReturning(ipv6, ipv4a, ipv4b))

        val result = resolver.lookup("example.com")

        assertEquals(2, result.size)
        assertTrue(result.all { it is Inet4Address })
        // 保持上游相对顺序
        assertEquals(listOf(ipv4a, ipv4b), result)
    }

    @Test
    fun ipv6Enabled_returnsUpstreamResultUnchanged() {
        val resolver = IpFamilyDns(dnsReturning(ipv6, ipv4a))
        resolver.ipv6Enabled = true

        val result = resolver.lookup("example.com")

        assertEquals(2, result.size)
        assertEquals(ipv6, result[0])
        assertEquals(ipv4a, result[1])
    }

    @Test
    fun ipv6Disabled_fallsBackWhenOnlyIpv6Exists() {
        val resolver = IpFamilyDns(dnsReturning(ipv6))

        val result = resolver.lookup("v6only.example.com")

        // 只有 AAAA 时不能把结果清空，否则纯 IPv6 站点会直接不可用
        assertEquals(1, result.size)
        assertTrue(result[0] is Inet6Address)
    }
}
