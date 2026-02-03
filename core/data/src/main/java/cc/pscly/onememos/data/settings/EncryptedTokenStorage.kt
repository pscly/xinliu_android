package cc.pscly.onememos.data.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStorage {
    private val prefs by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        EncryptedSharedPreferences.create(
            FILE_NAME,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getToken(): String = prefs.getString(KEY_TOKEN, "").orEmpty()

    override fun setToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    companion object {
        private const val FILE_NAME = "one_memos_secure"
        private const val KEY_TOKEN = "token"
    }
}
