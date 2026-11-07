package app.stegvis.wear

import java.io.IOException

sealed class Access {
    object None : Access()
    object PhoneExpired : Access()
    data class Token(val value: String, val fromPhone: Boolean) : Access()
}

enum class SyncResult { OK, NOT_SIGNED_IN, PHONE_EXPIRED, NO_PERMISSION, OFFLINE, FAILED }

sealed class Fetch {
    data class Ok(val state: ServerState, val raw: String) : Fetch()
    object NotLoggedIn : Fetch()
    object PhoneExpired : Fetch()
    object Offline : Fetch()
    object Failed : Fetch()
}

/** Decides which token to use, refreshes the watch's own session, and talks to the server. Pure Kotlin, testable with a fake Http. */
class Coordinator(
    private val auth: SupabaseAuth,
    private val api: StegvisApi,
    private val vault: Vault,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun access(): Access {
        val s = vault.session ?: return Access.None
        if (s.fromPhone) {
            // The phone owns this token; the watch never refreshes it (refresh rotation could sign the phone out).
            return if (s.expiresAt > clock()) Access.Token(s.accessToken, true) else Access.PhoneExpired
        }
        if (needsRefresh(s, clock())) {
            when (val r = auth.refresh(s)) {
                is AuthResult.Ok -> { vault.session = r.session; return Access.Token(r.session.accessToken, false) }
                is AuthResult.Failed -> if (r.reason == AuthFailure.SESSION_EXPIRED) {
                    vault.session = null
                    return Access.None
                }
                // Offline or rate limited: keep the old token and try again later.
            }
        }
        return Access.Token(s.accessToken, false)
    }

    /** Refreshes the watch's own session after a 401. Returns the new token or null. */
    private fun refreshAfter401(): String? {
        val s = vault.session ?: return null
        if (s.fromPhone) return null
        return when (val r = auth.refresh(s)) {
            is AuthResult.Ok -> { vault.session = r.session; r.session.accessToken }
            is AuthResult.Failed -> {
                if (r.reason == AuthFailure.SESSION_EXPIRED) vault.session = null
                null
            }
        }
    }

    fun fetchState(today: String): Fetch {
        val token = when (val a = access()) {
            Access.None -> return Fetch.NotLoggedIn
            Access.PhoneExpired -> return Fetch.PhoneExpired
            is Access.Token -> a.value
        }
        return try {
            var (code, body) = api.state(token)
            if (code == 401) {
                val fresh = refreshAfter401() ?: return if (vault.session == null) Fetch.NotLoggedIn else Fetch.PhoneExpired
                val retry = api.state(fresh)
                code = retry.first; body = retry.second
            }
            if (code !in 200..299) return Fetch.Failed
            Fetch.Ok(parseState(body, today), body)
        } catch (e: IOException) {
            Fetch.Offline
        } catch (e: Exception) {
            Fetch.Failed
        }
    }

    /** Sends today's steps. Returns true when the server accepted them. */
    fun syncSteps(steps: Int): Boolean = syncResult(steps) == SyncResult.OK

    fun syncResult(steps: Int): SyncResult {
        val token = when (val a = access()) {
            Access.None -> return SyncResult.NOT_SIGNED_IN
            Access.PhoneExpired -> return SyncResult.PHONE_EXPIRED
            is Access.Token -> a.value
        }
        return try {
            var code = api.postSteps(token, steps)
            if (code == 401) {
                val fresh = refreshAfter401()
                    ?: return if (vault.session == null) SyncResult.NOT_SIGNED_IN else SyncResult.PHONE_EXPIRED
                code = api.postSteps(fresh, steps)
            }
            if (code in 200..299) SyncResult.OK else SyncResult.FAILED
        } catch (e: IOException) {
            SyncResult.OFFLINE
        }
    }

    fun login(user: String, password: String): AuthFailure? =
        when (val r = auth.login(user, password)) {
            is AuthResult.Ok -> { vault.session = r.session; null }
            is AuthResult.Failed -> r.reason
        }

    /** Creates a name-only account on the server, then signs in with it. Validates the name and password before any network call. */
    fun signUp(username: String, password: String): AuthFailure? {
        val name = Username.clean(username) ?: return AuthFailure.INVALID_NAME
        if (password.length < 8 || password.length > 72) return AuthFailure.INVALID_PASSWORD
        val code = try {
            api.signup(name, password)
        } catch (e: IOException) {
            return AuthFailure.OFFLINE
        }
        return when (code) {
            in 200..299 -> login(name, password)
            400 -> AuthFailure.INVALID_NAME
            409 -> AuthFailure.USERNAME_TAKEN
            429 -> AuthFailure.RATE_LIMITED
            503 -> AuthFailure.UNAVAILABLE
            else -> AuthFailure.OTHER
        }
    }

    fun signOut() = vault.clearAll()
}
