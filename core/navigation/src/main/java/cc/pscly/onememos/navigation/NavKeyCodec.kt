package cc.pscly.onememos.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

sealed interface NavKeyDecodeResult {
    data class Success(val key: OneMemosNavKey) : NavKeyDecodeResult

    data class Rejected(val reason: NavKeyDecodeRejection) : NavKeyDecodeResult
}

enum class NavKeyDecodeRejection {
    UNKNOWN_TYPE,
    MALFORMED_PAYLOAD,
    INVALID_ARGUMENT,
}

interface NavKeyCodec {
    fun encode(key: OneMemosNavKey): String

    fun decode(value: String): NavKeyDecodeResult
}

class JsonNavKeyCodec(
    private val json: Json = defaultJson,
) : NavKeyCodec {
    override fun encode(key: OneMemosNavKey): String = json.encodeToString(OneMemosNavKey.serializer(), key)

    override fun decode(value: String): NavKeyDecodeResult {
        val raw = value.trim()
        if (raw.isEmpty()) {
            return NavKeyDecodeResult.Rejected(NavKeyDecodeRejection.MALFORMED_PAYLOAD)
        }
        return try {
            val key = json.decodeFromString(OneMemosNavKey.serializer(), raw)
            NavKeyDecodeResult.Success(key)
        } catch (error: Exception) {
            val message = buildString {
                var current: Throwable? = error
                while (current != null) {
                    append(current.message.orEmpty())
                    append(' ')
                    append(current.javaClass.name)
                    append(' ')
                    current = current.cause
                }
            }
            when {
                message.contains("Polymorphic", ignoreCase = true) ||
                    message.contains("Class discriminator", ignoreCase = true) ||
                    message.contains("is not registered", ignoreCase = true) ||
                    message.contains("Serializer for subclass", ignoreCase = true) ||
                    message.contains("Unknown type", ignoreCase = true) ->
                    NavKeyDecodeResult.Rejected(NavKeyDecodeRejection.UNKNOWN_TYPE)

                // SerializationException 继承 IllegalArgumentException，必须先于 require 失败处理。
                error is kotlinx.serialization.SerializationException ->
                    NavKeyDecodeResult.Rejected(NavKeyDecodeRejection.MALFORMED_PAYLOAD)

                error is IllegalArgumentException ->
                    // TodoItemKey init require / blank fields
                    NavKeyDecodeResult.Rejected(NavKeyDecodeRejection.INVALID_ARGUMENT)

                else ->
                    NavKeyDecodeResult.Rejected(NavKeyDecodeRejection.MALFORMED_PAYLOAD)
            }
        }
    }

    companion object {
        val defaultJson: Json =
            Json {
                ignoreUnknownKeys = false
                encodeDefaults = true
                classDiscriminator = "type"
                serializersModule =
                    SerializersModule {
                        polymorphic(OneMemosNavKey::class) {
                            subclass(HomeKey::class)
                            subclass(CollectionsKey::class)
                            subclass(TodoKey::class)
                            subclass(TodoItemKey::class)
                            subclass(ProfileKey::class)
                            subclass(ArchivedKey::class)
                            subclass(SettingsHubKey::class)
                            subclass(WelcomeKey::class)
                            subclass(EditorKey::class)
                            subclass(ShareCardKey::class)
                            subclass(AuthKey::class)
                            subclass(AccountSyncSettingsKey::class)
                            subclass(AccountManagementSettingsKey::class)
                            subclass(AdvancedSyncSettingsKey::class)
                            subclass(RecordEditingSettingsKey::class)
                            subclass(ReminderCalendarSettingsKey::class)
                            subclass(StorageOfflineSettingsKey::class)
                            subclass(AppearanceInteractionSettingsKey::class)
                            subclass(AboutAdvancedSettingsKey::class)
                        }
                    }
            }
    }
}
