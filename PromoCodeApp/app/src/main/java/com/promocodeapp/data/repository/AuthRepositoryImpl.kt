package com.promocodeapp.data.repository

import android.util.Log
import com.promocodeapp.data.api.SupabaseApiService
import com.promocodeapp.data.api.SupabaseUser
import com.promocodeapp.data.db.dao.UserDao
import com.promocodeapp.data.db.entity.UserEntity
import com.promocodeapp.domain.model.User
import com.promocodeapp.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val apiService: SupabaseApiService
) : AuthRepository {

    companion object {
        private const val TAG = "AuthRepository"
    }

    override suspend fun signUp(email: String, password: String): Result<User> = try {
        Log.d(TAG, "Attempting sign up for $email")
        
        // TODO: Integrate with Supabase Auth
        // For now, this is a placeholder that would connect to Supabase Auth API
        // Supabase Auth: POST /auth/v1/signup
        
        val user = User(
            id = "",
            email = email,
            firstName = null,
            lastName = null,
            profileImageUrl = null,
            fcmToken = null,
            defaultGeofenceRadius = 150,
            notificationsEnabled = true,
            createdDate = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        
        // Save locally
        saveUserLocally(user)
        
        Log.d(TAG, "Sign up successful for $email")
        Result.success(user)
    } catch (e: Exception) {
        Log.e(TAG, "Sign up failed: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun signIn(email: String, password: String): Result<User> = try {
        Log.d(TAG, "Attempting sign in for $email")
        
        // TODO: Integrate with Supabase Auth
        // Supabase Auth: POST /auth/v1/token?grant_type=password
        
        val user = User(
            id = "",
            email = email,
            firstName = null,
            lastName = null,
            profileImageUrl = null,
            fcmToken = null,
            defaultGeofenceRadius = 150,
            notificationsEnabled = true,
            createdDate = System.currentTimeMillis(),
            lastModified = System.currentTimeMillis()
        )
        
        // Fetch from remote
        val remoteUser = apiService.getUser(user.id)
        val domainUser = remoteUserToDomainUser(remoteUser)
        
        // Save locally
        saveUserLocally(domainUser)
        
        Log.d(TAG, "Sign in successful for $email")
        Result.success(domainUser)
    } catch (e: Exception) {
        Log.e(TAG, "Sign in failed: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun signOut(): Result<Unit> = try {
        Log.d(TAG, "Attempting sign out")
        
        // TODO: Call Supabase Auth sign out endpoint
        // Clear local data
        clearLocalData()
        
        Log.d(TAG, "Sign out successful")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Sign out failed: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun getCurrentUser(): Result<User?> = try {
        Log.d(TAG, "Fetching current user")
        
        // TODO: Get from Supabase session
        // For now, return null (user not authenticated)
        Result.success(null)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to get current user: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> = try {
        Log.d(TAG, "Attempting password update")
        
        // TODO: Call Supabase Auth password update endpoint
        // PUT /auth/v1/user/password
        
        Log.d(TAG, "Password updated successfully")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Password update failed: ${e.message}", e)
        Result.failure(e)
    }

    override suspend fun resetPassword(email: String): Result<Unit> = try {
        Log.d(TAG, "Attempting password reset for $email")
        
        // TODO: Call Supabase Auth password reset endpoint
        // POST /auth/v1/recovery
        
        Log.d(TAG, "Password reset email sent")
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Password reset failed: ${e.message}", e)
        Result.failure(e)
    }

    private suspend fun saveUserLocally(user: User) {
        try {
            val entity = UserEntity(
                id = user.id,
                email = user.email,
                firstName = user.firstName,
                lastName = user.lastName,
                profileImageUrl = user.profileImageUrl,
                fcmToken = user.fcmToken,
                defaultGeofenceRadius = user.defaultGeofenceRadius,
                notificationsEnabled = user.notificationsEnabled,
                createdDate = user.createdDate,
                lastModified = user.lastModified
            )
            userDao.insertOrUpdate(entity)
            Log.d(TAG, "User saved locally: ${user.email}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user locally: ${e.message}", e)
            throw e
        }
    }

    private suspend fun clearLocalData() {
        try {
            // Clear all user data
            userDao.deleteAllUsers()
            Log.d(TAG, "Local data cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear local data: ${e.message}", e)
        }
    }

    private fun remoteUserToDomainUser(remoteUser: SupabaseUser): User = User(
        id = remoteUser.id,
        email = remoteUser.email,
        firstName = remoteUser.firstName,
        lastName = remoteUser.lastName,
        profileImageUrl = remoteUser.profileImageUrl,
        fcmToken = remoteUser.fcmToken,
        defaultGeofenceRadius = remoteUser.defaultGeofenceRadius,
        notificationsEnabled = remoteUser.notificationsEnabled,
        createdDate = remoteUser.createdDate,
        lastModified = remoteUser.lastModified
    )
}
