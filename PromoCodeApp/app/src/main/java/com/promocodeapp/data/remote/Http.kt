package com.promocodeapp.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit

val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = true
    encodeDefaults = true
    coerceInputValues = true
}

/** Every Supabase request carries the project key; user requests add the user's JWT. */
class SupabaseHeadersInterceptor(
    private val config: SupabaseConfig,
    private val sessions: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder().header("apikey", config.anonKey)
        if (request.header("Authorization") == null) {
            builder.header("Authorization", "Bearer ${sessions.session.value?.accessToken ?: config.anonKey}")
        }
        return chain.proceed(builder.build())
    }
}

/** Exchanges the refresh token for a new session, at most once per stale token. */
class TokenRefresher(
    private val authApi: AuthApi,
    private val sessions: SessionStore,
    private val clock: Clock,
) {
    /**
     * @param staleAccessToken the token that was just rejected, if any.
     * @return a usable session, or null when the user has to sign in again.
     */
    @Synchronized
    fun refreshBlocking(staleAccessToken: String?): Session? {
        val current = sessions.session.value ?: return null
        // Another thread already refreshed while we waited for the lock.
        if (staleAccessToken != null && current.accessToken != staleAccessToken) return current
        return try {
            val response = runBlocking { authApi.refresh(RefreshBody(current.refreshToken)) }
            response.toSession(clock, fallbackEmail = current.email)?.also(sessions::set)
        } catch (e: HttpException) {
            // The refresh token itself was rejected: the session is over.
            if (e.code() in 400..403) sessions.set(null)
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Refreshes ahead of time when the token is about to lapse. */
    fun freshSession(): Session? {
        val current = sessions.session.value ?: return null
        return if (current.expiresAt - clock.millis() < 60_000) refreshBlocking(current.accessToken) ?: current else current
    }
}

class TokenAuthenticator(private val refresher: TokenRefresher) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // already retried once
        val sent = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        val session = refresher.refreshBlocking(sent) ?: return null
        if (session.accessToken == sent) return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${session.accessToken}")
            .build()
    }
}

fun AuthResponse.toSession(clock: Clock, fallbackEmail: String = ""): Session? {
    val access = accessToken ?: return null
    val refresh = refreshToken ?: return null
    val userId = user?.id ?: id ?: return null
    return Session(
        accessToken = access,
        refreshToken = refresh,
        expiresAt = clock.millis() + (expiresIn ?: 3600) * 1000,
        userId = userId,
        email = user?.email ?: email ?: fallbackEmail,
    )
}

object SupabaseHttp {
    fun baseClient(config: SupabaseConfig, sessions: SessionStore, debug: Boolean): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(SupabaseHeadersInterceptor(config, sessions))
            .apply {
                if (debug) {
                    // Headers only, with credentials masked: bodies contain coupon codes.
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                            redactHeader("apikey")
                        }
                    )
                }
            }
            .build()

    fun retrofit(config: SupabaseConfig, client: OkHttpClient): Retrofit = Retrofit.Builder()
        // An unconfigured build never makes requests; the placeholder keeps Retrofit happy.
        .baseUrl(if (config.isConfigured) config.baseUrl else "https://unconfigured.invalid/")
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
}

/** Best human-readable message for a failed backend call. */
fun Throwable.userMessage(): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val parsed = body?.let { runCatching { ApiJson.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull() }
        parsed?.msg ?: parsed?.errorDescription ?: parsed?.message ?: parsed?.error
            ?: "The server rejected the request (${code()})."
    }
    is IOException -> "Can't reach the server. Check your connection and try again."
    else -> message ?: "Something went wrong."
}
