package cc.pscly.onememos.quicktiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OverlayPermissionGatewayTest {
    @Test
    fun gateway_contractExposesPackageAndGrantState() {
        val gateway =
            object : OverlayPermissionGateway {
                override val packageName: String = "cc.pscly.onememos"

                override fun isGranted(): Boolean = false
            }
        assertEquals("cc.pscly.onememos", gateway.packageName)
        assertFalse(gateway.isGranted())
    }
}
