package app.stegvis.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Fake transport: records each request and answers from a queue. */
class FakeHttp(private val answers: MutableList<Any>) : Http {
    val calls = mutableListOf<Triple<String, String, String?>>()
    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        calls += Triple(method, url, body)
        val next = answers.removeAt(0)
        if (next is IOException) throw next
        @Suppress("UNCHECKED_CAST")
        return next as Pair<Int, String>
    }
}

class AuthTest {
    private val now = 1_000_000L
    private val tokenBody = """{"access_token":"A1","refresh_token":"R1","expires_in":3600}"""

    private fun auth(http: Http) = SupabaseAuth(http, "https://example.supabase.co", "anon", clock = { now })

    @Test fun parsesTokenResponse() {
        val s = parseTokenResponse(tokenBody, now)
        assertEquals("A1", s.accessToken)
        assertEquals("R1", s.refreshToken)
        assertEquals(now + 3_600_000L, s.expiresAt)
        assertFalse(s.fromPhone)
    }

    @Test fun tokenResponseWithoutAccessTokenIsRejected() {
        val http = FakeHttp(mutableListOf(200 to """{"error":"x"}"""))
        val r = auth(http).login("anna", "pw")
        assertEquals(AuthResult.Failed(AuthFailure.OTHER), r)
    }

    @Test fun passwordLoginMapsUsernameToEmailAndSendsAnonKey() {
        val http = FakeHttp(mutableListOf(200 to tokenBody))
        val r = auth(http).login("Åsa", "secret")
        assertTrue(r is AuthResult.Ok)
        val (method, url, body) = http.calls.single()
        assertEquals("POST", method)
        assertEquals("https://example.supabase.co/auth/v1/token?grant_type=password", url)
        assertTrue(body!!.contains("\"email\":\"+asa@users.nrnworld.one\""))
    }

    @Test fun emailLoginIsSentAsTyped() {
        val http = FakeHttp(mutableListOf(200 to tokenBody))
        auth(http).login("Anna@Example.se", "pw")
        assertTrue(http.calls.single().third!!.contains("\"email\":\"Anna@Example.se\""))
    }

    @Test fun invalidNameIsRejectedBeforeNetwork() {
        val http = FakeHttp(mutableListOf())
        assertEquals(AuthResult.Failed(AuthFailure.INVALID_NAME), auth(http).login("ab", "pw"))
        assertTrue(http.calls.isEmpty())
    }

    @Test fun errorStatusesMapToReasons() {
        assertEquals(AuthResult.Failed(AuthFailure.BAD_CREDENTIALS), auth(FakeHttp(mutableListOf(400 to "{}"))).login("anna", "x"))
        assertEquals(AuthResult.Failed(AuthFailure.RATE_LIMITED), auth(FakeHttp(mutableListOf(429 to "{}"))).login("anna", "x"))
        assertEquals(AuthResult.Failed(AuthFailure.OTHER), auth(FakeHttp(mutableListOf(500 to "{}"))).login("anna", "x"))
    }

    @Test fun networkErrorMapsToOffline() {
        val r = auth(FakeHttp(mutableListOf(IOException("no route")))).login("anna", "x")
        assertEquals(AuthResult.Failed(AuthFailure.OFFLINE), r)
    }

    @Test fun refreshUsesRefreshGrant() {
        val http = FakeHttp(mutableListOf(200 to """{"access_token":"A2","refresh_token":"R2","expires_in":60}"""))
        val old = Session("A1", "R1", now - 1, fromPhone = false)
        val r = auth(http).refresh(old) as AuthResult.Ok
        assertEquals("A2", r.session.accessToken)
        assertEquals("R2", r.session.refreshToken)
        assertTrue(http.calls.single().second.endsWith("grant_type=refresh_token"))
        assertTrue(http.calls.single().third!!.contains("\"refresh_token\":\"R1\""))
    }

    @Test fun refreshRejectedMeansSessionExpired() {
        val r = auth(FakeHttp(mutableListOf(400 to "{}"))).refresh(Session("A", "R", now, false))
        assertEquals(AuthResult.Failed(AuthFailure.SESSION_EXPIRED), r)
    }

    @Test fun needsRefreshOnlyForOwnSessionNearExpiry() {
        assertTrue(needsRefresh(Session("A", "R", now + 30_000, false), now))
        assertFalse(needsRefresh(Session("A", "R", now + 600_000, false), now))
        assertFalse(needsRefresh(Session("A", null, now, false), now))
        // The phone's token is never refreshed on the watch.
        assertFalse(needsRefresh(Session("A", null, now, true), now))
    }

    @Test fun jwtExpiryIsReadFromPayload() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":2000000000}""".toByteArray())
        val token = "eyJhbGciOiJIUzI1NiJ9.$payload.sig"
        assertEquals(2_000_000_000_000L, jwtExpiry(token))
        assertNull(jwtExpiry("not-a-jwt"))
    }

    @Test fun coordinatorRefreshesOwnSessionAndStoresNewTokens() {
        val vault = Vault(MemoryStore())
        vault.session = Session("old", "R1", now - 1, false)
        // Refresh answer, then the steps POST with the new token.
        val http = FakeHttp(mutableListOf(
            200 to """{"access_token":"new","refresh_token":"R2","expires_in":3600}""",
            200 to "{}",
        ))
        val api = StegvisApi(http)
        val coordinator = Coordinator(auth(http), api, vault, clock = { now })
        assertTrue(coordinator.syncSteps(1234))
        assertEquals("new", vault.session!!.accessToken)
        assertEquals("R2", vault.session!!.refreshToken)
        assertTrue(http.calls.last().third!!.contains("\"steps\":1234"))
    }

    @Test fun phoneTokenIsUsedButNeverRefreshed() {
        val vault = Vault(MemoryStore())
        vault.session = Session("phoneTok", null, now + 60_000, fromPhone = true)
        val http = FakeHttp(mutableListOf(401 to "{}"))
        val coordinator = Coordinator(auth(http), StegvisApi(http), vault, clock = { now })
        assertEquals(false, coordinator.syncSteps(10)) // 401 with a phone token: no refresh call is made
        assertEquals(1, http.calls.size)
        assertNotNull(vault.session) // the phone's token stays; the phone decides when to replace it
    }

    @Test fun expiredPhoneTokenReportsPhoneExpired() {
        val vault = Vault(MemoryStore())
        vault.savePhoneToken("", now)
        vault.session = Session("t", null, now - 1, fromPhone = true)
        val coordinator = Coordinator(auth(FakeHttp(mutableListOf())), StegvisApi(FakeHttp(mutableListOf())), vault, clock = { now })
        assertEquals(Access.PhoneExpired, coordinator.access())
    }
}
