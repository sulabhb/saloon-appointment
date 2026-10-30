package com.salonbooking.app.staff

import com.salonbooking.app.core.network.ApiResult
import com.salonbooking.app.core.network.NetworkModule
import com.salonbooking.app.core.network.StaffApi
import com.salonbooking.app.staff.session.SessionState
import com.salonbooking.app.staff.session.SessionToken
import com.salonbooking.app.staff.session.StaffAuthInterceptor
import com.salonbooking.app.staff.session.StaffSession
import com.salonbooking.app.staff.session.TokenVault
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

/** Sign-in, the bearer token on staff calls only, and forgetting everything on sign-out or a 401. */
class StaffSessionTest {

    private class MemoryVault(var stored: String? = null) : TokenVault {
        override fun read() = stored
        override fun write(token: String) { stored = token }
        override fun clear() { stored = null }
    }

    private val server = MockWebServer()
    private val vault = MemoryVault()
    private lateinit var token: SessionToken
    private lateinit var session: StaffSession

    private val me = """{"user":{"id":"u1","email":"jo@luna-hair.test","fullName":"Jo Rivera","platformAdmin":false},
        "businesses":[{"businessId":"b1","slug":"luna-hair-co","displayName":"Luna Hair Co","role":"OWNER"}],
        "selected":{"businessId":"b1","role":"OWNER","locationIds":null,"staffId":null}}"""

    @Before
    fun setUp() {
        server.start()
        token = SessionToken(vault)
        val json = NetworkModule.json()
        val client = OkHttpClient.Builder().addInterceptor(StaffAuthInterceptor(token)).build()
        val api = NetworkModule.createRetrofit(server.url("/").toString(), client, json).create(StaffApi::class.java)
        session = StaffSession(api, token, json)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "") =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    @Test
    fun `signing in keeps the token in the vault and sends it on staff calls only`() = runTest {
        respond(200, """{"token":"secret","me":$me}""")
        val result = session.signIn("jo@luna-hair.test", "pw", "Pixel")
        assertTrue(result is ApiResult.Ok)
        assertEquals("secret", vault.stored)
        assertTrue(session.state.value is SessionState.SignedIn)
        val login = server.takeRequest()
        assertNull("no token on the sign-in call itself", login.headers["Authorization"])
        val body = login.body?.utf8().orEmpty()
        assertTrue("sends the client type the server requires: $body", body.contains("\"client\":\"android\""))

        respond(200, me)
        session.restore()
        assertEquals("Bearer secret", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `wrong passwords show the server's message`() = runTest {
        respond(401, """{"error":{"code":"INVALID_CREDENTIALS","message":"Email or password is not correct."}}""")
        val result = session.signIn("jo@luna-hair.test", "nope", "Pixel")
        assertEquals("Email or password is not correct.", (result as ApiResult.Failed).message)
        assertNull(vault.stored)
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    @Test
    fun `a rejected token is flagged and forgotten`() = runTest {
        vault.stored = "old"
        token = SessionToken(vault)
        setUpWith(token)
        respond(401, """{"error":{"code":"UNAUTHENTICATED","message":"Sign in again."}}""")
        session.restore()
        assertTrue(token.rejected.value)
        assertNull(vault.stored)
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    @Test
    fun `signing out forgets the token even when the server cannot be reached`() = runTest {
        respond(200, """{"token":"secret","me":$me}""")
        session.signIn("jo@luna-hair.test", "pw", "Pixel")
        server.close()
        session.signOut()
        assertNull(vault.stored)
        assertEquals(SessionState.SignedOut, session.state.value)
    }

    private fun setUpWith(newToken: SessionToken) {
        val json = NetworkModule.json()
        val client = OkHttpClient.Builder().addInterceptor(StaffAuthInterceptor(newToken)).build()
        val api = NetworkModule.createRetrofit(server.url("/").toString(), client, json).create(StaffApi::class.java)
        session = StaffSession(api, newToken, json)
    }
}
