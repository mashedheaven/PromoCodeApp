# PromoCodeApp - Location-Based Promo Code and Membership Tracking Application

## Project Overview

PromoCodeApp is a comprehensive Android mobile application designed to help users manage, organize, and track promotional codes, memberships, and location-based deals. The app combines coupon management with intelligent location-based geofencing to send timely notifications when users are near relevant stores.

## Architecture Overview

The application follows a modern Android development architecture with clear separation of concerns:

```
┌─────────────────────────────────────────┐
│     Presentation Layer (Jetpack Compose) │
│  - UI Components & Screens               │
│  - ViewModels & State Management         │
│  - Navigation                            │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│        Domain Layer (Business Logic)     │
│  - Use Cases                             │
│  - Repository Interfaces                 │
│  - Domain Models                         │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│          Data Layer (Data Management)    │
│  - Room Database (Local)                 │
│  - Supabase API (Remote)                 │
│  - Repository Implementations            │
│  - Data Models & DAOs                    │
└──────────────────┬──────────────────────┘
                   │
┌──────────────────▼──────────────────────┐
│         External Services                │
│  - Firebase Cloud Messaging (FCM)        │
│  - Google Play Services (Geofencing)     │
│  - Location Services (GPS)               │
│  - Supabase (PostgreSQL Backend)         │
└─────────────────────────────────────────┘
```

## Technology Stack

### Frontend
- **Language**: Kotlin (Google's officially recommended language)
- **UI Framework**: Jetpack Compose (Modern declarative UI)
- **State Management**: Flow & StateFlow with ViewModel
- **Navigation**: Jetpack Navigation Compose

### Local Data
- **Database**: Room Database (Type-safe SQLite wrapper)
- **ORM**: Automatic entity-to-data-class mapping

### Remote Services
- **Backend**: Supabase (PostgreSQL-based)
- **API Client**: Retrofit 2
- **Serialization**: Gson
- **HTTP Client**: OkHttp 3

### Location & Notifications
- **Location Services**: Google Play Services - Location API
- **Geofencing**: GeofencingClient (100 concurrent geofences)
- **Push Notifications**: Firebase Cloud Messaging (FCM)
- **Analytics**: Firebase Analytics

### Dependency Injection
- **Framework**: Hilt (Android)
- **Lifecycle**: Jetpack Lifecycle components

## Project Structure

```
PromoCodeApp/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/promocodeapp/
│   │   │   │   ├── data/                    # Data layer
│   │   │   │   │   ├── api/                 # Supabase API service & models
│   │   │   │   │   ├── db/                  # Room database
│   │   │   │   │   │   ├── entity/          # Database entities
│   │   │   │   │   │   ├── dao/             # Data Access Objects
│   │   │   │   │   │   └── AppDatabase.kt   # Database configuration
│   │   │   │   │   └── repository/          # Repository implementations
│   │   │   │   │
│   │   │   │   ├── domain/                  # Domain layer (business logic)
│   │   │   │   │   ├── model/               # Domain models (Coupon, Membership, etc.)
│   │   │   │   │   └── repository/          # Repository interfaces
│   │   │   │   │
│   │   │   │   ├── presentation/            # Presentation layer (UI)
│   │   │   │   │   ├── ui/                  # Jetpack Compose screens
│   │   │   │   │   ├── viewmodel/           # ViewModels & state
│   │   │   │   │   └── navigation/          # Navigation routes
│   │   │   │   │
│   │   │   │   ├── service/                 # Background services
│   │   │   │   │   ├── PromoCodeMessagingService.kt  # FCM service
│   │   │   │   │   └── GeofenceBroadcastReceiver.kt  # Geofence triggers
│   │   │   │   │
│   │   │   │   ├── util/                    # Utility classes
│   │   │   │   │   └── Utilities.kt         # Helper functions
│   │   │   │   │
│   │   │   │   ├── di/                      # Dependency injection
│   │   │   │   │   └── AppModule.kt         # Hilt modules
│   │   │   │   │
│   │   │   │   ├── ui/theme/                # UI theme configuration
│   │   │   │   │   ├── Theme.kt             # Material 3 theme
│   │   │   │   │   └── Type.kt              # Typography
│   │   │   │   │
│   │   │   │   ├── MainActivity.kt          # Entry point
│   │   │   │   └── PromoCodeApplication.kt  # Application class
│   │   │   │
│   │   │   ├── AndroidManifest.xml          # App manifest
│   │   │   └── res/                         # Resources
│   │   │
│   │   └── test/                            # Unit tests
│   │
│   └── build.gradle.kts                     # App-level Gradle configuration
│
├── gradle/                                  # Gradle configuration
│   └── libs.versions.toml                   # Version catalog
│
├── build.gradle.kts                         # Root Gradle configuration
├── settings.gradle.kts                      # Gradle settings
├── gradle.properties                        # Gradle properties
└── README.md                                # This file
```

## Database Schema

### Entities

#### Coupons Table
```sql
CREATE TABLE coupons (
    id BIGINT PRIMARY KEY,
    userId STRING NOT NULL,
    code STRING NOT NULL,
    merchantName STRING NOT NULL,
    discountType STRING, -- PERCENTAGE, FIXED_AMOUNT, BOGO, FREE_SHIPPING
    discountValue REAL NOT NULL,
    minPurchaseAmount REAL,
    description STRING,
    expirationDate BIGINT NOT NULL,
    createdDate BIGINT NOT NULL,
    category STRING,
    isFavorite BOOLEAN DEFAULT 0,
    isUsed BOOLEAN DEFAULT 0,
    isArchived BOOLEAN DEFAULT 0,
    imageUrl STRING,
    barcodeData STRING,
    notes STRING,
    lastModified BIGINT NOT NULL
)
```

#### CouponLocations Table
```sql
CREATE TABLE coupon_locations (
    id BIGINT PRIMARY KEY,
    couponId BIGINT NOT NULL,
    userId STRING NOT NULL,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    radius INT DEFAULT 150,
    geofenceId STRING,
    locationName STRING,
    createdDate BIGINT NOT NULL
)
```

#### Memberships Table
```sql
CREATE TABLE memberships (
    id BIGINT PRIMARY KEY,
    userId STRING NOT NULL,
    organizationName STRING NOT NULL,
    membershipNumber STRING NOT NULL,
    membershipType STRING,
    startDate BIGINT NOT NULL,
    renewalDate BIGINT NOT NULL,
    annualFee REAL,
    monthlyFee REAL,
    currency STRING DEFAULT 'USD',
    benefits STRING,
    notes STRING,
    isActive BOOLEAN DEFAULT 1,
    reminderEnabled BOOLEAN DEFAULT 1,
    reminderDaysBeforeRenewal INT DEFAULT 7,
    createdDate BIGINT NOT NULL,
    lastModified BIGINT NOT NULL
)
```

#### Users Table
```sql
CREATE TABLE users (
    userId STRING PRIMARY KEY,
    email STRING NOT NULL,
    firstName STRING,
    lastName STRING,
    profileImageUrl STRING,
    fcmToken STRING,
    defaultGeofenceRadius INT DEFAULT 150,
    notificationsEnabled BOOLEAN DEFAULT 1,
    proximityNotificationsEnabled BOOLEAN DEFAULT 1,
    expirationNotificationsEnabled BOOLEAN DEFAULT 1,
    membershipNotificationsEnabled BOOLEAN DEFAULT 1,
    locationPermissionGranted BOOLEAN DEFAULT 0,
    backgroundLocationPermissionGranted BOOLEAN DEFAULT 0,
    createdDate BIGINT NOT NULL,
    lastModified BIGINT NOT NULL,
    lastSyncDate BIGINT
)
```

## Key Features Implementation

### 1. Coupon Management
- Create, read, update, delete coupons
- Support for multiple discount types (percentage, fixed amount, BOGO, free shipping)
- Search and filter by merchant, category, expiration
- Favorite/bookmark system
- Barcode/QR code storage

**Implementation**: `CouponViewModel` + `CouponListScreen` + `CouponRepository`

### 2. Location-Based Geofencing
- Associate coupons with locations (lat/lon)
- Create virtual boundaries (100-150m radius minimum)
- Support up to 100 concurrent geofences
- Automatic geofence triggers on entry/exit

**Implementation**: `GeofenceManager` + `GeofenceBroadcastReceiver`

```kotlin
// Example: Creating a geofence
val geofence = geofenceManager.createGeofence(
    id = "coupon_123",
    latitude = 40.7128,
    longitude = -74.0060,
    radiusInMeters = 150f,
    transitionTypes = Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT
)
```

### 3. Push Notifications
- Firebase Cloud Messaging (FCM) integration
- Multiple notification channels:
  - Proximity Alerts (High priority)
  - Expiration Warnings (Default)
  - Membership Reminders (Default)
  - General Notifications (Low)
- Deep linking to coupon details

**Implementation**: `PromoCodeMessagingService`

### 4. Offline Mode
- Complete local database copy with Room
- Pending changes tracking
- Automatic sync on reconnection
- Conflict resolution (last-write-wins)

**Implementation**: `PendingChangeDao` + `SyncRepository`

### 5. Membership Tracking
- Store memberships with renewal dates
- Automatic reminder calculations
- Annual cost tracking
- Location association for gym/club locations

**Implementation**: `MembershipEntity` + `MembershipDao`

## API Integration (Supabase)

### Supabase Configuration

1. **Create a Supabase Project**
   - Visit https://supabase.com
   - Create new project
   - Copy project URL and API key

2. **Update Network Configuration**
   ```kotlin
   // In AppModule.kt
   val baseUrl = "https://your-project.supabase.co/rest/v1/"
   ```

3. **Database Tables**
   - coupons
   - coupon_locations
   - memberships
   - membership_locations
   - users
   - sync_metadata

### API Endpoints

```
POST /coupons              - Create coupon
PUT /coupons/:id           - Update coupon
DELETE /coupons/:id        - Delete coupon
GET /coupons               - List user's coupons

POST /memberships          - Create membership
PUT /memberships/:id       - Update membership
DELETE /memberships/:id    - Delete membership
GET /memberships           - List user's memberships

POST /sync                 - Sync pending changes
POST /notifications/fcm-token - Update FCM token
```

## Permissions Required

### Location Permissions
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```

### Network Permissions
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Notification Permission (Android 13+)
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### Runtime Permission Requests
```kotlin
// Request location permission
ActivityCompat.requestPermissions(
    this,
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
    LOCATION_PERMISSION_CODE
)

// Request background location (must be manual in settings for Android 10+)
// Users must go to Settings > Permissions > Location > Allow all the time
```

## Geofencing Best Practices

1. **Minimum Radius**: Use 100-150 meters minimum
   - Smaller radii may be missed due to GPS accuracy
   - 5-20 meter accuracy in urban areas with good GPS

2. **Number of Geofences**
   - Max 100 per app
   - Recommended 50 or fewer for battery efficiency

3. **Location Update Interval**
   - Foreground: 20 seconds (when app is open)
   - Background: System-optimized (3-5 minutes typical)

4. **Battery Optimization**
   ```kotlin
   val locationRequest = LocationRequest.Builder(20000) // 20 seconds
       .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
       .build()
   ```

5. **Geofence Transitions**
   - ENTER: User entered geofence
   - EXIT: User left geofence
   - DWELL: User stayed in geofence for duration (optional)

## Development Roadmap

### Phase 1: MVP (Current - Weeks 1-4)
- ✅ Core coupon management
- ✅ Local database (Room)
- ✅ Geofencing setup
- ✅ FCM integration
- ⏳ Basic UI with Jetpack Compose
- ⏳ Authentication setup

### Phase 2: Enhanced Features (Weeks 5-8)
- [ ] Barcode/QR code scanning (ML Kit Vision)
- [ ] Comprehensive membership tracking
- [ ] Full offline sync with conflict resolution
- [ ] Advanced search and filtering
- [ ] Analytics dashboard

### Phase 3: iOS Development (Months 3-4)
- [ ] Swift + SwiftUI implementation
- [ ] Feature parity with Android
- [ ] Shared backend infrastructure

### Phase 4: API Integration (Months 5-6)
- [ ] CouponAPI.org integration
- [ ] Voucherify for coupon management
- [ ] Cashback tracking APIs
- [ ] AI-powered recommendations

### Phase 5: Advanced Features (Ongoing)
- [ ] Price comparison
- [ ] Social sharing
- [ ] Community validation
- [ ] Widgets and app clips
- [ ] Wear OS companion
- [ ] Web dashboard

## Building and Running

### Prerequisites
- Android Studio Giraffe or later
- Android SDK 34
- JDK 17 or later
- Gradle 8.0+

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew build

# Run on emulator
./gradlew installDebug

# Run tests
./gradlew test

# Build release
./gradlew assembleRelease

# Check for errors/warnings
./gradlew lint
```

### Development Setup

1. Clone the repository
   ```bash
   git clone https://github.com/mashedheaven/PromoCodeApp.git
   cd PromoCodeApp
   ```

2. Install dependencies
   ```bash
   ./gradlew dependencies
   ```

3. Configure Firebase
   - Add `google-services.json` to `app/` directory
   - Get from Firebase Console

4. Configure Supabase
   - Update `BASE_URL` in `AppModule.kt`
   - Set up database tables

5. Build and run
   ```bash
   ./gradlew installDebug
   ```

## Testing Strategy

### Unit Tests
- Repository and ViewModel logic
- Utility functions and calculations
- Location distance calculations

### Integration Tests
- Database operations
- API interactions
- Synchronization logic

### UI Tests
- Compose screen rendering
- User interactions
- Navigation flows

### Manual Testing Checklist
- [ ] Geofencing accuracy (multiple devices)
- [ ] Battery drain during background location
- [ ] Notification delivery across Android versions
- [ ] Offline mode functionality
- [ ] Sync conflict resolution
- [ ] Permission flows

## Troubleshooting

### Geofences Not Triggering
1. Verify permissions granted (Settings > Apps > PromoCodeApp > Permissions)
2. Check if location is enabled on device
3. Verify geofence radius is at least 100 meters
4. Test with emulator using Mock Location app
5. Check LogCat for "GeofenceBroadcastReceiver" logs

### Notifications Not Appearing
1. Verify FCM token is generated (`adb logcat | grep "FCM Token"`)
2. Check notification channels are created (Settings > Apps > PromoCodeApp > Notifications)
3. Verify Firebase project is correctly configured
4. Check network connectivity

### Database Sync Issues
1. Verify Supabase project URL and key in AppModule
2. Check internet connectivity
3. Review pending_changes table for stuck sync
4. Check logcat for API errors

### High Battery Drain
1. Reduce number of active geofences
2. Increase location update interval
3. Disable background location when not needed
4. Use PRIORITY_BALANCED_POWER_ACCURACY instead of HIGH_ACCURACY

## Security Considerations

1. **Location Privacy**
   - Encrypt location coordinates in transit (HTTPS/TLS)
   - Store only necessary location data
   - Allow users to disable background location
   - Clear location history periodically

2. **Authentication**
   - Use OAuth 2.0 with Supabase Auth
   - Implement JWT token refresh
   - Require strong passwords
   - Support MFA when available

3. **Data Protection**
   - Encrypt sensitive data in database (Room encryption extensions)
   - Use HTTPS for all API calls
   - Implement certificate pinning
   - Avoid storing coupon codes in logs

4. **Fraud Prevention**
   - Validate coupon expiration on server
   - Implement usage limits per coupon
   - Track redemption history
   - Detect suspicious patterns

## Contributing

See CONTRIBUTING.md for guidelines on:
- Code style (Kotlin style guide)
- Commit messages
- Pull request process
- Testing requirements

## License

PromoCodeApp is licensed under the MIT License. See LICENSE file for details.

## Support

For issues, feature requests, or questions:
- GitHub Issues: [PromoCodeApp Issues](https://github.com/mashedheaven/PromoCodeApp/issues)
- Email: support@promocodeapp.com

## Credits

Built with:
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Supabase](https://supabase.com)
- [Firebase](https://firebase.google.com)
- [Google Play Services](https://developers.google.com/android/guides/overview)

## Changelog

### Version 1.0.0 (MVP)
- Initial release
- Coupon management
- Geofencing with notifications
- Room database for offline storage
- Firebase integration

---

**Last Updated**: November 15, 2025
**Maintainer**: Gogul (@mashedheaven)
