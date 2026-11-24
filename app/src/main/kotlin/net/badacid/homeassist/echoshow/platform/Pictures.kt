package net.badacid.homeassist.echoshow.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

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
                val bytes = answer.body?.bytes() ?: return@withContext null
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
