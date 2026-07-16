package cc.pscly.onememos.update

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import cc.pscly.onememos.domain.update.UpdateDeliveryAction
import cc.pscly.onememos.domain.update.UpdateDeliveryFailure
import cc.pscly.onememos.domain.update.UpdateDeliveryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppUpdateDeliveryLauncherTest {
    @Test
    fun permissionReturn_forwardsResultAndLaunchesManagerFollowUp() {
        val install = UpdateDeliveryAction.InstallApk("content://updates/one-memos.apk")
        val fixture = fixture(permissionGranted = true, followUp = install)
        val open = UpdateDeliveryAction.OpenUnknownSourcesSettings("cc.pscly.onememos")
        fixture.launcher.dispatch(open, activity = null, onResult = fixture.callbackResults::add)
        fixture.launchedIntents.clear()

        fixture.launcher.onHostResumed(activity = null)

        val permission = UpdateDeliveryResult.UnknownSourcesPermissionChanged(granted = true)
        assertEquals(listOf(permission), fixture.managerResults)
        assertEquals(listOf(permission), fixture.callbackResults)
        assertEquals(1, fixture.launchedIntents.size)
        assertEquals(Intent.ACTION_VIEW, fixture.launchedIntents.single().action)
        assertEquals(install.uri, fixture.launchedIntents.single().data.toString())
    }

    @Test
    fun installDispatch_waitsForExternalReturnBeforeReportingResult() {
        val fixture = fixture()
        val install = UpdateDeliveryAction.InstallApk("content://updates/one-memos.apk")

        fixture.launcher.dispatch(install, activity = null, onResult = fixture.callbackResults::add)

        assertEquals(1, fixture.launchedIntents.size)
        assertTrue(fixture.managerResults.isEmpty())
        assertTrue(fixture.callbackResults.isEmpty())
    }

    @Test
    fun installerReturn_forwardsSameResultToManagerAndCaller() {
        val fixture = fixture()
        fixture.launcher.dispatch(
            UpdateDeliveryAction.InstallApk("content://updates/one-memos.apk"),
            activity = null,
            onResult = fixture.callbackResults::add,
        )

        fixture.launcher.onInstallerReturned()

        assertEquals(listOf(UpdateDeliveryResult.InstallerReturned), fixture.managerResults)
        assertEquals(listOf(UpdateDeliveryResult.InstallerReturned), fixture.callbackResults)
    }

    @Test
    fun missingExternalActivity_forwardsTypedFailureOnce() {
        val fixture = fixture()
        fixture.launcher.bindInstallerLauncher {
            throw ActivityNotFoundException("missing")
        }

        fixture.launcher.dispatch(
            UpdateDeliveryAction.InstallApk("content://updates/one-memos.apk"),
            activity = null,
            onResult = fixture.callbackResults::add,
        )

        val failure = UpdateDeliveryResult.Failed(UpdateDeliveryFailure.ACTIVITY_NOT_FOUND)
        assertEquals(listOf(failure), fixture.managerResults)
        assertEquals(listOf(failure), fixture.callbackResults)
        fixture.launcher.onInstallerReturned()
        assertEquals(1, fixture.managerResults.size)
    }

    @Test
    fun hostResume_withoutPendingDelivery_hasNoEffect() {
        val fixture = fixture(permissionGranted = true)

        fixture.launcher.onHostResumed(activity = null)

        assertTrue(fixture.managerResults.isEmpty())
        assertTrue(fixture.callbackResults.isEmpty())
        assertTrue(fixture.launchedIntents.isEmpty())
    }

    private fun fixture(
        permissionGranted: Boolean = false,
        followUp: UpdateDeliveryAction? = null,
    ): Fixture {
        val managerResults = mutableListOf<UpdateDeliveryResult>()
        val callbackResults = mutableListOf<UpdateDeliveryResult>()
        val launchedIntents = mutableListOf<Intent>()
        var nextAction = followUp
        val launcher =
            AppUpdateDeliveryLauncher(
                context = RuntimeEnvironment.getApplication(),
                requestDeliveryAction = { null },
                consumeDeliveryResult = { result ->
                    managerResults += result
                    nextAction.also { nextAction = null }
                },
                canRequestPackageInstalls = { permissionGranted },
            )
        launcher.bindUnknownSourcesLauncher { intent -> launchedIntents += intent }
        launcher.bindInstallerLauncher { intent -> launchedIntents += intent }
        return Fixture(
            launcher = launcher,
            managerResults = managerResults,
            callbackResults = callbackResults,
            launchedIntents = launchedIntents,
        )
    }

    private data class Fixture(
        val launcher: AppUpdateDeliveryLauncher,
        val managerResults: MutableList<UpdateDeliveryResult>,
        val callbackResults: MutableList<UpdateDeliveryResult>,
        val launchedIntents: MutableList<Intent>,
    )
}
