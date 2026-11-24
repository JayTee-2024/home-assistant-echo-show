package net.badacid.homeassist.echoshow.screens

import android.view.Gravity
import android.widget.LinearLayout
import net.badacid.homeassist.echoshow.devices.DEVICE_LOGIC
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi")
class IconViewScreenshotTest {
    @Test
    fun drawsEveryDeviceIcon() {
        val context = RuntimeEnvironment.getApplication()
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.BACKGROUND)
            for (path in DEVICE_LOGIC.map { it.icon } + listOf(Icons.SETTINGS, Icons.CLOSE, Icons.CHECK)) {
                addView(IconView(context, path, Theme.AMBER), LinearLayout.LayoutParams(80, 80).apply { setMargins(12, 12, 12, 12) })
            }
        }
        val file = snapshot(row, "icons", width = 1400, height = 110)
        assertTrue(file.length() > 1000)
    }
}
