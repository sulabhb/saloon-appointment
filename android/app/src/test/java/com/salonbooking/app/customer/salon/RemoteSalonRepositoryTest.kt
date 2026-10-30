package com.salonbooking.app.customer.salon

import com.salonbooking.app.core.network.LocationSummaryDto
import com.salonbooking.app.core.network.NetworkModule
import com.salonbooking.app.core.network.PublicApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Checks the app understands the backend's real JSON and error responses. */
class RemoteSalonRepositoryTest {

    private val server = MockWebServer()
    private lateinit var repository: RemoteSalonRepository

    @Before
    fun setUp() {
        server.start()
        val api = NetworkModule
            .createRetrofit(server.url("/").toString(), OkHttpClient(), NetworkModule.json())
            .create(PublicApi::class.java)
        repository = RemoteSalonRepository(api)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test
    fun `parses a salon and ignores fields it does not know`() = runTest {
        respond(200, """
            {"slug":"glow-hair","displayName":"Glow Hair","description":null,"accentColor":"#9C5B6B","newField":1,
             "locations":[{"slug":"sydney-cbd","name":"Sydney CBD","suburb":"Sydney","state":"NSW",
                           "phone":null,"timeZone":"Australia/Sydney"}]}
        """.trimIndent())

        val result = repository.loadSalon("glow-hair") as SalonResult.Found

        assertEquals("Glow Hair", result.salon.displayName)
        assertEquals("#9C5B6B", result.salon.accentColor)
        assertEquals(
            listOf(LocationSummaryDto("sydney-cbd", "Sydney CBD", "Sydney", "NSW", null, "Australia/Sydney")),
            result.salon.locations,
        )
        assertEquals("/api/v1/public/b/glow-hair", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `maps backend errors`() = runTest {
        respond(404, """{"error":{"code":"NOT_FOUND","message":"Business not found."}}""")
        respond(503, """{"error":{"code":"BUSINESS_UNAVAILABLE","message":"Unavailable."}}""")
        respond(500, "{}")

        assertEquals(SalonResult.NotFound, repository.loadSalon("a-salon"))
        assertEquals(SalonResult.Unavailable, repository.loadSalon("a-salon"))
        assertEquals(SalonResult.NetworkError, repository.loadSalon("a-salon"))
    }

    @Test
    fun `reports network failures`() = runTest {
        server.close()

        assertEquals(SalonResult.NetworkError, repository.loadSalon("glow-hair"))
    }
}
