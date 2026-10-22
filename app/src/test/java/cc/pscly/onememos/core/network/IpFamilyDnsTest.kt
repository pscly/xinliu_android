package cc.pscly.onememos.core.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * IPv4/IPv6 顺序策略（1.17.2 起为“自适应”）：
 * - 默认：IPv4 优先，IPv6 保留在末尾作为兜底（绝不丢地址）；
 * - 记录过某个 host 实际连通的地址族后，该 host 优先走记录到的族；
 * - 开启 IPv6 设置：保持系统原始顺序；
 * - 纯 IPv6 / 纯 IPv4：原样返回。
 */
class IpFamilyDnsTest {
    private fun ipv4(vararg octets: Int): InetAddress =
        InetAddress.getByAddress(byteArrayOf(octets[0].toByte(), octets[1].toByte(), octets[2].toByte(), octets[3].toByte()))

    private val ipv4a = ipv4(28, 0, 1, 228)
    private val ipv4b = ipv4(28, 0, 1, 229)
    private val ipv6 = InetAddress.getByAddress(ByteArray(16) { 0xfd.toByte() })

    private fun dnsReturning(vararg addresses: InetAddress): Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = addresses.toList()
        }

    @Test
    fun default_prefersIpv4ButKeepsIpv6AsFallback() {
        val resolver = IpFamilyDns(dnsReturning(ipv6, ipv4a, ipv4b))

        val result = resolver.lookup("me.pscly.cc")

        // 地址不能丢：IPv4 在前，IPv6 在末尾
        assertEquals(3, result.size)
        assertEquals(ipv4a, result[0])
        assertEquals(ipv4b, result[1])
        assertTrue(result[2] is Inet6Address)
    }

    @Test
    fun afterIpv6Connected_prefersIpv6ForThatHostOnly() {
        val resolver = IpFamilyDns(dnsReturning(ipv6, ipv4a, ipv4b))
        resolver.recordConnectedAddress("me.pscly.cc", ipv6)

        val forHost = resolver.lookup("me.pscly.cc")
        val forOtherHost = resolver.lookup("xl.pscly.cc")

        // 该 host 改走 IPv6 优先，但 IPv4 仍在列表里兜底
        assertEquals(3, forHost.size)
        assertTrue(forHost[0] is Inet6Address)
        assertEquals(ipv4a, forHost[1])
        // 其它 host 不受影响
        assertEquals(ipv4a, forOtherHost[0])
    }

    @Test
    fun afterIpv4Connected_keepsIpv4First() {
        val resolver = IpFamilyDns(dnsReturning(ipv6, ipv4a))
        resolver.recordConnectedAddress("me.pscly.cc", ipv4a)

        val result = resolver.lookup("me.pscly.cc")

        assertEquals(ipv4a, result[0])
        assertTrue(result[1] is Inet6Address)
    }

    @Test
    fun resetPreferences_restoresIpv4First() {
        val resolver = IpFamilyDns(dnsReturning(ipv6, ipv4a))
        resolver.recordConnectedAddress("me.pscly.cc", ipv6)
        resolver.resetPreferences()

        val result = resolver.lookup("me.pscly.cc")

        assertEquals(ipv4a, result[0])
    }

    @Test
    fun ipv6Enabled_returnsUpstreamOrderUnchanged() {
        val resolver = IpFamilyDns(dnsReturning(ipv6, ipv4a))
        resolver.ipv6Enabled = true
        // 即使记录过 IPv4，也应该完全按系统顺序
        resolver.recordConnectedAddress("me.pscly.cc", ipv4a)

        val result = resolver.lookup("me.pscly.cc")

        assertEquals(listOf(ipv6, ipv4a), result)
    }

    @Test
    fun ipv6Disabled_keepsIpv6OnlyHostUnchanged() {
        val resolver = IpFamilyDns(dnsReturning(ipv6))

        val result = resolver.lookup("v6only.example.com")

        assertEquals(1, result.size)
        assertTrue(result[0] is Inet6Address)
    }

    @Test
    fun ipv6Disabled_keepsIpv4OnlyHostUnchanged() {
        val resolver = IpFamilyDns(dnsReturning(ipv4a, ipv4b))

        val result = resolver.lookup("v4only.example.com")

        assertEquals(listOf(ipv4a, ipv4b), result)
        assertTrue(result.all { it is Inet4Address })
    }
}
