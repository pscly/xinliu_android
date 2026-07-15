package cc.pscly.onememos.settings

import android.content.ActivityNotFoundException
import cc.pscly.onememos.domain.settings.SettingsCapabilityError
import java.io.IOException
import java.net.UnknownHostException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SettingsCapabilityErrorMapperTest {
    @Test
    fun http401And403_mapToAuthenticationExpired() {
        assertEquals(
            SettingsCapabilityError.AuthenticationExpired,
            SettingsCapabilityErrorMapper.map(http(401)),
        )
        assertEquals(
            SettingsCapabilityError.AuthenticationExpired,
            SettingsCapabilityErrorMapper.map(http(403)),
        )
    }

    @Test
    fun otherHttp_andIo_andSecurity_andPlatform_andWork_andUnknown() {
        assertEquals(
            SettingsCapabilityError.InvalidInput,
            SettingsCapabilityErrorMapper.map(http(400)),
        )
        assertEquals(
            SettingsCapabilityError.NetworkUnavailable,
            SettingsCapabilityErrorMapper.map(http(502)),
        )
        assertEquals(
            SettingsCapabilityError.NetworkUnavailable,
            SettingsCapabilityErrorMapper.map(IOException("socket closed with secret body")),
        )
        assertEquals(
            SettingsCapabilityError.NetworkUnavailable,
            SettingsCapabilityErrorMapper.map(UnknownHostException("secret.host")),
        )
        assertEquals(
            SettingsCapabilityError.PermissionDenied,
            SettingsCapabilityErrorMapper.map(SecurityException("calendar denied detail")),
        )
        assertEquals(
            SettingsCapabilityError.PlatformUnavailable,
            SettingsCapabilityErrorMapper.map(ActivityNotFoundException("com.secret.Activity")),
        )
        assertEquals(
            SettingsCapabilityError.PlatformUnavailable,
            SettingsCapabilityErrorMapper.map(IllegalStateException("WorkManager not initialized WorkInfo=ENQUEUED")),
        )
        val unknown = SettingsCapabilityErrorMapper.map(IllegalArgumentException("boom-secret"))
        assertTrue(unknown is SettingsCapabilityError.Unknown)
        assertEquals("IllegalArgumentException", (unknown as SettingsCapabilityError.Unknown).diagnosticCode)
    }

    @Test
    fun mappedErrors_doNotLeakExceptionMessageOrHttpBody() {
        val body = """{"token":"secret-token","message":"leak-me"}"""
        val mapped = SettingsCapabilityErrorMapper.map(http(401, body))
        val text = mapped.toString()
        assertFalse(text.contains("secret-token"))
        assertFalse(text.contains("leak-me"))
        assertFalse(text.contains("WorkInfo"))
    }

    private fun http(code: Int, body: String = "error-body"): HttpException {
        val responseBody = body.toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, responseBody))
    }
}
