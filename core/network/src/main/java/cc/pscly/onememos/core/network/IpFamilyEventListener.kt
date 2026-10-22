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
        Log.w(
            TAG,
            "connectFailed host=${call.request().url.host} addr=${inetSocketAddress.address} " +
                "err=${ex.javaClass.simpleName}: ${ex.message}",
        )
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

    private companion object {
        const val TAG = "MemoNet"
    }
}
