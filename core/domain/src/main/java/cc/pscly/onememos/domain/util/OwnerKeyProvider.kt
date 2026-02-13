package cc.pscly.onememos.domain.util

interface OwnerKeyProvider {
    fun currentOwnerKeyOrNull(): String?
}
