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
    /** The "Last recording" or "Updating picture…" label for the camera, or null for none. */
    private val labelOf: ((EntityState) -> String?)? = null,
    /** True to show the whole picture (full screen), false to fill the card. */
    fitWhole: Boolean = false,
) : FrameLayout(context) {
    private val label = Theme.text(context, "", Theme.SMALL_SP).apply {
        background = Theme.roundedPanel(context, Theme.BACKDROP, 8f)
        setPadding(Theme.dp(context, 10f), Theme.dp(context, 4f), Theme.dp(context, 10f), Theme.dp(context, 4f))
        visibility = GONE
    }
    private val image = ImageView(context).apply { scaleType = if (fitWhole) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP }
    private val words = Theme.text(context, "Loading…", Theme.CARD_LINE_SP, Theme.TEXT_DIM).apply { gravity = Gravity.CENTER }
    private var entity: EntityState? = null
    private var shown: Bitmap? = null
    private var loading: Job? = null

    init {
        background = Theme.roundedPanel(context, Theme.SURFACE_RAISED, 12f)
        clipToOutline = true
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(words, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = Theme.dp(context, 12f)
            bottomMargin = Theme.dp(context, 12f)
        })
    }

    /** Works the label out again, as after live video stops. */
    fun refreshLabel() {
        val text = entity?.let { labelOf?.invoke(it) }
        label.text = text.orEmpty()
        label.visibility = if (text == null) GONE else VISIBLE
    }

    fun show(camera: EntityState) {
        val pathChanged = entity?.let { readCamera(it).picturePath } != readCamera(camera).picturePath
        entity = camera
        refreshLabel()
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

    /** Stops fetching. A later show() or attach starts again. */
    fun stop() {
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
                refreshLabel()
                delay(refreshMs)
                count += 1
            }
        }
    }
}
