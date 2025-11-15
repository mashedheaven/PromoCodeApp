# Backend Integration Guide

## Overview

This guide covers the complete backend integration for PromoCodeApp using Supabase as the remote database and Firebase for push notifications and authentication.

---

## Architecture Overview

The app follows an **offline-first architecture** with the following data flow:

```
Local Database (Room)
        ↓
  Pending Changes Queue
        ↓
    Sync Service
        ↓
  Supabase Backend
```

### Key Features

1. **Offline-First**: All data stored locally with pending changes tracked
2. **Automatic Sync**: Background sync service syncs changes periodically
3. **Conflict Resolution**: Remote data takes precedence on conflict
4. **Error Retry**: Failed syncs are retried with exponential backoff

---

## Step 1: Supabase Setup

### 1.1 Create Supabase Project

1. Go to [https://app.supabase.com](https://app.supabase.com)
2. Sign up or log in
3. Create a new project
4. Wait for database to be initialized (5-10 minutes)

### 1.2 Get API Credentials

1. Go to **Settings → API**
2. Copy the following:
   - **Project URL**: `https://your-project.supabase.co`
   - **Anon Key**: Your public API key

### 1.3 Create Database Tables

Run the SQL migrations provided in `SUPABASE_SETUP.md`:

```sql
-- Create users table
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  first_name TEXT,
  last_name TEXT,
  profile_image_url TEXT,
  fcm_token TEXT,
  default_geofence_radius INTEGER DEFAULT 150,
  notifications_enabled BOOLEAN DEFAULT true,
  created_date BIGINT NOT NULL,
  last_modified BIGINT NOT NULL
);

-- Create coupons table
CREATE TABLE IF NOT EXISTS coupons (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  code TEXT NOT NULL,
  merchant_name TEXT NOT NULL,
  discount_type TEXT NOT NULL,
  discount_value DOUBLE PRECISION NOT NULL,
  discount_value_currency TEXT DEFAULT 'USD',
  min_purchase_amount DOUBLE PRECISION,
  description TEXT,
  expiration_date BIGINT NOT NULL,
  created_date BIGINT NOT NULL,
  category TEXT,
  is_favorite BOOLEAN DEFAULT false,
  is_used BOOLEAN DEFAULT false,
  is_archived BOOLEAN DEFAULT false,
  image_url TEXT,
  barcode_data TEXT,
  notes TEXT,
  last_modified BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Create coupon_locations table
CREATE TABLE IF NOT EXISTS coupon_locations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  coupon_id UUID NOT NULL REFERENCES coupons(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  latitude DOUBLE PRECISION NOT NULL,
  longitude DOUBLE PRECISION NOT NULL,
  radius INTEGER DEFAULT 150,
  geofence_id TEXT,
  location_name TEXT,
  created_date BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Create indices for performance
CREATE INDEX idx_coupons_user_id ON coupons(user_id);
CREATE INDEX idx_coupons_expiration ON coupons(expiration_date);
CREATE INDEX idx_coupon_locations_coupon ON coupon_locations(coupon_id);
```

### 1.4 Enable Row Level Security (RLS)

For production, enable RLS on all tables:

```sql
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupons ENABLE ROW LEVEL SECURITY;
ALTER TABLE coupon_locations ENABLE ROW LEVEL SECURITY;

-- Create policies
CREATE POLICY "Users can only see their own data"
  ON users FOR SELECT
  USING (auth.uid() = id);

CREATE POLICY "Users can only see their own coupons"
  ON coupons FOR SELECT
  USING (auth.uid() = user_id);
```

---

## Step 2: Update App Configuration

### 2.1 Add Supabase Credentials

Edit `app/src/main/java/com/promocodeapp/di/AppModule.kt`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    companion object {
        // Replace with your Supabase credentials
        private const val SUPABASE_URL = "https://your-project.supabase.co"
        private const val SUPABASE_API_KEY = "your-supabase-anon-key"
        private const val SUPABASE_REST_URL = "$SUPABASE_URL/rest/v1/"
    }
    // ... rest of configuration
}
```

### 2.2 Build and Run

```bash
cd PromoCodeApp
./gradlew clean build
./gradlew installDebug
```

---

## Step 3: Firebase Setup

### 3.1 Create Firebase Project

1. Go to [https://console.firebase.google.com](https://console.firebase.google.com)
2. Create a new project
3. Add Android app to the project
4. Download `google-services.json`
5. Place it in `app/` directory

### 3.2 Configure FCM

1. Go to **Cloud Messaging** tab
2. Copy **Server Key** and **Sender ID**
3. Update `PromoCodeApplication.kt` with your Sender ID

### 3.3 Test FCM

Send a test message via Firebase Console to verify setup.

---

## Step 4: Sync Configuration

### 4.1 Enable Periodic Sync

In your MainActivity or Application class:

```kotlin
import com.promocodeapp.service.SyncScheduler

// Schedule sync to run every 30 minutes
SyncScheduler.schedulePeriodicSync(
    context = applicationContext,
    userId = currentUserId,
    intervalMinutes = 30
)
```

### 4.2 Manual Sync

To trigger immediate sync:

```kotlin
SyncScheduler.triggerImmediateSync(
    context = applicationContext,
    userId = currentUserId
)
```

---

## Step 5: Authentication Flow

### 5.1 User Registration

```kotlin
// In your ViewModel or Activity
val authRepository: AuthRepository by inject()

val result = authRepository.signUp(
    email = "user@example.com",
    password = "secure_password_123"
)

result.onSuccess { user ->
    Log.d("Auth", "Registration successful: ${user.email}")
    // Start sync for new user
    SyncScheduler.schedulePeriodicSync(context, user.id)
}

result.onFailure { error ->
    Log.e("Auth", "Registration failed: ${error.message}")
}
```

### 5.2 User Login

```kotlin
val result = authRepository.signIn(
    email = "user@example.com",
    password = "secure_password_123"
)

result.onSuccess { user ->
    Log.d("Auth", "Login successful")
    // Sync user data
    SyncScheduler.triggerImmediateSync(context, user.id)
}
```

### 5.3 Logout

```kotlin
val result = authRepository.signOut()

result.onSuccess {
    Log.d("Auth", "Logout successful")
    SyncScheduler.cancelAllSyncs(context)
}
```

---

## Step 6: Data Synchronization

### 6.1 Manual Coupon Sync

```kotlin
val syncRepository: SyncRepository by inject()

// Sync all coupons with remote
val result = syncRepository.syncCoupons(userId)

result.onSuccess {
    Log.d("Sync", "Coupons synced successfully")
}

result.onFailure { error ->
    Log.e("Sync", "Sync failed: ${error.message}")
}
```

### 6.2 Create Coupon (with Auto Sync)

```kotlin
val couponRepository: CouponRepository by inject()

val coupon = Coupon(
    id = 0,
    userId = currentUserId,
    code = "SAVE20",
    merchantName = "Store Name",
    discountType = DiscountType.Percentage(20.0),
    discountValue = 20.0,
    expirationDate = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000),
    createdDate = System.currentTimeMillis(),
    locations = listOf(
        Location(
            id = 0,
            couponId = 0,
            latitude = 37.7749,
            longitude = -122.4194,
            radius = 150,
            locationName = "Store Location"
        )
    )
)

// Create locally (automatically queued for sync)
val result = couponRepository.createCoupon(coupon)

result.onSuccess { createdCoupon ->
    Log.d("Coupon", "Created: ${createdCoupon.code}")
    // Trigger immediate sync
    SyncScheduler.triggerImmediateSync(context, currentUserId)
}
```

### 6.3 Monitor Pending Changes

```kotlin
val syncRepository: SyncRepository by inject()

syncRepository.getPendingChanges(userId).collect { count ->
    Log.d("Sync", "Pending changes: $count")
    // Update UI to show sync status
}
```

---

## Step 7: Error Handling & Retry Logic

### 7.1 Network Errors

The app automatically handles network errors:
- **Automatic Retry**: Failed syncs retry with exponential backoff
- **Queue Preservation**: Pending changes stay in queue until successful
- **User Notification**: Optional toast/snackbar when sync fails

### 7.2 Conflict Resolution

When conflicts occur during sync:
1. Remote version takes precedence
2. Local changes are preserved in pending queue
3. User is notified of conflict resolution

### 7.3 Custom Error Handling

```kotlin
val result = syncRepository.fullSync(userId)

result.onFailure { error ->
    when (error) {
        is IOException -> showNetworkError()
        is TimeoutException -> showTimeoutError()
        else -> showGenericError(error.message)
    }
}
```

---

## Step 8: Testing

### 8.1 Test Coupon Sync

1. Create a coupon in the app
2. Go offline (disable internet)
3. Verify coupon appears in local list
4. Go online (enable internet)
5. Verify coupon syncs to Supabase

### 8.2 Test Offline Functionality

```kotlin
// Simulate offline mode
SyncScheduler.triggerImmediateSync(context, userId)
// Disable internet
// Create multiple coupons
// Enable internet
// Verify all coupons sync
```

### 8.3 Test Push Notifications

1. Send test notification via Firebase Console
2. Verify notification appears when app:
   - Is in foreground
   - Is in background
   - Is completely closed

---

## Step 9: Monitoring & Debugging

### 9.1 Enable Detailed Logging

Set log level in `AppModule.kt`:

```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY  // Logs all request/response details
}
```

### 9.2 View Network Requests

Use Android Studio's Network Profiler:
1. Open Android Profiler (View → Tool Windows → Profiler)
2. Click **Network** tab
3. Make requests and observe HTTP calls

### 9.3 Database Inspection

Use Room Inspector:
1. Run app with debugger
1. Go to View → Tool Windows → Database Inspector
2. Inspect local database tables and data

### 9.4 Firebase Console

Monitor:
1. **Cloud Messaging**: FCM token registration
2. **Firestore**: Real-time database updates
3. **Analytics**: User behavior tracking

---

## Step 10: Production Deployment

### 10.1 Pre-Deployment Checklist

- [ ] Supabase credentials updated
- [ ] Firebase `google-services.json` added
- [ ] RLS policies enabled
- [ ] Sync interval optimized for battery
- [ ] Error handling comprehensive
- [ ] Logging disabled (or set to INFO level)
- [ ] ProGuard rules configured

### 10.2 Build Release APK

```bash
./gradlew bundleRelease  # For Google Play Store
./gradlew assembleRelease  # For direct distribution
```

### 10.3 Monitor After Deployment

1. Check Firebase Analytics for crashes
2. Monitor Supabase database performance
3. Track FCM delivery rates
4. Monitor user feedback

---

## Troubleshooting

### Issue: "Connection refused" errors

**Cause**: Invalid Supabase URL or API key
**Solution**: 
1. Verify credentials in AppModule.kt
2. Check Supabase project is running
3. Verify internet connectivity

### Issue: "401 Unauthorized" errors

**Cause**: Invalid or expired API key
**Solution**:
1. Regenerate API key in Supabase
2. Update AppModule.kt
3. Rebuild and reinstall app

### Issue: FCM tokens not updating

**Cause**: Firebase not properly initialized
**Solution**:
1. Verify `google-services.json` in app/
2. Check Firebase service in `PromoCodeMessagingService.kt`
3. Verify Firebase Cloud Messaging enabled in console

### Issue: Sync stuck indefinitely

**Cause**: Corrupted pending changes
**Solution**:
```kotlin
// Clear pending changes (use with caution)
pendingChangeDao.deleteSyncedChanges(userId)
// Retry sync
SyncScheduler.triggerImmediateSync(context, userId)
```

### Issue: High battery drain

**Cause**: Sync running too frequently
**Solution**:
1. Increase sync interval from 30 to 60+ minutes
2. Use `triggerImmediateSync` only when necessary
3. Disable background sync when battery < 20%

---

## API Reference

### Coupon Repository

```kotlin
interface CouponRepository {
    suspend fun createCoupon(coupon: Coupon): Result<Coupon>
    suspend fun updateCoupon(coupon: Coupon): Result<Coupon>
    suspend fun deleteCoupon(couponId: Long): Result<Unit>
    
    fun getUserCoupons(userId: String): Flow<List<Coupon>>
    fun getFavoriteCoupons(userId: String): Flow<List<Coupon>>
    fun searchCoupons(userId: String, query: String): Flow<List<Coupon>>
    fun getExpiringCoupons(userId: String, days: Int): Flow<List<Coupon>>
    
    suspend fun archiveCoupon(couponId: Long): Result<Unit>
    suspend fun toggleFavorite(couponId: Long, isFavorite: Boolean): Result<Unit>
}
```

### Sync Repository

```kotlin
interface SyncRepository {
    suspend fun syncCoupons(userId: String): Result<Unit>
    suspend fun syncMemberships(userId: String): Result<Unit>
    suspend fun fullSync(userId: String): Result<Unit>
    
    fun getPendingChanges(userId: String): Flow<Int>
    suspend fun markChangeAsSynced(changeId: Long): Result<Unit>
}
```

### Auth Repository

```kotlin
interface AuthRepository {
    suspend fun signUp(email: String, password: String): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUser(): Result<User?>
    suspend fun updatePassword(newPassword: String): Result<Unit>
}
```

---

## Security Best Practices

1. **API Key Management**
   - Never commit API keys to version control
   - Use environment variables or secure storage
   - Rotate keys regularly

2. **User Data**
   - Enable RLS on all tables
   - Implement proper authorization checks
   - Use HTTPS for all communications

3. **Password Security**
   - Use Supabase Auth for password hashing
   - Implement password reset flow
   - Enforce strong password requirements

4. **FCM Tokens**
   - Refresh tokens periodically
   - Update server when token changes
   - Remove old tokens

---

## Support & Resources

- [Supabase Documentation](https://supabase.com/docs)
- [Firebase Documentation](https://firebase.google.com/docs)
- [Retrofit Documentation](https://square.github.io/retrofit/)
- [Room Documentation](https://developer.android.com/training/data-storage/room)

---

**Last Updated**: November 15, 2025
**Version**: 1.0.0
