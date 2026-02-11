package cc.pscly.onememos.worker

import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class WorkerRetryPolicyTest {
    @Test
    fun http_401_403_do_not_retry() {
        assertHttpRetry(code = 401, expectedRetry = false)
        assertHttpRetry(code = 403, expectedRetry = false)
    }

    @Test
    fun http_404_do_not_retry() {
        assertHttpRetry(code = 404, expectedRetry = false)
    }

    @Test
    fun http_429_retries() {
        assertHttpRetry(code = 429, expectedRetry = true)
    }

    @Test
    fun http_500_retries() {
        assertHttpRetry(code = 500, expectedRetry = true)
    }

    @Test
    fun classify_http_code_404_do_not_retry() {
        val c = WorkerRetryPolicy.classifyHttpCode(code = 404, runAttemptCount = 0)
        assertEquals(404, c.httpCode)
        assertFalse(c.retry)
        assertTrue(c.userMessage.isNotBlank())
    }

    @Test
    fun classify_http_code_attempt_cap_disables_retry() {
        val c = WorkerRetryPolicy.classifyHttpCode(
            code = 429,
            runAttemptCount = WorkerRetryPolicy.ATTEMPT_CAP,
        )
        assertEquals(429, c.httpCode)
        assertFalse(c.retry)
        assertTrue(c.userMessage.contains("已达重试上限"))
    }

    @Test
    fun ioexception_retries() {
        val d = WorkerRetryPolicy.classify(IOException("boom"), runAttemptCount = 0)
        val c = d as WorkerRetryPolicy.Decision.Classified
        assertTrue(c.retry)
        assertEquals(0, c.httpCode)
        assertTrue(c.userMessage.isNotBlank())
    }

    @Test
    fun attempt_cap_disables_retry_but_keeps_message_for_http() {
        val d = WorkerRetryPolicy.classify(httpException(500), runAttemptCount = WorkerRetryPolicy.ATTEMPT_CAP)
        val c = d as WorkerRetryPolicy.Decision.Classified
        assertFalse(c.retry)
        assertEquals(500, c.httpCode)
        assertTrue(c.userMessage.contains("已达重试上限"))
    }

    @Test
    fun attempt_cap_disables_retry_but_keeps_message_for_io() {
        val d = WorkerRetryPolicy.classify(IOException("boom"), runAttemptCount = WorkerRetryPolicy.ATTEMPT_CAP)
        val c = d as WorkerRetryPolicy.Decision.Classified
        assertFalse(c.retry)
        assertEquals(0, c.httpCode)
        assertTrue(c.userMessage.contains("已达重试上限"))
    }

    @Test
    fun cancellation_exception_must_propagate() {
        val d = WorkerRetryPolicy.classify(CancellationException("cancel"), runAttemptCount = 0)
        assertTrue(d is WorkerRetryPolicy.Decision.PropagateCancellation)
    }

    private fun assertHttpRetry(code: Int, expectedRetry: Boolean) {
        val d = WorkerRetryPolicy.classify(httpException(code), runAttemptCount = 0)
        val c = d as WorkerRetryPolicy.Decision.Classified
        assertEquals(code, c.httpCode)
        assertEquals(expectedRetry, c.retry)
        assertTrue(c.userMessage.isNotBlank())
    }

    private fun httpException(code: Int): HttpException {
        val body = "".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(code, body))
    }
}
