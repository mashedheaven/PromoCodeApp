package com.promocodeapp.di

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.promocodeapp.data.api.SupabaseApiService
import com.promocodeapp.data.db.AppDatabase
import com.promocodeapp.data.db.dao.*
import com.promocodeapp.data.repository.AuthRepositoryImpl
import com.promocodeapp.data.repository.CouponRepositoryImpl
import com.promocodeapp.data.repository.SyncRepositoryImpl
import com.promocodeapp.domain.repository.AuthRepository
import com.promocodeapp.domain.repository.CouponRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Singleton
    @Provides
    fun provideCouponDao(database: AppDatabase): CouponDao = database.couponDao()

    @Singleton
    @Provides
    fun provideCouponLocationDao(database: AppDatabase): CouponLocationDao = database.couponLocationDao()

    @Singleton
    @Provides
    fun provideMembershipDao(database: AppDatabase): MembershipDao = database.membershipDao()

    @Singleton
    @Provides
    fun provideMembershipLocationDao(database: AppDatabase): MembershipLocationDao = database.membershipLocationDao()

    @Singleton
    @Provides
    fun provideUserDao(database: AppDatabase): UserDao = database.userDao()

    @Singleton
    @Provides
    fun providePendingChangeDao(database: AppDatabase): PendingChangeDao = database.pendingChangeDao()

    @Singleton
    @Provides
    fun provideSyncMetadataDao(database: AppDatabase): SyncMetadataDao = database.syncMetadataDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    companion object {
        // TODO: Replace with your Supabase credentials
        // Get these from your Supabase project settings:
        // 1. Go to https://app.supabase.com
        // 2. Select your project
        // 3. Go to Settings → API
        // 4. Copy the Project URL and anon key
        private const val SUPABASE_URL = "https://your-supabase-project.supabase.co"
        private const val SUPABASE_API_KEY = "your-supabase-anon-key"
        private const val SUPABASE_REST_URL = "$SUPABASE_URL/rest/v1/"
    }

    @Singleton
    @Provides
    fun provideGson(): Gson = Gson()

    @Singleton
    @Provides
    fun provideHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Authorization interceptor for Supabase
        val authInterceptor = okhttp3.Interceptor { chain ->
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()
                .header("apikey", SUPABASE_API_KEY)
                .header("Authorization", "Bearer $SUPABASE_API_KEY")
                .header("Content-Type", "application/json")

            val request = requestBuilder.build()
            chain.proceed(request)
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Singleton
    @Provides
    fun provideRetrofit(httpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(SUPABASE_REST_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(httpClient)
            .build()
    }

    @Singleton
    @Provides
    fun provideSupabaseApiService(retrofit: Retrofit): SupabaseApiService {
        return retrofit.create(SupabaseApiService::class.java)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Singleton
    @Provides
    fun provideCouponRepository(
        couponDao: CouponDao,
        couponLocationDao: CouponLocationDao,
        pendingChangeDao: PendingChangeDao,
        apiService: SupabaseApiService,
        gson: Gson
    ): CouponRepository {
        return CouponRepositoryImpl(
            couponDao = couponDao,
            couponLocationDao = couponLocationDao,
            pendingChangeDao = pendingChangeDao,
            apiService = apiService,
            gson = gson
        )
    }

    @Singleton
    @Provides
    fun provideSyncRepository(
        couponDao: CouponDao,
        couponLocationDao: CouponLocationDao,
        pendingChangeDao: PendingChangeDao,
        apiService: SupabaseApiService,
        gson: Gson
    ): com.promocodeapp.domain.repository.SyncRepository {
        return SyncRepositoryImpl(
            couponDao = couponDao,
            couponLocationDao = couponLocationDao,
            pendingChangeDao = pendingChangeDao,
            apiService = apiService,
            gson = gson
        )
    }

    @Singleton
    @Provides
    fun provideAuthRepository(
        userDao: UserDao,
        apiService: SupabaseApiService
    ): AuthRepository {
        return AuthRepositoryImpl(
            userDao = userDao,
            apiService = apiService
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Singleton
    @Provides
    fun provideNotificationManager(
        @ApplicationContext context: Context
    ): NotificationManager {
        return ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: throw RuntimeException("NotificationManager not available")
    }
}
