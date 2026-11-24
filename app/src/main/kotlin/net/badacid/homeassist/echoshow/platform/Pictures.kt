package net.badacid.homeassist.echoshow.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** No camera still is anywhere near this size; something larger is not read into the Echo's memory. */
const val MAX_PICTURE_BYTES = 10L * 1024 * 1024

/** The body's bytes, or null when there are more than `maxBytes` of them. Reads no further than the limit. */
fun readLimited(body: okhttp3.ResponseBody, maxBytes: Long): ByteArray? {
    if (body.contentLength() > maxBytes) return null
    val source = body.source()
    // True when at least one byte more than the limit is there.
    if (source.request(maxBytes + 1)) return null
    return source.readByteArray()
}

/**
 * Fetches a picture and decodes it to roughly the given size (a power-of-two reduction, so a large camera
 * picture does not fill the Echo's memory). Null when it cannot. The address can hold an access token, so
 * it is never logged.
 */
suspend fun loadPicture(client: OkHttpClient, address: String, widthPx: Int, heightPx: Int, accessToken: String?): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(address).apply { accessToken?.let { header("Authorization", "Bearer $it") } }.build()
            client.newCall(request).execute().use { answer ->
                if (!answer.isSuccessful) return@withContext null
                val bytes = answer.body?.let { readLimited(it, MAX_PICTURE_BYTES) } ?: return@withContext null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= widthPx && bounds.outHeight / (sample * 2) >= heightPx) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null // A malformed address.
        }
    }
