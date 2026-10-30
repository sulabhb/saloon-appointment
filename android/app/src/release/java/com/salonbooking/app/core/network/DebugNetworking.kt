package com.salonbooking.app.core.network

import okhttp3.OkHttpClient

/** Release builds add no network logging. */
object DebugNetworking {
    fun configure(builder: OkHttpClient.Builder) = Unit
}
