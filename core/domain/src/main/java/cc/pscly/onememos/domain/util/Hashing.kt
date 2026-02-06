package cc.pscly.onememos.domain.util

import java.security.MessageDigest

object Hashing {
    fun sha256Hex(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val out = md.digest(bytes)
        val sb = StringBuilder(out.size * 2)
        for (b in out) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}

