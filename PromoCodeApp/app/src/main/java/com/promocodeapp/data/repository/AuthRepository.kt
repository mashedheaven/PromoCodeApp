package com.promocodeapp.data.repository

import androidx.room.withTransaction
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.remote.AuthApi
import com.promocodeapp.data.remote.CredentialsBody
import com.promocodeapp.data.remote.EmailBody
import com.promocodeapp.data.remote.Session
import com.promocodeapp.data.remote.SessionStore
import com.promocodeapp.data.remote.SupabaseConfig
import com.promocodeapp.data.remote.toSession
import com.promocodeapp.data.remote.userMessage
import com.promocodeapp.data.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthResult {
    data object SignedIn : AuthResult
    /** The account exists but its e-mail address must be confirmed first. */
    data object ConfirmationRequired : AuthResult
    data class Failed(val message: String) : AuthResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val config: SupabaseConfig,
    private val authApi: AuthApi,
    private val sessions: SessionStore,
    private val db: AppDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val changes: LocalChangeListener,
) {
    val isAvailable: Boolean get() = config.isConfigured
    val session: StateFlow<Session?> get() = sessions.session

    suspend fun signIn(email: String, password: String): AuthResult = attempt {
        val session = authApi.signIn(CredentialsBody(email.trim(), password)).toSession(clock, email.trim())
            ?: return@attempt AuthResult.Failed("Unexpected response from the server.")
        adopt(session)
        AuthResult.SignedIn
    }

    suspend fun signUp(email: String, password: String): AuthResult = attempt {
        val session = authApi.signUp(CredentialsBody(email.trim(), password)).toSession(clock, email.trim())
            ?: return@attempt AuthResult.ConfirmationRequired
        adopt(session)
        AuthResult.SignedIn
    }

    suspend fun sendPasswordReset(email: String): AuthResult = attempt {
        authApi.recover(EmailBody(email.trim()))
        AuthResult.ConfirmationRequired
    }

    /** Ends the session. Data stays on the device and simply stops syncing. */
    suspend fun signOut() {
        val current = sessions.session.value ?: return
        sessions.set(null)
        // Best effort: the local session is gone either way.
        runCatching { authApi.signOut("Bearer ${current.accessToken}") }
            .onFailure { if (it is CancellationException) throw it }
    }

    private suspend fun adopt(session: Session) {
        val previous = settings.lastAccountId()
        db.withTransaction {
            if (previous != null && previous != session.userId) {
                // Another account's data must not leak into this one.
                db.couponDao().clear()
                db.placeDao().clear()
                db.membershipDao().clear()
                db.placeAlertStateDao().clear()
            } else {
                // Everything on the device, including data created before signing
                // in, is uploaded; deletions the server never saw are forgotten.
                db.couponDao().purgeAllTombstones()
                db.placeDao().purgeAllTombstones()
                db.membershipDao().purgeAllTombstones()
                db.couponDao().markAllDirty()
                db.placeDao().markAllDirty()
                db.membershipDao().markAllDirty()
            }
        }
        settings.clearSyncState()
        settings.setLastAccountId(session.userId)
        sessions.set(session)
        changes.onLocalChange()
    }

    private suspend fun attempt(block: suspend () -> AuthResult): AuthResult {
        if (!config.isConfigured) return AuthResult.Failed("Cloud sync isn't set up in this build.")
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AuthResult.Failed(e.userMessage())
        }
    }
}
