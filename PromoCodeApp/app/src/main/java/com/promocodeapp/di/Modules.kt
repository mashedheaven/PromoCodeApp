package com.promocodeapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.promocodeapp.BuildConfig
import com.promocodeapp.data.local.AppDatabase
import com.promocodeapp.data.remote.AuthApi
import com.promocodeapp.data.remote.EncryptedSessionStore
import com.promocodeapp.data.remote.RestApi
import com.promocodeapp.data.remote.SessionStore
import com.promocodeapp.data.remote.SupabaseConfig
import com.promocodeapp.data.remote.SupabaseHttp
import com.promocodeapp.data.remote.TokenAuthenticator
import com.promocodeapp.data.remote.TokenRefresher
import com.promocodeapp.data.repository.LocalChangeListener
import com.promocodeapp.work.WorkScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/** Lives as long as the process; for work that must outlast a screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun supabaseConfig(): SupabaseConfig = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    @Provides
    @Singleton
    fun authApi(config: SupabaseConfig, sessions: SessionStore): AuthApi =
        SupabaseHttp.retrofit(config, SupabaseHttp.baseClient(config, sessions, BuildConfig.DEBUG))
            .create(AuthApi::class.java)

    @Provides
    @Singleton
    fun restApi(config: SupabaseConfig, sessions: SessionStore, authApi: AuthApi, clock: Clock): RestApi {
        val client = SupabaseHttp.baseClient(config, sessions, BuildConfig.DEBUG).newBuilder()
            .authenticator(TokenAuthenticator(TokenRefresher(authApi, sessions, clock)))
            .build()
        return SupabaseHttp.retrofit(config, client).create(RestApi::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun sessionStore(impl: EncryptedSessionStore): SessionStore

    @Binds
    abstract fun localChangeListener(impl: WorkScheduler): LocalChangeListener
}
