package net.badacid.homeassist.echoshow.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import java.io.File

/**
 * Draws `view` at the Echo's screen size and saves it as docs/screens/<name>.png, so the screens can be
 * looked at without the Echo. Use in a Robolectric test with @GraphicsMode(NATIVE).
 */
fun snapshot(view: View, name: String, width: Int = 1280, height: Int = 800): File {
    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
    view.layout(0, 0, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    // Tests run with the module as the working directory.
    val file = File("../docs/screens/$name.png")
    file.parentFile?.mkdirs()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}
