# Backend Integration Testing Guide

## 📋 Pre-Testing Checklist

- [ ] Supabase project created
- [ ] Database tables created (SQL migrations run)
- [ ] Firebase project created
- [ ] `google-services.json` added to `app/`
- [ ] Supabase credentials in `AppModule.kt`
- [ ] App builds successfully
- [ ] Test device/emulator ready
- [ ] Internet connectivity available

---

## Test 1: Basic Connectivity

### Objective
Verify the app can connect to Supabase backend

### Steps
1. Build and run the app
2. Open Logcat (Android Studio)
3. Filter by tag: `CouponRepository` or `SyncRepository`
4. Observe network requests in debug output

### Expected Result
```
D/CouponRepository: Syncing coupon SAVE20 to remote
D/NetworkModule: HTTP 200 Response received from Supabase
```

### Failure Debugging
If requests fail:
- Check SUPABASE_URL format
- Verify SUPABASE_API_KEY is correct
- Check internet connectivity
- Verify Supabase project is running

---

## Test 2: Create & Sync Coupon

### Objective
Test complete flow: create local coupon → sync to backend

### Steps
1. Build and run app
2. Open Supabase dashboard
3. Create a coupon in the app:
   - Code: `TEST123`
   - Merchant: `Test Store`
   - Discount: `15% OFF`
   - Expiration: 30 days
4. Trigger immediate sync:
   ```kotlin
   SyncScheduler.triggerImmediateSync(context, userId)
   ```
5. Check Supabase `coupons` table

### Expected Result
- Coupon appears in local SQLite database immediately
- Coupon appears in Supabase table after sync
- Status shows "Synced ✓"
- No errors in Logcat

### Test Code (in ViewModel)
```kotlin
// Create coupon
val coupon = Coupon(
    userId = userId,
    code = "TEST123",
    merchantName = "Test Store",
    discountType = DiscountType.Percentage(15.0),
    discountValue = 15.0,
    expirationDate = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000),
    createdDate = System.currentTimeMillis()
)

couponRepository.createCoupon(coupon).onSuccess {
    Log.d("Test", "Coupon created locally")
    SyncScheduler.triggerImmediateSync(context, userId)
}
```

---

## Test 3: Offline Functionality

### Objective
Verify app works offline and queues changes

### Steps
1. Create coupon in app (while online)
2. Disable internet (Flight Mode or WiFi off)
3. Create another coupon
4. Verify app doesn't crash
5. Check pending changes count:
   ```kotlin
   syncRepository.getPendingChanges(userId).collect { count ->
       Log.d("Test", "Pending: $count")
   }
   ```
6. Enable internet
7. Verify sync happens automatically

### Expected Result
- App functions normally offline
- New coupon saved to local database
- Pending changes count = 1
- Sync succeeds after going online
- Both coupons appear in Supabase

### Failure Case
- If offline, coupons should NOT be created in Supabase
- If offline, pending count should show changes queued

---

## Test 4: Pending Changes Queue

### Objective
Verify pending changes tracking works correctly

### Steps
1. Go offline
2. Create 3 coupons
3. Monitor pending changes:
   ```kotlin
   syncRepository.getPendingChanges(userId).collect { count ->
       updateUI("$count changes pending")
   }
   ```
4. Expected: Count = 3
5. Go online
6. Wait for sync
7. Expected: Count = 0
8. Check Supabase: all 3 coupons present

### Expected Behavior
```
Pending: 0
Create Coupon 1
Pending: 1
Create Coupon 2
Pending: 2
Create Coupon 3
Pending: 3
[Go Online]
[Sync starts]
Pending: 3 (uploading...)
[After 30 seconds]
Pending: 0 (sync complete)
```

---

## Test 5: Authentication Flow

### Objective
Test user registration and login

### Steps
1. Implement sign-up screen (or use test code):
```kotlin
val email = "test@example.com"
val password = "TestPassword123!"

authRepository.signUp(email, password).onSuccess { user ->
    Log.d("Auth", "Signup successful: ${user.email}")
    // User created in Supabase
}
```

2. Verify in Supabase `users` table
3. Test login:
```kotlin
authRepository.signIn(email, password).onSuccess { user ->
    Log.d("Auth", "Login successful")
    // Schedule sync for this user
    SyncScheduler.schedulePeriodicSync(context, user.id)
}
```

4. Test logout:
```kotlin
authRepository.signOut().onSuccess {
    Log.d("Auth", "Logout successful")
    SyncScheduler.cancelAllSyncs(context)
}
```

### Expected Result
- User record created in Supabase
- Login retrieves user data
- Logout clears local data
- Sync runs for logged-in user

---

## Test 6: Sync Retry Logic

### Objective
Test automatic retry on network failure

### Steps
1. Create coupon (should be queued for sync)
2. Simulate network failure:
   - Use Android Studio's network throttling
   - Or disable WiFi while keeping some connectivity
3. Trigger sync
4. Observe retry behavior
5. After 5 minutes, enable network
6. Verify sync succeeds

### Expected Behavior
```
Sync attempt 1: Failed (retry in 5 min)
Sync attempt 2: Failed (retry in 10 min)
Sync attempt 3: Failed (retry in 15 min)
[Network restored]
Sync attempt 4: Success
```

### Test Code
```kotlin
// In Logcat, filter by "SyncWorker"
// You should see:
// D/SyncWorker: Starting background sync
// E/SyncWorker: Sync worker error: (message)
// D/SyncWorker: Starting background sync (retry)
```

---

## Test 7: Data Synchronization

### Objective
Verify complete data sync (coupons + locations)

### Steps
1. Create coupon with multiple locations:
```kotlin
val coupon = Coupon(
    userId = userId,
    code = "SYNC_TEST",
    merchantName = "Multi Location Store",
    discountType = DiscountType.FixedAmount(10.0, "USD"),
    discountValue = 10.0,
    expirationDate = System.currentTimeMillis() + (60 * 24 * 60 * 60 * 1000),
    createdDate = System.currentTimeMillis(),
    locations = listOf(
        Location(latitude = 37.7749, longitude = -122.4194, radius = 150),
        Location(latitude = 34.0522, longitude = -118.2437, radius = 200),
        Location(latitude = 40.7128, longitude = -74.0060, radius = 150)
    )
)

couponRepository.createCoupon(coupon)
SyncScheduler.triggerImmediateSync(context, userId)
```

2. Check Supabase:
   - `coupons` table: 1 new coupon
   - `coupon_locations` table: 3 new locations

### Expected Result
- Coupon synced to remote
- All 3 locations synced
- Data integrity maintained
- No duplicate records

---

## Test 8: Conflict Resolution

### Objective
Test how conflicts are handled

### Steps
1. Create coupon locally with discount = 15%
2. Simultaneously modify coupon in Supabase (discount = 20%)
3. Trigger sync
4. Verify remote version (20%) takes precedence
5. Check local database (should be 20%)

### Expected Behavior
- Remote version wins conflicts
- User receives notification
- No data loss
- Consistency maintained

---

## Test 9: Error Handling

### Objective
Test various error scenarios

### Test Cases

**Case A: Invalid API Key**
```kotlin
// Temporarily change SUPABASE_API_KEY to invalid value
// Expected: 401 Unauthorized error
// Should: Show user-friendly error message
```

**Case B: Network Timeout**
```kotlin
// Set unreachable IP for SUPABASE_URL
// Expected: TimeoutException after 30 seconds
// Should: Retry automatically
```

**Case C: Empty Response**
```kotlin
// Simulate empty coupon list
// Expected: Handle gracefully
// Should: Show empty state to user
```

**Case D: Invalid Data**
```kotlin
// Return coupon with missing required fields
// Expected: Handle parsing error
// Should: Log error and skip invalid record
```

---

## Test 10: Firebase Cloud Messaging

### Objective
Test push notifications from Supabase events

### Steps
1. Use Firebase Console to send test message
2. App should receive notification
3. When tapped, should navigate to coupon detail
4. Verify notification channels:
   - Proximity Alerts (High priority)
   - Expiration Warnings (Default)
   - Membership Reminders (Default)
   - General (Low priority)

### Expected Result
- Notification appears in notification tray
- Correct channel/priority used
- Deep linking works
- App doesn't crash

### Test Code
```kotlin
// Send test notification from Firebase Console
// Or use:
adb shell am start \
  -a android.intent.action.VIEW \
  -d "https://promocodeapp.example.com/coupon/123"
```

---

## Test 11: Background Sync

### Objective
Verify sync runs in background correctly

### Steps
1. Create coupon with pending changes
2. Press Home button (app goes background)
3. Wait 30+ minutes
4. Open Supabase dashboard
5. Verify coupon was synced

### Expected Result
- Sync runs while app is backgrounded
- Pending changes are uploaded
- No manual user action needed
- WorkManager handles scheduling

### Check WorkManager
```kotlin
// In Android Studio Terminal
adb shell dumpsys jobscheduler | grep "promocodeapp"
```

---

## Test 12: Concurrent Operations

### Objective
Test multiple simultaneous operations

### Steps
1. Create 5 coupons rapidly
2. Trigger sync
3. Modify 2 coupons
4. Trigger sync again
5. Delete 1 coupon
6. Verify all operations sync correctly

### Expected Result
- All operations queued properly
- No data loss
- Correct operation order maintained
- All changes synced to backend

---

## Performance Testing

### Sync Performance
```kotlin
// Measure sync time
val startTime = System.currentTimeMillis()
syncRepository.fullSync(userId)
val endTime = System.currentTimeMillis()
Log.d("Performance", "Sync took ${endTime - startTime}ms")
```

### Expected Results
- < 3 seconds for 10 coupons
- < 10 seconds for 100 coupons
- < 30 seconds for 1000 coupons

### Database Performance
```kotlin
// Measure query time
val startTime = System.currentTimeMillis()
couponRepository.getUserCoupons(userId).collect { coupons ->
    val endTime = System.currentTimeMillis()
    Log.d("Performance", "Query took ${endTime - startTime}ms")
}
```

### Expected Results
- < 100ms for local query
- < 2000ms for remote fetch

---

## Test Automation (Unit Tests)

### Sample Test
```kotlin
@Test
fun testCouponSync() = runTest {
    // Setup
    val coupon = Coupon(...)
    
    // Create locally
    couponRepository.createCoupon(coupon).apply {
        assertIsSuccess()
    }
    
    // Verify pending change
    syncRepository.getPendingChanges(userId).first().apply {
        assertEquals(1, this)
    }
    
    // Mock sync success
    val syncResult = syncRepository.syncCoupons(userId)
    
    // Verify sync result
    syncResult.apply {
        assertIsSuccess()
    }
    
    // Verify pending changes cleared
    syncRepository.getPendingChanges(userId).first().apply {
        assertEquals(0, this)
    }
}
```

---

## Monitoring Checklist

During each test, monitor:

- [ ] **Logcat**: No errors or exceptions
- [ ] **Network Monitor**: Correct HTTP methods and endpoints
- [ ] **Database Inspector**: Data changes reflected locally
- [ ] **Supabase Console**: Remote data updates
- [ ] **Memory Usage**: No memory leaks
- [ ] **Battery**: Reasonable battery consumption
- [ ] **CPU**: Not maxing out

---

## Sign-Off Checklist

After all tests pass:

- [ ] Connectivity test passed
- [ ] Create/sync test passed
- [ ] Offline test passed
- [ ] Pending changes test passed
- [ ] Authentication test passed
- [ ] Retry logic test passed
- [ ] Data sync test passed
- [ ] Conflict resolution test passed
- [ ] Error handling test passed
- [ ] FCM test passed
- [ ] Background sync test passed
- [ ] Concurrent operations test passed
- [ ] Performance acceptable
- [ ] No crashes observed
- [ ] User experience smooth

---

## Troubleshooting Test Failures

### If Tests Fail

1. **Check Credentials**
   ```bash
   # Verify in AppModule.kt
   SUPABASE_URL = correct format?
   SUPABASE_API_KEY = valid?
   ```

2. **Check Database**
   ```bash
   # Verify tables exist in Supabase
   SELECT * FROM coupons;
   SELECT * FROM coupon_locations;
   ```

3. **Check Logs**
   ```bash
   # Filter Logcat for errors
   adb logcat | grep -i error
   ```

4. **Reset Test State**
   ```bash
   # Clear app data and retry
   adb shell pm clear com.promocodeapp
   adb uninstall com.promocodeapp
   ./gradlew installDebug
   ```

---

**Testing Version**: 1.0  
**Last Updated**: November 15, 2025
