package com.salonbooking.app.core.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** The outcome of an API call, with the server's error code and message kept for the screen. */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /** [code] is the API's stable error code, e.g. SLOT_UNAVAILABLE; [message] is written for people. */
    data class Failed(val status: Int, val code: String, val message: String) : ApiResult<Nothing>

    data object Offline : ApiResult<Nothing>
}

/** Runs [call], turning HTTP errors into [ApiResult.Failed] and connection problems into [ApiResult.Offline]. */
suspend fun <T> apiCall(json: Json, call: suspend () -> T): ApiResult<T> = try {
    ApiResult.Ok(call())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    val body = e.response()?.errorBody()?.string()
    val error = body?.let { runCatching { json.decodeFromString<ApiErrorDto>(it).error }.getOrNull() }
    ApiResult.Failed(e.code(), error?.code ?: "HTTP_${e.code()}", error?.message ?: "Something went wrong. Please try again.")
} catch (e: IOException) {
    ApiResult.Offline
}
