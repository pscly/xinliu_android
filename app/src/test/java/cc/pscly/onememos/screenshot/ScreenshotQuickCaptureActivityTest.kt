package cc.pscly.onememos.screenshot

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreenshotQuickCaptureActivityTest {
    @Test
    fun saveBitmapToCacheFile_writesPngToCache() =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            val now = LocalDateTime.of(2026, 2, 11, 1, 2, 3)

            val executedThreadName = AtomicReference<String?>(null)
            val baseDispatcher =
                Executors.newSingleThreadExecutor { r -> Thread(r, "io-test") }
                    .asCoroutineDispatcher()

            val recordingDispatcher =
                object : CoroutineDispatcher() {
                    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                        baseDispatcher.dispatch(context) {
                            executedThreadName.set(Thread.currentThread().name)
                            block.run()
                        }
                    }
                }

            val file =
                try {
                    ScreenshotQuickCaptureActivity.saveBitmapToCacheFile(
                        cacheDir = context.cacheDir,
                        bitmap = bitmap,
                        now = now,
                        ioDispatcher = recordingDispatcher,
                    )
                } finally {
                    baseDispatcher.close()
                }

            assertTrue(file.exists())
            assertTrue(file.length() > 0)
            assertTrue(file.parentFile?.name == "screenshots")
            assertTrue(file.name == "screenshot-2026-02-11T01-02-03.png")
            assertTrue(bitmap.isRecycled)
            assertTrue(executedThreadName.get()?.startsWith("io-test") == true)
        }
}
