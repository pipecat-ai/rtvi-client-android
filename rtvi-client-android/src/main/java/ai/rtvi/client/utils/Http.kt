package ai.rtvi.client.utils

import ai.rtvi.client.result.Future
import ai.rtvi.client.result.HttpError
import ai.rtvi.client.result.withPromise
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
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
    customHeaders: List<Pair<String, String>>
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
                promise.resolveErr(HttpError.ExceptionThrown(e))
                return@thread
            }

            val resultBody = try {
                result.body?.string()
            } catch (e: Exception) {
                promise.resolveErr(HttpError.ExceptionThrown(e))
                return@thread
            }

            if (result.code != 200) {
                promise.resolveErr(HttpError.BadStatusCode(result.code, resultBody))
                return@thread
            }

            if (resultBody == null) {
                promise.resolveErr(HttpError.MissingResponseBody)
                return@thread
            }

            promise.resolveOk(resultBody)
        }
    }
}