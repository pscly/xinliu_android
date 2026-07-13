package cc.pscly.onememos.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appUpdateDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_updates")

data class AppUpdatePreferences(
    val ignoredVersionTag: String = "",
    val remindAfterEpochMs: Long = 0L,
    val nextAutomaticCheckAtEpochMs: Long = 0L,
    val downloadId: Long = 0L,
    val downloadFilePath: String = "",
    val downloadRelease: AppUpdateRelease? = null,
)

@Singleton
class AppUpdateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val state: Flow<AppUpdatePreferences> =
        context.appUpdateDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map(::toPreferences)

    suspend fun snapshot(): AppUpdatePreferences = state.first()

    suspend fun setNextAutomaticCheckAt(epochMs: Long) {
        context.appUpdateDataStore.edit { it[Keys.NEXT_AUTOMATIC_CHECK_AT] = epochMs }
    }

    suspend fun setRemindAfter(epochMs: Long) {
        context.appUpdateDataStore.edit { it[Keys.REMIND_AFTER] = epochMs }
    }

    suspend fun setIgnoredVersionTag(tag: String) {
        context.appUpdateDataStore.edit { prefs ->
            if (tag.isBlank()) prefs.remove(Keys.IGNORED_VERSION_TAG) else prefs[Keys.IGNORED_VERSION_TAG] = tag
        }
    }

    suspend fun saveDownload(
        downloadId: Long,
        filePath: String,
        release: AppUpdateRelease,
    ) {
        context.appUpdateDataStore.edit { prefs ->
            prefs[Keys.DOWNLOAD_ID] = downloadId
            prefs[Keys.DOWNLOAD_FILE_PATH] = filePath
            prefs[Keys.DOWNLOAD_TAG] = release.tag
            prefs[Keys.DOWNLOAD_VERSION_NAME] = release.versionName
            prefs[Keys.DOWNLOAD_TITLE] = release.title
            prefs[Keys.DOWNLOAD_NOTES] = release.notes
            prefs[Keys.DOWNLOAD_PUBLISHED_AT] = release.publishedAt
            prefs[Keys.DOWNLOAD_APK_NAME] = release.apkName
            prefs[Keys.DOWNLOAD_APK_URL] = release.apkUrl
            prefs[Keys.DOWNLOAD_APK_SIZE] = release.apkSizeBytes
            prefs[Keys.DOWNLOAD_SHA256] = release.sha256
        }
    }

    suspend fun clearDownload() {
        context.appUpdateDataStore.edit { prefs ->
            prefs.remove(Keys.DOWNLOAD_ID)
            prefs.remove(Keys.DOWNLOAD_FILE_PATH)
            prefs.remove(Keys.DOWNLOAD_TAG)
            prefs.remove(Keys.DOWNLOAD_VERSION_NAME)
            prefs.remove(Keys.DOWNLOAD_TITLE)
            prefs.remove(Keys.DOWNLOAD_NOTES)
            prefs.remove(Keys.DOWNLOAD_PUBLISHED_AT)
            prefs.remove(Keys.DOWNLOAD_APK_NAME)
            prefs.remove(Keys.DOWNLOAD_APK_URL)
            prefs.remove(Keys.DOWNLOAD_APK_SIZE)
            prefs.remove(Keys.DOWNLOAD_SHA256)
        }
    }

    private fun toPreferences(prefs: Preferences): AppUpdatePreferences {
        val release =
            if ((prefs[Keys.DOWNLOAD_TAG]).isNullOrBlank()) {
                null
            } else {
                AppUpdateRelease(
                    tag = prefs[Keys.DOWNLOAD_TAG].orEmpty(),
                    versionName = prefs[Keys.DOWNLOAD_VERSION_NAME].orEmpty(),
                    title = prefs[Keys.DOWNLOAD_TITLE].orEmpty(),
                    notes = prefs[Keys.DOWNLOAD_NOTES].orEmpty(),
                    publishedAt = prefs[Keys.DOWNLOAD_PUBLISHED_AT].orEmpty(),
                    apkName = prefs[Keys.DOWNLOAD_APK_NAME].orEmpty(),
                    apkUrl = prefs[Keys.DOWNLOAD_APK_URL].orEmpty(),
                    apkSizeBytes = prefs[Keys.DOWNLOAD_APK_SIZE] ?: 0L,
                    sha256 = prefs[Keys.DOWNLOAD_SHA256].orEmpty(),
                )
            }
        return AppUpdatePreferences(
            ignoredVersionTag = prefs[Keys.IGNORED_VERSION_TAG].orEmpty(),
            remindAfterEpochMs = prefs[Keys.REMIND_AFTER] ?: 0L,
            nextAutomaticCheckAtEpochMs = prefs[Keys.NEXT_AUTOMATIC_CHECK_AT] ?: 0L,
            downloadId = prefs[Keys.DOWNLOAD_ID] ?: 0L,
            downloadFilePath = prefs[Keys.DOWNLOAD_FILE_PATH].orEmpty(),
            downloadRelease = release,
        )
    }

    private object Keys {
        val IGNORED_VERSION_TAG = stringPreferencesKey("ignored_version_tag")
        val REMIND_AFTER = longPreferencesKey("remind_after_epoch_ms")
        val NEXT_AUTOMATIC_CHECK_AT = longPreferencesKey("next_automatic_check_at_epoch_ms")
        val DOWNLOAD_ID = longPreferencesKey("download_id")
        val DOWNLOAD_FILE_PATH = stringPreferencesKey("download_file_path")
        val DOWNLOAD_TAG = stringPreferencesKey("download_tag")
        val DOWNLOAD_VERSION_NAME = stringPreferencesKey("download_version_name")
        val DOWNLOAD_TITLE = stringPreferencesKey("download_title")
        val DOWNLOAD_NOTES = stringPreferencesKey("download_notes")
        val DOWNLOAD_PUBLISHED_AT = stringPreferencesKey("download_published_at")
        val DOWNLOAD_APK_NAME = stringPreferencesKey("download_apk_name")
        val DOWNLOAD_APK_URL = stringPreferencesKey("download_apk_url")
        val DOWNLOAD_APK_SIZE = longPreferencesKey("download_apk_size")
        val DOWNLOAD_SHA256 = stringPreferencesKey("download_sha256")
    }
}
