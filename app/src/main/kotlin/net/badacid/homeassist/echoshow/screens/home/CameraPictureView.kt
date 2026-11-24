package net.badacid.homeassist.echoshow.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.badacid.homeassist.echoshow.core.EntityState
import net.badacid.homeassist.echoshow.devices.camera.pictureUrl
import net.badacid.homeassist.echoshow.devices.camera.readCamera
import net.badacid.homeassist.echoshow.screens.Theme

/**
 * A camera's still picture, fetched again `refreshMs` after the last one arrived (a port of the LG app's
 * CameraPicture.tsx). A new picture replaces the old only once it has arrived, so the card never flashes
 * empty; until a first one arrives it says "Loading…" or "No picture".
 */
class CameraPictureView(
    context: Context,
    private val scope: CoroutineScope,
    private val loader: PictureLoader,
    private val homeAddress: String,
    private val refreshMs: Long = 10_000,
) : FrameLayout(context) {
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val words = Theme.text(context, "Loading…", Theme.CARD_LINE_SP, Theme.TEXT_DIM).apply { gravity = Gravity.CENTER }
    private var entity: EntityState? = null
    private var shown: Bitmap? = null
    private var loading: Job? = null

    init {
        background = Theme.roundedPanel(context, Theme.SURFACE_RAISED, 12f)
        clipToOutline = true
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(words, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun show(camera: EntityState) {
        val pathChanged = entity?.let { readCamera(it).picturePath } != readCamera(camera).picturePath
        entity = camera
        if (!readCamera(camera).isAvailable) {
            stop()
            shown = null
            image.setImageBitmap(null)
            words.text = "Unavailable"
            words.visibility = VISIBLE
            return
        }
        if (pathChanged || loading == null) restart()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        entity?.let { if (loading == null) restart() }
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun stop() {
        loading?.cancel()
        loading = null
    }

    private fun restart() {
        stop()
        loading = scope.launch {
            var count = 0
            while (true) {
                val camera = entity ?: return@launch
                val url = pictureUrl(homeAddress, readCamera(camera).picturePath, count)
                val picture = url?.let { loader.load(it, width.coerceAtLeast(320), height.coerceAtLeast(240)) }
                if (picture != null) {
                    shown = picture
                    image.setImageBitmap(picture)
                    words.visibility = GONE
                } else if (shown == null) {
                    words.text = "No picture"
                    words.visibility = VISIBLE
                }
                // The next refresh is planned only once this load has ended: a cloud camera can take longer
                // than the refresh time to send a picture.
                delay(refreshMs)
                count += 1
            }
        }
    }
}
