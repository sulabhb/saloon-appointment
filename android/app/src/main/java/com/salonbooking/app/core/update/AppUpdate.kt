package com.salonbooking.app.core.update

import com.salonbooking.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.Response

/** The server's version rules for the app (APP-4). */
@Serializable
data class AppConfigDto(val minAndroidVersionCode: Int, val latestAndroidVersionCode: Int, val androidStoreUrl: String)

/**
 * Whether this build is too old to keep using. Set from the app config at launch, or from any
 * request the server refuses with 426. Holds the store link to send people to.
 */
@Singleton
class AppUpdate(private val versionCode: Int) {

    @Inject constructor() : this(BuildConfig.VERSION_CODE)

    private val required = MutableStateFlow<String?>(null)

    /** The store link when an update is required, otherwise null. */
    val updateRequired: StateFlow<String?> = required.asStateFlow()

    fun apply(config: AppConfigDto) {
        if (versionCode < config.minAndroidVersionCode) required.value = config.androidStoreUrl
    }

    fun refusedByServer() {
        if (required.value == null) required.value = STORE_URL
    }

    companion object {
        const val STORE_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

        /** Sent on every request so the server can tell builds apart, e.g. `android/0.1.0+1`. */
        fun clientVersion(name: String = BuildConfig.VERSION_NAME, code: Int = BuildConfig.VERSION_CODE) = "android/$name+$code"
    }
}

/** Adds the app version to every request and notices when the server says this build is too old. */
class ClientVersionInterceptor @Inject constructor(private val update: AppUpdate) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request().newBuilder().header("X-Client-Version", AppUpdate.clientVersion()).build())
        if (response.code == 426) update.refusedByServer()
        return response
    }
}
