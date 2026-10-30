package com.salonbooking.app.core.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Debug builds log request lines and status codes (no bodies, which may hold personal data).
 * Booking management tokens in URLs are masked, as they are credentials.
 */
object DebugNetworking {
    private val TOKEN_IN_PATH = Regex("/manage/[^/?\\s]+")

    fun configure(builder: OkHttpClient.Builder) {
        val logger = HttpLoggingInterceptor { message -> Log.d("http", message.replace(TOKEN_IN_PATH, "/manage/***")) }
        builder.addInterceptor(logger.setLevel(HttpLoggingInterceptor.Level.BASIC))
    }
}
