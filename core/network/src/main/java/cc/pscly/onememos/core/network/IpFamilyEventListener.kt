package cc.pscly.onememos.core.network

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol

/**
 * 网络诊断 + 地址族自适应：
 * - `connectEnd`：记录该 host 实际连上的地址族，交给 [IpFamilyDns] 下次优先使用；
 * - `connectFailed` / `callFailed`：打一条 warning，便于用 `adb logcat -s MemoNet`
 *   直接看到“哪个 host/地址/异常类型”导致同步失败（Release 包也有，量很小）。
 *
 * 注意：只记录 host 与异常类型，不打印请求头/正文，避免泄露凭据。
 */
class IpFamilyEventListener(
    private val dns: IpFamilyDns,
) : EventListener() {
    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        val address = inetSocketAddress.address ?: return
        dns.recordConnectedAddress(hostname = call.request().url.host, address = address)
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ex: IOException,
    ) {
        val address = inetSocketAddress.address
        Log.w(
            TAG,
            "connectFailed host=${call.request().url.host} addr=$address " +
                "err=${ex.javaClass.simpleName}: ${ex.message}",
        )
        if (address != null && isProxyFakeIp(address)) {
            // 这类地址只有 VPN/代理客户端自己认识；它一旦不可用，应用侧无法绕开，只能提示改直连。
            Log.w(
                TAG,
                "检测到代理/VPN 的 fake-ip（$address）：如果持续超时，" +
                    "请在代理客户端把 ${call.request().url.host} 设为直连（DIRECT），或关闭代理后重试",
            )
        }
    }

    override fun callFailed(
        call: Call,
        ex: IOException,
    ) {
        Log.w(
            TAG,
            "callFailed host=${call.request().url.host} path=${call.request().url.encodedPath} " +
                "err=${ex.javaClass.simpleName}: ${ex.message}",
        )
    }

    /** Clash/Mihomo 常见 fake-ip 段：IPv4 198.18.0.0/15、IPv6 fdfe:dcba:9876::/48。 */
    private fun isProxyFakeIp(address: java.net.InetAddress): Boolean {
        val bytes = address.address
        return when (bytes.size) {
            4 ->
                (bytes[0].toInt() and 0xFF) == 198 &&
                    ((bytes[1].toInt() and 0xFF) == 18 || (bytes[1].toInt() and 0xFF) == 19)
            16 ->
                (bytes[0].toInt() and 0xFF) == 0xFD &&
                    (bytes[1].toInt() and 0xFF) == 0xFE &&
                    (bytes[2].toInt() and 0xFF) == 0xDC &&
                    (bytes[3].toInt() and 0xFF) == 0xBA &&
                    (bytes[4].toInt() and 0xFF) == 0x98 &&
                    (bytes[5].toInt() and 0xFF) == 0x76
            else -> false
        }
    }

    private companion object {
        const val TAG = "MemoNet"
    }
}
