# Backend Integration Quick Reference

## 🚀 5-Minute Setup

### 1. Get Supabase Credentials
```
SUPABASE_URL = https://your-project.supabase.co
SUPABASE_API_KEY = eyJ0eXAiOiJKV1QiLCJhbGc...
```

### 2. Update AppModule.kt
```kotlin
// app/src/main/java/com/promocodeapp/di/AppModule.kt
private const val SUPABASE_URL = "https://your-project.supabase.co"
private const val SUPABASE_API_KEY = "your-supabase-anon-key"
```

### 3. Build & Test
```bash
./gradlew clean build
./gradlew installDebug
```

---

## 📋 Common Tasks

### Create a Coupon (with Auto-Sync)
```kotlin
val coupon = Coupon(
    userId = userId,
    code = "SAVE20",
    merchantName = "Store Name",
    discountType = DiscountType.Percentage(20.0),
    discountValue = 20.0,
    expirationDate = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000),
    createdDate = System.currentTimeMillis()
)

couponRepository.createCoupon(coupon)
SyncScheduler.triggerImmediateSync(context, userId)
```

### Sync with Backend
```kotlin
SyncScheduler.schedulePeriodicSync(context, userId, 30)  // Every 30 minutes
SyncScheduler.triggerImmediateSync(context, userId)      // Now
SyncScheduler.cancelAllSyncs(context)                     // Cancel all
```

### User Authentication
```kotlin
// Sign up
authRepository.signUp("user@example.com", "password123")

// Sign in
authRepository.signIn("user@example.com", "password123")

// Sign out
authRepository.signOut()
```

### Monitor Sync Status
```kotlin
syncRepository.getPendingChanges(userId).collect { count ->
    println("Pending: $count")
}
```

---

## 🔍 Key APIs

| Component | Purpose | Location |
|-----------|---------|----------|
| `CouponRepository` | CRUD operations for coupons | `domain/repository/` |
| `SyncRepository` | Sync with backend | `domain/repository/` |
| `AuthRepository` | User authentication | `domain/repository/` |
| `SyncScheduler` | Schedule background sync | `service/DataSyncService.kt` |
| `SupabaseApiService` | REST API calls | `data/api/` |

---

## 🛠️ Debugging

### Check Sync Status
```kotlin
syncRepository.getPendingChanges(userId).collect { count ->
    Log.d("Sync", "Pending changes: $count")
}
```

### Enable Detailed Logging
```kotlin
// In AppModule.kt
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = HttpLoggingInterceptor.Level.BODY
}
```

### View Database
1. Android Studio → View → Tool Windows → Database Inspector
2. Inspect `pending_changes` table
3. Check `coupons` table

---

## ⚡ Performance Tips

1. **Sync Frequency**: 30 minutes for most apps
2. **Batch Uploads**: Queue multiple changes before sync
3. **Compression**: Enable gzip in OkHttp
4. **Retry Policy**: Exponential backoff (max 15 min)

---

## ❌ Troubleshooting

| Error | Cause | Fix |
|-------|-------|-----|
| `401 Unauthorized` | Invalid API key | Check SUPABASE_API_KEY in AppModule |
| `Connection refused` | Invalid URL | Verify SUPABASE_URL format |
| `Timeout` | Slow network | Increase timeout or retry |
| `Sync stuck` | Corrupted data | Clear pending changes |

---

## 📦 Included Features

✅ Offline-first architecture  
✅ Automatic retry on failure  
✅ Background sync service  
✅ User authentication  
✅ Pending changes queue  
✅ Conflict resolution  
✅ Error handling  
✅ Comprehensive logging  

---

## 📚 Full Documentation

See `BACKEND_INTEGRATION.md` for complete guide

---

**Last Updated**: November 15, 2025
