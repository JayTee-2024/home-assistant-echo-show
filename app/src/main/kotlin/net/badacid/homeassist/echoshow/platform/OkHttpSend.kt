package net.badacid.homeassist.echoshow.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import net.badacid.homeassist.echoshow.core.HttpAnswer
import net.badacid.homeassist.echoshow.core.HttpRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val JSON = "application/json; charset=utf-8".toMediaType()

/**
 * The client for everything sent to Home Assistant. It never follows a redirect: a 307 or 308 would send the
 * same request, with the password or a token in it, on to whatever address the answer named.
 */
fun homeAssistantClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

/**
 * The app's HTTP sender (core's HttpSend) on OkHttp. Every failure, including an address OkHttp cannot
 * use, comes out as an IOException, which core treats as "nothing answered".
 */
class OkHttpSend(private val client: OkHttpClient) : suspend (HttpRequest) -> HttpAnswer {
    override suspend fun invoke(request: HttpRequest): HttpAnswer {
        val url = request.url.toHttpUrlOrNull() ?: throw IOException("Not a usable address")
        val json = request.jsonBody
        val form = request.formBody
        val body: RequestBody? = when {
            json != null -> json.toString().toRequestBody(JSON)
            form != null -> FormBody.Builder().apply { form.forEach { (name, value) -> add(name, value) } }.build()
            request.method == "GET" -> null
            else -> ByteArray(0).toRequestBody()
        }
        val call = try {
            client.newCall(Request.Builder().url(url).method(request.method, body).build())
        } catch (problem: IllegalArgumentException) {
            throw IOException("Not a usable request", problem)
        }
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { continuation.resume(HttpAnswer(it.code, it.body?.string() ?: "")) }
                        } catch (e: IOException) {
                            continuation.resumeWithException(e)
                        }
                    }
                },
            )
        }
    }
}
