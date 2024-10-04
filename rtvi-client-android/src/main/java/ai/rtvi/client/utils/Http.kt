package ai.rtvi.client.utils

import ai.rtvi.client.result.Future
import ai.rtvi.client.result.HttpError
import ai.rtvi.client.result.withPromise
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val httpClient = OkHttpClient.Builder()
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()

internal fun post(
    thread: ThreadRef,
    url: String,
    body: RequestBody,
    customHeaders: List<Pair<String, String>>,
    responseHandler: ((InputStream) -> Unit)? = null
): Future<String, HttpError> {

    return withPromise(thread) { promise ->
        thread {
            val result = try {
                val request = Request.Builder().url(url).post(body).apply {
                    customHeaders.forEach {
                        header(it.first, it.second)
                    }
                }.build()

                httpClient.newCall(request).execute()

            } catch (e: Exception) {
                promise.resolveErr(HttpError.ExceptionThrown(url, e))
                return@thread
            }

            if (result.isSuccessful && responseHandler != null) {

                val resultBody = result.body

                if (resultBody == null) {
                    promise.resolveErr(HttpError.MissingResponseBody(url))
                    return@thread
                }

                try {
                    responseHandler(resultBody.byteStream())
                    promise.resolveOk("")
                } catch (e: Exception) {
                    promise.resolveErr(HttpError.ExceptionThrown(url, e))
                }

                return@thread
            }

            val resultBody = try {
                result.body?.string()
            } catch (e: Exception) {
                promise.resolveErr(HttpError.ExceptionThrown(url, e))
                return@thread
            }

            if (result.code != 200) {
                promise.resolveErr(HttpError.BadStatusCode(url, result.code, resultBody))
                return@thread
            }

            if (resultBody == null) {
                promise.resolveErr(HttpError.MissingResponseBody(url))
                return@thread
            }

            promise.resolveOk(resultBody)
        }
    }
}

// Incomplete implementation of SSE which handles messages sent by an RTVI backend
fun InputStream.parseServerSentEvents(action: (String) -> Unit) {

    val reader = BufferedReader(InputStreamReader(this))

    while (true) {
        val nextLine = reader.readLine() ?: return

        if (nextLine.isBlank()) {
            continue
        }

        if (nextLine.startsWith("data:")) {
            action(Base64.decode(nextLine.substring(5).trim(), Base64.DEFAULT).decodeToString())
        }
    }
}