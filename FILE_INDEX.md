# PromoCodeApp - Complete File Index & Navigation Guide

## 📚 Documentation Files (Start Here!)

### Root Level Documentation

| File | Purpose | Read Time |
|------|---------|-----------|
| **[README.md](README.md)** | Project overview, features, and quick links | 5 min |
| **[PromoCodeApp/README.md](PromoCodeApp/README.md)** | Comprehensive technical documentation (1500+ lines) | 30 min |
| **[PromoCodeApp/IMPLEMENTATION_SUMMARY.md](PromoCodeApp/IMPLEMENTATION_SUMMARY.md)** | Summary of what's been built | 10 min |
| **[PromoCodeApp/ARCHITECTURE.md](PromoCodeApp/ARCHITECTURE.md)** | Detailed architecture and design patterns | 20 min |
| **[PromoCodeApp/GETTING_STARTED.md](PromoCodeApp/GETTING_STARTED.md)** | Step-by-step setup guide | 15 min |
| **[PromoCodeApp/FIREBASE_SETUP.md](PromoCodeApp/FIREBASE_SETUP.md)** | Firebase configuration instructions | 10 min |
| **[PromoCodeApp/SUPABASE_SETUP.md](PromoCodeApp/SUPABASE_SETUP.md)** | Supabase setup with SQL migrations | 15 min |
| **[PromoCodeApp/CONTRIBUTING.md](PromoCodeApp/CONTRIBUTING.md)** | Development guidelines and contribution process | 10 min |

**Recommended Reading Order:**
1. Start with `README.md` (this file)
2. Then `GETTING_STARTED.md` for setup
3. Then `IMPLEMENTATION_SUMMARY.md` for overview of what's built
4. Then `ARCHITECTURE.md` for understanding the design
5. Reference specific setup guides as needed

---

## 🗂️ Project Structure

```
PromoCodeApp/
├── README.md                          # Start here!
├── PromoCodeApp/
│   ├── README.md                      # Comprehensive documentation
│   ├── IMPLEMENTATION_SUMMARY.md      # What's been built
│   ├── ARCHITECTURE.md                # Architecture details
│   ├── GETTING_STARTED.md             # Setup guide
│   ├── FIREBASE_SETUP.md              # Firebase config
│   ├── SUPABASE_SETUP.md              # Supabase config
│   ├── CONTRIBUTING.md                # Development guidelines
│   ├── build.gradle.kts               # Root Gradle config
│   ├── settings.gradle.kts            # Gradle settings
│   ├── gradle.properties              # Gradle properties
│   ├── gradle/
│   │   └── libs.versions.toml         # Dependency versions
│   └── app/
│       ├── build.gradle.kts           # App dependencies
│       ├── proguard-rules.pro         # Obfuscation rules
│       └── src/main/
│           ├── AndroidManifest.xml    # App manifest
│           └── java/com/promocodeapp/
│               ├── data/              # Data layer
│               ├── domain/            # Domain layer
│               ├── presentation/      # UI layer
│               ├── service/           # Background services
│               ├── util/              # Utilities
│               ├── di/                # Dependency injection
│               ├── ui/theme/          # Material 3 theme
│               ├── MainActivity.kt    # Entry point
│               └── PromoCodeApplication.kt # App class
```

---

## 📂 Source Code Files

### Data Layer (`app/src/main/java/com/promocodeapp/data/`)

#### Database Package (`data/db/`)
| File | Purpose | Lines |
|------|---------|-------|
| `entity/Entities.kt` | 7 database entities (Coupon, Membership, User, Location, etc.) | 180 |
| `dao/Daos.kt` | Data Access Objects with CRUD operations | 250 |
| `AppDatabase.kt` | Room database configuration and singleton | 45 |

**Key Classes:**
- `CouponEntity` - Coupon data model
- `MembershipEntity` - Membership data model
- `UserEntity` - User profile and settings
- `CouponLocationEntity` - Geofence locations
- `PendingChangeEntity` - Offline sync tracking
- `SyncMetadataEntity` - Sync timestamps

**Key DAOs:**
- `CouponDao` - Coupon operations
- `MembershipDao` - Membership operations
- `UserDao` - User operations
- `PendingChangeDao` - Sync queue management

#### API Package (`data/api/`)
| File | Purpose | Lines |
|------|---------|-------|
| `SupabaseApiService.kt` | Retrofit API interface + data models | 150 |

**Key Components:**
- `SupabaseApiService` - Retrofit interface
- `SupabaseCoupon` - API coupon model
- `SupabaseMembership` - API membership model
- `SyncRequest`/`SyncResponse` - Sync DTOs

#### Repository Package (`data/repository/`)
| File | Purpose | Lines |
|------|---------|-------|
| `CouponRepositoryImpl.kt` | Concrete repository implementation | 200 |

**Key Methods:**
- `createCoupon()` - Create new coupon
- `getUserCoupons()` - Get user's coupons (reactive)
- `searchCoupons()` - Search functionality
- `archiveCoupon()` - Archive expired coupons

---

### Domain Layer (`app/src/main/java/com/promocodeapp/domain/`)

#### Model Package (`domain/model/`)
| File | Purpose | Lines |
|------|---------|-------|
| `Models.kt` | Domain entities (framework-independent) | 200 |

**Key Classes:**
- `Coupon` - Domain coupon model
- `Membership` - Domain membership model
- `User` - Domain user model
- `Location` - Location with coordinates
- `DiscountType` - Sealed class for discount types
  - `Percentage(percentage: Double)`
  - `FixedAmount(amount: Double, currency: String)`
  - `BuyOneGetOne`
  - `FreeShipping`

#### Repository Package (`domain/repository/`)
| File | Purpose | Lines |
|------|---------|-------|
| `Repositories.kt` | Repository interfaces (contracts) | 120 |

**Key Interfaces:**
- `CouponRepository` - Coupon operations contract
- `LocationRepository` - Location operations contract
- `MembershipRepository` - Membership operations contract
- `UserRepository` - User operations contract
- `SyncRepository` - Synchronization contract
- `AuthRepository` - Authentication contract

---

### Presentation Layer (`app/src/main/java/com/promocodeapp/presentation/`)

#### UI Package (`presentation/ui/`)
| File | Purpose | Lines |
|------|---------|-------|
| `CouponListScreen.kt` | Main coupon list screen + composables | 400 |

**Key Composables:**
- `CouponListScreen()` - Main screen container
- `CouponTopAppBar()` - Top app bar
- `SearchAndFilterSection()` - Search and filter UI
- `CouponCard()` - Individual coupon card
- `DiscountBadge()` - Discount display
- `ErrorBanner()` - Error message display
- `EmptyStateScreen()` - Empty state UI

**Features:**
- Search by merchant/code/description
- Filter by status (All, Favorites, Expiring, etc.)
- Sort by expiration, merchant, discount
- Favorite toggle
- Visual expiration indicators

#### ViewModel Package (`presentation/viewmodel/`)
| File | Purpose | Lines |
|------|---------|-------|
| `CouponViewModel.kt` | State management and business logic | 350 |

**Key Components:**
- `CouponViewModel` - Main view model
- `CouponUiState` - UI state data class
- `FilterType` enum - Filter options
- `SortBy` enum - Sort options

**Key Methods:**
- `initialize()` - Initialize with user ID
- `createCoupon()` - Create new coupon
- `updateCoupon()` - Update existing coupon
- `deleteCoupon()` - Delete coupon
- `setSearchQuery()` - Update search
- `setFilterType()` - Change filter
- `setSortBy()` - Change sort order

#### Navigation Package (`presentation/navigation/`)
| File | Purpose | Lines |
|------|---------|-------|
| `Navigation.kt` | Navigation routes definition | 15 |

**Key Routes:**
- `CouponList` - Main screen
- `CouponDetail` - Coupon details
- `CreateCoupon` - Create new coupon
- `MembershipList` - Membership list
- `Settings` - App settings

---

### Service Layer (`app/src/main/java/com/promocodeapp/service/`)

| File | Purpose | Lines |
|------|---------|-------|
| `PromoCodeMessagingService.kt` | Firebase Cloud Messaging handler | 150 |
| `GeofenceBroadcastReceiver.kt` | Geofence event receiver | 120 |

**Key Components:**

**PromoCodeMessagingService:**
- `onMessageReceived()` - Handle incoming FCM messages
- `onNewToken()` - Handle new FCM token
- `sendNotification()` - Create and display notifications
- `createNotificationChannels()` - Setup Android 8+ channels
- Notification channels:
  - Proximity Alerts (High)
  - Expiration Warnings (Default)
  - Membership Reminders (Default)
  - General Notifications (Low)

**GeofenceBroadcastReceiver:**
- `onReceive()` - Handle geofence transitions
- `handleEnterGeofence()` - User entered geofence
- `handleExitGeofence()` - User left geofence
- `sendProximityNotification()` - Send notification

---

### Utilities (`app/src/main/java/com/promocodeapp/util/`)

| File | Purpose | Lines |
|------|---------|-------|
| `Utilities.kt` | Helper classes and functions | 250 |

**Key Classes:**
- `GeofenceManager` - Geofence creation and management
- `LocationPoint` - Location with distance calculation
- `DateUtils` - Date/time calculations
- `ValidationUtils` - Input validation

**Key Functions:**
- `addGeofences()` - Add geofences
- `removeGeofences()` - Remove geofences
- `createGeofence()` - Create single geofence
- `daysUntil()` - Calculate days until expiration
- `isExpired()` - Check if expired
- `isExpiringToday()` - Check if expiring today
- `isValidEmail()` - Validate email
- `isValidPassword()` - Validate password

---

### Dependency Injection (`app/src/main/java/com/promocodeapp/di/`)

| File | Purpose | Lines |
|------|---------|-------|
| `AppModule.kt` | Hilt dependency injection modules | 180 |

**Key Modules:**
- `DatabaseModule` - Provide database and DAOs
- `NetworkModule` - Provide Retrofit and API service
- `RepositoryModule` - Provide repository implementations
- `ServiceModule` - Provide Android services

---

### UI Theme (`app/src/main/java/com/promocodeapp/ui/theme/`)

| File | Purpose | Lines |
|------|---------|-------|
| `Theme.kt` | Material 3 color scheme and theming | 100 |
| `Type.kt` | Typography definitions | 80 |

**Features:**
- Light and dark color schemes
- Dynamic color support (Android 12+)
- Material 3 design system
- Custom typography
- Proper status bar styling

---

### Entry Points

| File | Purpose | Lines |
|------|---------|-------|
| `MainActivity.kt` | App entry point Activity | 40 |
| `PromoCodeApplication.kt` | Application class initialization | 20 |

---

## 🔧 Configuration Files

| File | Purpose |
|------|---------|
| `app/build.gradle.kts` | App-level Gradle configuration with all dependencies |
| `build.gradle.kts` | Root Gradle configuration |
| `settings.gradle.kts` | Gradle multi-project settings |
| `gradle.properties` | Gradle build properties |
| `gradle/libs.versions.toml` | Version catalog for dependency management |
| `app/proguard-rules.pro` | ProGuard obfuscation rules |
| `AndroidManifest.xml` | Android app manifest with permissions |

---

## 📋 Key Statistics

| Metric | Count |
|--------|-------|
| **Java/Kotlin Files** | 20+ |
| **Lines of Code** | 3000+ |
| **Documentation Lines** | 2000+ |
| **Database Entities** | 7 |
| **DAOs** | 7 |
| **Composables** | 10+ |
| **ViewModels** | 1 (+ 2 planned) |
| **API Models** | 8 |
| **Notification Channels** | 4 |
| **Navigation Routes** | 7 |
| **Dependencies** | 50+ |

---

## 🗺️ Feature Implementation Status

### Completed ✅
- [x] Data layer (Room database)
- [x] Domain layer (business logic)
- [x] Presentation layer (Jetpack Compose UI)
- [x] Geofencing infrastructure
- [x] Firebase/FCM setup
- [x] Dependency injection
- [x] Material 3 theme
- [x] Documentation
- [x] Utilities and helpers

### In Progress 🔄
- [ ] Backend API integration
- [ ] Authentication screens
- [ ] Data synchronization

### Planned 📋
- [ ] Barcode/QR scanning
- [ ] Membership management screens
- [ ] Settings screen
- [ ] Analytics
- [ ] iOS port

---

## 🚀 Quick Navigation

### I want to...

**...understand the project**
→ Read [README.md](README.md) then [IMPLEMENTATION_SUMMARY.md](PromoCodeApp/IMPLEMENTATION_SUMMARY.md)

**...get started developing**
→ Follow [GETTING_STARTED.md](PromoCodeApp/GETTING_STARTED.md)

**...understand the architecture**
→ Read [ARCHITECTURE.md](PromoCodeApp/ARCHITECTURE.md)

**...set up Firebase**
→ Follow [FIREBASE_SETUP.md](PromoCodeApp/FIREBASE_SETUP.md)

**...set up Supabase**
→ Follow [SUPABASE_SETUP.md](PromoCodeApp/SUPABASE_SETUP.md)

**...contribute to the project**
→ Read [CONTRIBUTING.md](PromoCodeApp/CONTRIBUTING.md)

**...find a specific file**
→ Use this index or search in your IDE

**...build and run the app**
```bash
cd PromoCodeApp
./gradlew build
./gradlew installDebug
```

**...add a new feature**
1. Read CONTRIBUTING.md for code style
2. Create files in appropriate layer (data/domain/presentation)
3. Follow MVVM pattern
4. Add tests
5. Submit PR

---

## 📞 Getting Help

- **Architecture questions**: See [ARCHITECTURE.md](PromoCodeApp/ARCHITECTURE.md)
- **Setup issues**: See [GETTING_STARTED.md](PromoCodeApp/GETTING_STARTED.md)
- **Firebase problems**: See [FIREBASE_SETUP.md](PromoCodeApp/FIREBASE_SETUP.md)
- **Supabase issues**: See [SUPABASE_SETUP.md](PromoCodeApp/SUPABASE_SETUP.md)
- **Code guidelines**: See [CONTRIBUTING.md](PromoCodeApp/CONTRIBUTING.md)
- **General questions**: Open an issue on GitHub

---

**This Index Created**: November 15, 2025
**Last Updated**: November 15, 2025
**Total Files**: 20+ source files + 8 documentation files
**Total Lines**: 3000+ LOC + 2000+ documentation lines
