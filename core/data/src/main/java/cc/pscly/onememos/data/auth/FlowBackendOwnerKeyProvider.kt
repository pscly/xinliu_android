package cc.pscly.onememos.data.auth

import cc.pscly.onememos.domain.util.Hashing
import cc.pscly.onememos.domain.util.OwnerKeyProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlowBackendOwnerKeyProvider @Inject constructor(
    private val flowBackendCredentialStorage: FlowBackendCredentialStorage,
) : OwnerKeyProvider {
    override fun currentOwnerKeyOrNull(): String? {
        val cred = flowBackendCredentialStorage.get() ?: return null
        val username = cred.username.trim()
        if (username.isBlank()) return null
        return Hashing.sha256Hex(username.lowercase())
    }
}
