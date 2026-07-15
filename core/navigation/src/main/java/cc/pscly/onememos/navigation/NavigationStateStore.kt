package cc.pscly.onememos.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface NavigationRestoreResult {
    data class Restored(val snapshot: NavigationSnapshot) : NavigationRestoreResult

    data class Rejected(val reason: NavigationRestoreRejection) : NavigationRestoreResult
}

enum class NavigationRestoreRejection {
    MALFORMED_PAYLOAD,
    INVALID_STRUCTURE,
    UNKNOWN_KEY,
}

interface NavigationStateStore {
    fun save(snapshot: NavigationSnapshot): String

    fun restore(encoded: String): NavigationRestoreResult
}

class JsonNavigationStateStore(
    private val json: Json = defaultJson,
    private val navKeyCodec: NavKeyCodec = JsonNavKeyCodec(json = JsonNavKeyCodec.defaultJson),
) : NavigationStateStore {
    override fun save(snapshot: NavigationSnapshot): String {
        val wire =
            WireSnapshot(
                activeSection = snapshot.activeSection.name,
                stacks =
                    TopLevelSection.entries.associate { section ->
                        section.name to
                            snapshot.stacks.getValue(section).map { key ->
                                navKeyCodec.encode(key)
                            }
                    },
            )
        return json.encodeToString(WireSnapshot.serializer(), wire)
    }

    override fun restore(encoded: String): NavigationRestoreResult {
        val raw = encoded.trim()
        if (raw.isEmpty()) {
            return NavigationRestoreResult.Rejected(NavigationRestoreRejection.MALFORMED_PAYLOAD)
        }
        val wire =
            try {
                json.decodeFromString(WireSnapshot.serializer(), raw)
            } catch (_: Exception) {
                return NavigationRestoreResult.Rejected(NavigationRestoreRejection.MALFORMED_PAYLOAD)
            }

        val stacks = linkedMapOf<TopLevelSection, List<OneMemosNavKey>>()
        for (section in TopLevelSection.entries) {
            val encodedStack = wire.stacks[section.name]
            if (encodedStack == null || encodedStack.isEmpty()) {
                return NavigationRestoreResult.Rejected(NavigationRestoreRejection.INVALID_STRUCTURE)
            }
            val decoded = mutableListOf<OneMemosNavKey>()
            for (item in encodedStack) {
                when (val result = navKeyCodec.decode(item)) {
                    is NavKeyDecodeResult.Success -> decoded += result.key
                    is NavKeyDecodeResult.Rejected ->
                        return NavigationRestoreResult.Rejected(NavigationRestoreRejection.UNKNOWN_KEY)
                }
            }
            if (decoded.first() != section.root) {
                return NavigationRestoreResult.Rejected(NavigationRestoreRejection.INVALID_STRUCTURE)
            }
            stacks[section] = decoded
        }

        val active =
            runCatching { TopLevelSection.valueOf(wire.activeSection) }.getOrNull()
                ?: return NavigationRestoreResult.Rejected(NavigationRestoreRejection.INVALID_STRUCTURE)

        val snapshot =
            NavigationSnapshot(
                activeSection = active,
                stacks = stacks,
            )
        return if (snapshot.isStructurallyValid()) {
            NavigationRestoreResult.Restored(snapshot.normalized())
        } else {
            NavigationRestoreResult.Rejected(NavigationRestoreRejection.INVALID_STRUCTURE)
        }
    }

    @Serializable
    private data class WireSnapshot(
        val activeSection: String,
        val stacks: Map<String, List<String>>,
    )

    companion object {
        val defaultJson: Json =
            Json {
                ignoreUnknownKeys = false
                encodeDefaults = true
            }
    }
}
