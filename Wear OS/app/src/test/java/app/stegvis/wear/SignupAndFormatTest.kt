package app.stegvis.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SignupAndFormatTest {
    private val now = 1_000_000L
    private val tokenBody = """{"access_token":"A1","refresh_token":"R1","expires_in":3600}"""

    private fun coordinator(http: Http, vault: Vault = Vault(MemoryStore())) =
        Coordinator(SupabaseAuth(http, "https://x.supabase.co", "anon", clock = { now }), StegvisApi(http), vault, clock = { now })

    @Test fun signupCreatesAccountThenLogsIn() {
        val http = FakeHttp(mutableListOf(200 to """{"created":true}""", 200 to tokenBody))
        val vault = Vault(MemoryStore())
        assertNull(coordinator(http, vault).signUp("Åsa_1", "secret123"))
        assertTrue(http.calls[0].second.endsWith("/api/username-signup"))
        assertNotNull(vault.session)
        assertTrue(http.calls[1].third!!.contains("+asa_1@users.nrnworld.one"))
    }

    @Test fun signupBadNameOrPasswordNeverTouchesNetwork() {
        val http = FakeHttp(mutableListOf())
        val c = coordinator(http)
        assertEquals(AuthFailure.INVALID_NAME, c.signUp("a", "secret123"))
        assertEquals(AuthFailure.INVALID_NAME, c.signUp("bad name", "secret123"))
        assertEquals(AuthFailure.INVALID_PASSWORD, c.signUp("anna", "short"))
        assertEquals(AuthFailure.INVALID_PASSWORD, c.signUp("anna", "x".repeat(73)))
        assertEquals(0, http.calls.size)
    }

    @Test fun signupStatusesMapToReasons() {
        assertEquals(AuthFailure.USERNAME_TAKEN, coordinator(FakeHttp(mutableListOf(409 to "{}"))).signUp("anna", "secret123"))
        assertEquals(AuthFailure.RATE_LIMITED, coordinator(FakeHttp(mutableListOf(429 to "{}"))).signUp("anna", "secret123"))
        assertEquals(AuthFailure.UNAVAILABLE, coordinator(FakeHttp(mutableListOf(503 to "{}"))).signUp("anna", "secret123"))
        assertEquals(AuthFailure.OFFLINE, coordinator(FakeHttp(mutableListOf(IOException("x")))).signUp("anna", "secret123"))
    }

    @Test fun loginServiceUnavailableIs503() {
        val http = FakeHttp(mutableListOf(503 to "{}"))
        assertEquals(AuthFailure.UNAVAILABLE, coordinator(http).login("anna", "pw"))
    }

    @Test fun syncResultReportsReasons() {
        val vault = Vault(MemoryStore())
        assertEquals(SyncResult.NOT_SIGNED_IN, coordinator(FakeHttp(mutableListOf()), vault).syncResult(5))
        vault.session = Session("t", "r", now + 3_600_000, false)
        assertEquals(SyncResult.OK, coordinator(FakeHttp(mutableListOf(200 to "{}")), vault).syncResult(5))
        assertEquals(SyncResult.OFFLINE, coordinator(FakeHttp(mutableListOf(IOException("x"))), vault).syncResult(5))
        assertEquals(SyncResult.FAILED, coordinator(FakeHttp(mutableListOf(500 to "{}")), vault).syncResult(5))
    }

    @Test fun steps401WithoutRefreshTokenKeepsSessionAsPhoneExpiredOrNone() {
        val vault = Vault(MemoryStore())
        vault.session = Session("t", null, now + 60_000, fromPhone = true)
        assertEquals(SyncResult.PHONE_EXPIRED, coordinator(FakeHttp(mutableListOf(401 to "{}")), vault).syncResult(5))
    }

    @Test fun formatsStepsAndKm() {
        assertEquals("12 345", formatSteps(12_345))
        assertEquals("999", formatSteps(999))
        assertEquals("0", formatSteps(0))
        assertEquals("3,8", formatKm(5000, "sv"))
        assertEquals("3.8", formatKm(5000, "en"))
    }

    @Test fun languageFollowsSystemOnlyForSvDa() {
        assertEquals("sv", effectiveLanguage("system", "sv"))
        assertEquals("da", effectiveLanguage("system", "da"))
        assertEquals("en", effectiveLanguage("system", "de"))
        assertEquals("da", effectiveLanguage("da", "sv"))
    }

    @Test fun celebrationDayAndAccountNameAreStoredAndSignOutClearsAccount() {
        val vault = Vault(MemoryStore())
        assertNull(vault.celebratedDay)
        vault.celebratedDay = "2026-11-07"
        vault.accountName = "anna"
        vault.goal = 12_500
        vault.clearAll()
        assertNull(vault.accountName)
        assertEquals("2026-11-07", vault.celebratedDay) // kept: signing out must not replay today's celebration
        assertEquals(12_500, vault.goal)
    }

    @Test fun shortNameCutsAfterFourteenCharacters() {
        assertEquals("Anna", shortName("Anna"))
        assertEquals("Abcdefghijklm…", shortName("Abcdefghijklmnopqrst"))
    }

    @Test fun ownSession401WhileOfflineIsOfflineNotPhoneExpired() {
        val vault = Vault(MemoryStore())
        vault.session = Session("t", "r", now + 3_600_000, false)
        // state 401, then the refresh call fails with no network
        val c = coordinator(FakeHttp(mutableListOf(401 to "{}", IOException("x"))), vault)
        assertEquals(Fetch.Offline, c.fetchState("2026-11-07"))
        vault.session = Session("t", "r", now + 3_600_000, false)
        assertEquals(SyncResult.OFFLINE, coordinator(FakeHttp(mutableListOf(401 to "{}", 429 to "{}")), vault).syncResult(5))
        assertNotNull(vault.session) // a transient failure never signs the user out
    }

    @Test fun ownSessionRejectedRefreshSignsOut() {
        val vault = Vault(MemoryStore())
        vault.session = Session("t", "r", now + 3_600_000, false)
        assertEquals(Fetch.NotLoggedIn, coordinator(FakeHttp(mutableListOf(401 to "{}", 400 to "{}")), vault).fetchState("2026-11-07"))
        assertNull(vault.session)
    }

    @Test fun compactStepsRounds() {
        assertEquals("950", compactSteps(950))
        assertEquals("12k", compactSteps(12_300))
        assertEquals("100k", compactSteps(100_000))
    }
}
