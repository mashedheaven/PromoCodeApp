# Architecture Documentation

## Overview

PromoCodeApp uses a layered, modular architecture that ensures separation of concerns, testability, and scalability. The architecture follows Android best practices and modern Kotlin conventions.

## Architecture Layers

### 1. Presentation Layer (UI & State Management)

**Location**: `presentation/`

Responsible for displaying user interface and managing UI state.

Components:
- **Screens**: Jetpack Compose composable functions (`CouponListScreen`, etc.)
- **ViewModels**: Manage UI state and user interactions (`CouponViewModel`)
- **Navigation**: Route management between screens

Key Classes:
```kotlin
// Screens are pure composables that take ViewModel as dependency
@Composable
fun CouponListScreen(
    userId: String,
    viewModel: CouponViewModel = hiltViewModel()
)

// ViewModels expose UI state via Flow
data class CouponUiState(
    val coupons: List<Coupon> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

**Characteristics**:
- No business logic
- Pure composables for testing
- State flows for reactive updates
- Hilt-injected ViewModels

### 2. Domain Layer (Business Logic)

**Location**: `domain/`

Contains business logic and entities independent of framework.

Components:
- **Models**: Core domain entities (`Coupon`, `Membership`, `User`, `Location`)
- **Use Cases**: Business logic operations (optional, can be in repository)
- **Repository Interfaces**: Abstract data operations

Key Classes:
```kotlin
// Domain models are framework-agnostic
data class Coupon(
    val id: Long,
    val userId: String,
    val code: String,
    val merchantName: String,
    val discountType: DiscountType,
    // ... other fields
)

// Repository interfaces define contracts
interface CouponRepository {
    suspend fun createCoupon(coupon: Coupon): Result<Coupon>
    suspend fun updateCoupon(coupon: Coupon): Result<Coupon>
    suspend fun deleteCoupon(couponId: Long): Result<Unit>
}
```

**Characteristics**:
- Pure Kotlin/business logic
- No Android dependencies
- Easily testable
- Defines contracts for data operations

### 3. Data Layer (Data Management)

**Location**: `data/`

Handles data retrieval and persistence.

Components:
- **Database**: Room entities, DAOs, and database configuration
- **API**: Retrofit service interfaces and API models
- **Repository Implementations**: Concrete implementations of domain interfaces
- **Data Models**: Database and API entities

Key Classes:
```kotlin
// Database layer
@Entity(tableName = "coupons")
data class CouponEntity(...)

@Dao
interface CouponDao {
    @Insert suspend fun insertCoupon(coupon: CouponEntity): Long
    @Query("SELECT * FROM coupons") fun getAllCoupons(): Flow<List<CouponEntity>>
}

// API layer
interface SupabaseApiService {
    @GET("coupons") suspend fun getUserCoupons(@Query("user_id") userId: String): List<SupabaseCoupon>
}

// Repository implementation
class CouponRepositoryImpl(
    private val couponDao: CouponDao,
    private val apiService: SupabaseApiService
) : CouponRepository {
    // Implements the domain interface
}
```

**Characteristics**:
- Abstraction over data sources
- Handles local (Room) and remote (Supabase) data
- Manages caching and synchronization
- Handles offline-first patterns

### 4. Service Layer (Background Operations)

**Location**: `service/`

Manages background services and long-running operations.

Components:
- **FCM Service**: Firebase Cloud Messaging
- **Geofence Receiver**: Handles geofence transitions
- **Sync Service**: Manages data synchronization (future)

Key Classes:
```kotlin
@AndroidEntryPoint
class PromoCodeMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Handle incoming notifications
    }
}

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Handle geofence transitions
    }
}
```

## Data Flow

### 1. User Creates a Coupon

```
User Input (Compose UI)
        ↓
CouponViewModel.createCoupon()
        ↓
CouponRepository.createCoupon()
        ↓
CouponDao.insertCoupon() → Room Database
        ↓
SupabaseApiService.createCoupon() → Supabase (async)
        ↓
UI State Updated via Flow
        ↓
CouponListScreen Re-composes with new coupon
```

### 2. User Views Coupons

```
CouponListScreen initiates
        ↓
CouponViewModel.loadCoupons()
        ↓
CouponRepository.getUserCoupons()
        ↓
CouponDao.getUserCoupons() → Room Database
        ↓
Returns Flow<List<CouponEntity>>
        ↓
Map to domain models
        ↓
Update CouponUiState
        ↓
Compose re-composition
        ↓
Display coupons in LazyColumn
```

### 3. Geofence Triggers Notification

```
Device enters geofence
        ↓
GeofenceBroadcastReceiver.onReceive()
        ↓
Identify coupon from geofence ID
        ↓
Create and send notification via NotificationManager
        ↓
User taps notification
        ↓
Deep link to MainActivity with coupon_id
        ↓
Navigate to CouponDetail screen
```

## Dependency Injection with Hilt

All dependencies are injected using Hilt for testability and modularity.

### Setup
```kotlin
// 1. Add @HiltAndroidApp to Application
@HiltAndroidApp
class PromoCodeApplication : Application() {
    // ...
}

// 2. Create DI modules
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        // Create and provide database
    }
}

// 3. Inject in Activities/ViewModels
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Hilt automatically provides dependencies
}

@HiltViewModel
class CouponViewModel @Inject constructor(
    private val couponRepository: CouponRepository
) : ViewModel() {
    // Repository is injected
}
```

## Error Handling Strategy

### Repository Level
```kotlin
suspend fun createCoupon(coupon: Coupon): Result<Coupon> = try {
    val entity = couponToDatabaseEntity(coupon)
    val id = couponDao.insertCoupon(entity)
    Result.success(coupon.copy(id = id))
} catch (e: Exception) {
    Result.failure(e)
}
```

### ViewModel Level
```kotlin
fun createCoupon(...) {
    viewModelScope.launch {
        couponRepository.createCoupon(coupon)
            .onSuccess { createdCoupon ->
                loadCoupons() // Refresh
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "Failed to create coupon"
                )
            }
    }
}
```

### UI Level
```kotlin
if (uiState.error != null) {
    ErrorBanner(
        message = uiState.error!!,
        onDismiss = { viewModel.clearError() }
    )
}
```

## Offline-First Architecture

### Local-First Approach
1. All operations go to local Room database first
2. Changes are marked as "pending" in PendingChangeEntity
3. When connectivity returns, sync service processes pending changes
4. Conflict resolution uses timestamp (last-write-wins)

### Implementation
```kotlin
// User action is immediately saved locally
couponDao.insertCoupon(entity)

// Marked as pending
pendingChangeDao.insertPendingChange(
    PendingChangeEntity(
        changeType = "CREATE",
        entityType = "COUPON",
        // ...
    )
)

// Later, sync service processes pending changes
syncRepository.fullSync(userId)
```

## Composition Root

The Hilt dependency graph is constructed in `AppModule.kt`:

```kotlin
// Database module provides all DAO instances
@Provides
fun provideCouponDao(database: AppDatabase): CouponDao = database.couponDao()

// Network module provides Retrofit and API services
@Provides
fun provideSupabaseApiService(retrofit: Retrofit): SupabaseApiService {
    return retrofit.create(SupabaseApiService::class.java)
}

// Repository module wires DAOs and API services
@Provides
fun provideCouponRepository(
    couponDao: CouponDao,
    couponLocationDao: CouponLocationDao,
    apiService: SupabaseApiService
): CouponRepository {
    return CouponRepositoryImpl(...)
}
```

## Testing Strategy

### Unit Tests (data and domain layers)
```kotlin
@RunWith(JUnit4::class)
class CouponRepositoryTest {
    @Test
    fun createCoupon_savesToDatabase() = runTest {
        // Test repository logic without Android framework
    }
}
```

### Integration Tests (ViewModel + Repository)
```kotlin
@RunWith(AndroidJUnit4::class)
class CouponViewModelTest {
    @Test
    fun loadCoupons_updateUiState() = runTest {
        // Test ViewModel with actual repository
    }
}
```

### UI Tests (Compose screens)
```kotlin
@RunWith(AndroidJUnit4::class)
class CouponListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun couponCard_displaysCorrectly() {
        // Test Compose UI rendering
    }
}
```

## Best Practices Implemented

1. **SOLID Principles**
   - Single Responsibility: Each class has one reason to change
   - Open/Closed: Repository interfaces are open for extension
   - Liskov Substitution: Repository implementations are interchangeable
   - Interface Segregation: Small, focused interfaces
   - Dependency Inversion: Depend on abstractions, not implementations

2. **Reactive Programming**
   - Use Flow for data streams
   - StateFlow for UI state management
   - Coroutines for async operations

3. **Null Safety**
   - Leverage Kotlin's type system
   - Use nullable types explicitly
   - Avoid null checks with proper design

4. **Resource Management**
   - Lifecycle-aware components
   - Proper coroutine scope management
   - Database connection pooling

5. **Security**
   - API calls over HTTPS only
   - Sensitive data not logged
   - Input validation before database storage

## Future Improvements

1. **CQRS Pattern**: Separate read and write operations
2. **Event Sourcing**: Store all changes as events
3. **Rx Extensions**: Add RxJava for advanced reactive patterns
4. **GraphQL**: Replace REST with GraphQL for more flexible queries
5. **Paging**: Implement Android Paging 3 for large datasets

---

**Architecture Version**: 1.0.0
**Last Updated**: November 15, 2025
