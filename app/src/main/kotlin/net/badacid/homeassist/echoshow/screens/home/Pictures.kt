package net.badacid.homeassist.echoshow.screens.home

import android.graphics.Bitmap

/** Fetches a picture from Home Assistant, decoded to about the given size. Null when it cannot. The app gives one on OkHttp. */
fun interface PictureLoader {
    suspend fun load(url: String, widthPx: Int, heightPx: Int): Bitmap?
}
