package app.stegvis.wear

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The only network seam. Throws IOException when there is no connection. */
interface Http {
    fun send(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String>
}

class UrlHttp : Http {
    override fun send(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }
}

enum class AuthFailure { BAD_CREDENTIALS, INVALID_NAME, INVALID_PASSWORD, USERNAME_TAKEN, UNAVAILABLE, RATE_LIMITED, OFFLINE, SESSION_EXPIRED, OTHER }

sealed class AuthResult {
    data class Ok(val session: Session) : AuthResult()
    data class Failed(val reason: AuthFailure) : AuthResult()
}

/** Supabase password and refresh grants over REST, no SDK. */
class SupabaseAuth(
    private val http: Http,
    private val baseUrl: String,
    private val anonKey: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private fun headers() = mapOf("apikey" to anonKey, "Content-Type" to "application/json")

    fun login(userOrEmail: String, password: String): AuthResult {
        val email = if (userOrEmail.contains('@')) userOrEmail.trim()
        else Username.clean(userOrEmail)?.let(Username::toEmail) ?: return AuthResult.Failed(AuthFailure.INVALID_NAME)
        val body = JSONObject().put("email", email).put("password", password).toString()
        return grant("password", body, AuthFailure.BAD_CREDENTIALS)
    }

    fun refresh(s: Session): AuthResult {
        val token = s.refreshToken ?: return AuthResult.Failed(AuthFailure.SESSION_EXPIRED)
        val body = JSONObject().put("refresh_token", token).toString()
        return grant("refresh_token", body, AuthFailure.SESSION_EXPIRED)
    }

    private fun grant(type: String, body: String, rejected: AuthFailure): AuthResult {
        val url = "$baseUrl/auth/v1/token?grant_type=$type"
        return try {
            val (code, text) = http.send("POST", url, headers(), body)
            when (code) {
                in 200..299 -> try {
                    AuthResult.Ok(parseTokenResponse(text, clock()))
                } catch (e: Exception) {
                    AuthResult.Failed(AuthFailure.OTHER)
                }
                400, 401, 403 -> AuthResult.Failed(rejected)
                429 -> AuthResult.Failed(AuthFailure.RATE_LIMITED)
                503 -> AuthResult.Failed(AuthFailure.UNAVAILABLE)
                else -> AuthResult.Failed(AuthFailure.OTHER)
            }
        } catch (e: IOException) {
            AuthResult.Failed(AuthFailure.OFFLINE)
        }
    }
}

/** Stegvis REST calls. Responses are returned as (status, body) so the caller decides what a 401 means. */
class StegvisApi(private val http: Http, private val base: String = API_BASE) {
    fun state(token: String): Pair<Int, String> =
        http.send("GET", "$base/api/state", mapOf("Authorization" to "Bearer $token"), null)

    /** POST /api/username-signup. Returns the status code: 200 created, 409 taken, 429 too many, 503 not configured. */
    fun signup(username: String, password: String): Int {
        val body = JSONObject().put("username", username).put("password", password).toString()
        return http.send("POST", "$base/api/username-signup", mapOf("Content-Type" to "application/json"), body).first
    }

    fun postSteps(token: String, steps: Int): Int {
        val body = JSONObject().put("steps", steps).toString()
        return http.send(
            "POST", "$base/api/steps",
            mapOf("Authorization" to "Bearer $token", "Content-Type" to "application/json"),
            body,
        ).first
    }
}
