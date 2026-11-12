package com.promocodeapp.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Where the backend lives. Blank values mean "local-only mode". */
data class SupabaseConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.startsWith("https://") && anonKey.isNotBlank()
    val baseUrl: String get() = url.trimEnd('/') + "/"
}

@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch millis after which the access token is no longer valid. */
    val expiresAt: Long,
    val userId: String,
    val email: String,
)

interface SessionStore {
    val session: StateFlow<Session?>
    fun set(session: Session?)
}

/**
 * Persists the session encrypted with an Android Keystore key, so tokens are
 * unreadable outside this app even from a copied data directory.
 */
@Singleton
class EncryptedSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) : SessionStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    override val session: StateFlow<Session?> = state

    @Synchronized
    override fun set(session: Session?) {
        state.value = session
        if (session == null) {
            prefs.edit().remove(KEY).apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val encrypted = cipher.doFinal(Json.encodeToString(Session.serializer(), session).toByteArray())
            val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(encrypted, Base64.NO_WRAP)
            prefs.edit().putString(KEY, blob).apply()
        }.onFailure { Log.w(TAG, "Could not persist session; it will last until the app restarts", it) }
    }

    private fun load(): Session? {
        val blob = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val (iv, data) = blob.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            Json.decodeFromString(Session.serializer(), String(cipher.doFinal(data)))
        }.onFailure {
            // Key lost (e.g. restored onto another device): treat as signed out.
            prefs.edit().remove(KEY).apply()
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val TAG = "SessionStore"
        const val FILE = "session"
        const val KEY = "blob"
        const val ALIAS = "promocodeapp_session"
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
