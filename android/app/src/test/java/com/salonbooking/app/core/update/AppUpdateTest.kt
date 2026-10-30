package com.salonbooking.app.core.update

import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.NetworkModule
import com.salonbooking.app.core.network.PublicApi
import com.salonbooking.app.core.network.apiCall
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The version header, the launch check and the server's 426 (APP-4). */
class AppUpdateTest {

    private val server = MockWebServer()
    private val json = NetworkModule.json()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun api(update: AppUpdate): PublicApi {
        val client = OkHttpClient.Builder().addInterceptor(ClientVersionInterceptor(update)).build()
        return NetworkModule.createRetrofit(server.url("/").toString(), client, json).create(PublicApi::class.java)
    }

    private fun config(min: Int) = AppConfigDto(min, min + 1, "https://store.example/app")

    @Test
    fun `builds below the minimum must update and others carry on`() {
        val old = AppUpdate(versionCode = 3)
        old.apply(config(min = 4))
        assertEquals("https://store.example/app", old.updateRequired.value)

        val current = AppUpdate(versionCode = 4)
        current.apply(config(min = 4))
        assertNull(current.updateRequired.value)
    }

    @Test
    fun `every request says which build it comes from`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "application/json")
            .body("""{"minAndroidVersionCode":1,"latestAndroidVersionCode":1,"androidStoreUrl":"x"}""").build())
        api(AppUpdate(versionCode = 1)).appConfig()
        val header = server.takeRequest().headers["X-Client-Version"]!!
        assertTrue(header, Regex("""^android/[^+\s]+\+\d+$""").matches(header))
        assertEquals("android/0.3.0+12", AppUpdate.clientVersion("0.3.0", 12))
    }

    @Test
    fun `a 426 from any call asks for the update`() = runTest {
        val update = AppUpdate(versionCode = 1)
        server.enqueue(MockResponse.Builder().code(426).addHeader("Content-Type", "application/json")
            .body("""{"error":{"code":"APP_UPDATE_REQUIRED","message":"Please update the app to keep booking."}}""").build())
        val result = apiCall(json) { api(update).business("luna-hair-co") }
        assertEquals("APP_UPDATE_REQUIRED", (result as ApiResult.Failed).code)
        assertEquals(AppUpdate.STORE_URL, update.updateRequired.value)
    }
}
