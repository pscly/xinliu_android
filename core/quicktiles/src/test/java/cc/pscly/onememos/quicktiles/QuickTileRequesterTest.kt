package cc.pscly.onememos.quicktiles

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, shadows = [ShadowStatusBarManager::class])
class QuickTileRequesterTest {
    @Test
    fun request_returnsCompletedOrPlatformUnavailable() =
        runBlocking {
            val requester = AndroidQuickTileRequester(RuntimeEnvironment.getApplication())
            val result = requester.request(QuickTileKind.QUICK_CAPTURE)
            assertTrue(
                "unexpected result=$result",
                result is QuickTileRequestResult.Completed ||
                    result is QuickTileRequestResult.PlatformUnavailable,
            )
        }

    @Test
    fun screenshotTile_returnsCompletedOrPlatformUnavailable() =
        runBlocking {
            val requester = AndroidQuickTileRequester(RuntimeEnvironment.getApplication())
            val result = requester.request(QuickTileKind.SCREENSHOT_CAPTURE)
            assertTrue(
                "unexpected result=$result",
                result is QuickTileRequestResult.Completed ||
                    result is QuickTileRequestResult.PlatformUnavailable,
            )
        }
}
