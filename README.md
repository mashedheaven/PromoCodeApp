# PromoCodeApp - Location-Based Promo Code & Membership Tracking

A comprehensive Android application that helps users manage promotional codes, track memberships, and receive location-based notifications when they're near relevant stores. Never miss a deal again!

## 🎯 Project Vision

PromoCodeApp addresses a significant gap where consumers struggle to remember available discounts and deals when physically near relevant stores. By combining coupon management with intelligent location-based geofencing, the app sends timely notifications to help users capture savings opportunities at precisely the right moment and place.

## ✨ Key Features

- **📱 Coupon Management**: Create, organize, and track promotional codes
- **📍 Location-Based Alerts**: Geofencing with automatic notifications when near stores
- **💾 Offline Support**: Full access to coupons without internet connection
- **🔔 Smart Notifications**: Multiple notification types (proximity, expiration, membership)
- **🏪 Membership Tracking**: Track gym memberships, subscriptions, and loyalty programs
- **🎨 Modern UI**: Beautiful Material 3 design with Jetpack Compose
- **⚡ Fast & Responsive**: Built with modern Android Jetpack components

## 🏗️ Architecture

The application follows a clean, layered architecture:

```
Presentation (Jetpack Compose)
       ↓
Domain (Business Logic)
       ↓
Data (Room + Supabase)
       ↓
Services (FCM, Geofencing)
```

**See [ARCHITECTURE.md](PromoCodeApp/ARCHITECTURE.md) for detailed architecture documentation.**

## 🛠️ Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 1.9.21 |
| UI Framework | Jetpack Compose | 2023.10 |
| Local Database | Room | 2.6.1 |
| Remote Backend | Supabase | PostgreSQL |
| API Client | Retrofit | 2.10.0 |
| DI Container | Hilt | 2.48 |
| Notifications | Firebase FCM | 32.7.0 |
| Location | Google Play Services | 18.2.0 |
| Async | Coroutines | 1.7.3 |

## 📁 Project Structure

```
PromoCodeApp/
├── PromoCodeApp/                 # Android application
│   ├── app/
│   │   └── src/main/java/com/promocodeapp/
│   │       ├── data/             # Data layer (Room, API)
│   │       ├── domain/           # Business logic
│   │       ├── presentation/     # UI (Jetpack Compose)
│   │       ├── service/          # Background services
│   │       ├── util/             # Utilities
│   │       ├── di/               # Dependency injection
│   │       └── ui/theme/         # Material 3 theme
│   ├── gradle/                   # Gradle configuration
│   ├── build.gradle.kts          # Root Gradle config
│   └── README.md                 # Detailed documentation
├── README.md                      # This file
└── GETTING_STARTED.md            # Quick start guide
```

## 🚀 Quick Start

### Prerequisites
- Android Studio Giraffe or later
- JDK 17+
- Android SDK 34
- Android device or emulator (API 24+)

### Setup Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/mashedheaven/PromoCodeApp.git
   cd PromoCodeApp
   ```

2. **Open in Android Studio**
   - File > Open > Select `PromoCodeApp` folder

3. **Configure Firebase**
   - Get `google-services.json` from Firebase Console
   - Place in `PromoCodeApp/app/` directory

4. **Configure Supabase**
   - Update `BASE_URL` in `app/src/main/java/com/promocodeapp/di/AppModule.kt`
   - Run SQL migrations (see [SUPABASE_SETUP.md](PromoCodeApp/SUPABASE_SETUP.md))

5. **Build & Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

**See [GETTING_STARTED.md](PromoCodeApp/GETTING_STARTED.md) for detailed setup instructions.**

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| [IMPLEMENTATION_SUMMARY.md](PromoCodeApp/IMPLEMENTATION_SUMMARY.md) | Overview of what's been built |
| [ARCHITECTURE.md](PromoCodeApp/ARCHITECTURE.md) | Detailed architecture & design patterns |
| [GETTING_STARTED.md](PromoCodeApp/GETTING_STARTED.md) | Step-by-step setup guide |
| [BACKEND_QUICK_REFERENCE.md](PromoCodeApp/BACKEND_QUICK_REFERENCE.md) | 5-minute backend integration quick start |
| [BACKEND_INTEGRATION.md](PromoCodeApp/BACKEND_INTEGRATION.md) | Complete backend integration guide |
| [BACKEND_INTEGRATION_SUMMARY.md](PromoCodeApp/BACKEND_INTEGRATION_SUMMARY.md) | Backend implementation summary |
| [BACKEND_INTEGRATION_TESTS.md](PromoCodeApp/BACKEND_INTEGRATION_TESTS.md) | Backend testing guide with 12+ test scenarios |
| [FIREBASE_SETUP.md](PromoCodeApp/FIREBASE_SETUP.md) | Firebase configuration guide |
| [SUPABASE_SETUP.md](PromoCodeApp/SUPABASE_SETUP.md) | Supabase backend setup with SQL migrations |
| [CONTRIBUTING.md](PromoCodeApp/CONTRIBUTING.md) | Development guidelines |
| [PromoCodeApp/README.md](PromoCodeApp/README.md) | Comprehensive project documentation |

## 📊 Project Status

### MVP Implementation: ✅ COMPLETE
### Backend Integration: ✅ COMPLETE

**Completed**:
- ✅ Project structure & Gradle configuration
- ✅ Room database with 7 entities and sync support
- ✅ Domain models & repository interfaces
- ✅ Data repository implementations
- ✅ Jetpack Compose UI screens
- ✅ ViewModels with state management
- ✅ Hilt dependency injection
- ✅ Firebase integration with FCM
- ✅ Geofencing implementation
- ✅ Material 3 theme & design
- ✅ **Supabase backend integration**
- ✅ **Offline-first synchronization**
- ✅ **Background sync service with WorkManager**
- ✅ **Authentication repository**
- ✅ **Comprehensive backend documentation**
- ✅ **Testing guide with 12+ test scenarios**

**In Progress**:
- 🔄 Authentication UI screens
- 🔄 End-to-end testing

**Planned**:
- 📋 Barcode/QR code scanning
- 📋 Membership management screens
- 📋 iOS port
- 📋 Advanced features (price comparison, social sharing)

## 🔄 Development Phases

### Phase 1: MVP (Current - Weeks 1-4) ✅
Core coupon management, geofencing, notifications

### Phase 2: Enhanced Features (Weeks 5-8)
Barcode scanning, membership tracking, offline sync

### Phase 3: iOS Development (Months 3-4)
Swift + SwiftUI port with feature parity

### Phase 4: API Integration (Months 5-6)
CouponAPI, Voucherify, cashback tracking

### Phase 5: Advanced Features (Ongoing)
Price comparison, social sharing, community features

## 🔐 Permissions

The app requires the following permissions:

- **Location**: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_BACKGROUND_LOCATION
- **Network**: INTERNET, ACCESS_NETWORK_STATE
- **Notifications**: POST_NOTIFICATIONS (Android 13+)
- **Camera**: CAMERA (future: barcode scanning)
- **Storage**: READ/WRITE_EXTERNAL_STORAGE

## 🧪 Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run with coverage
./gradlew testDebugUnitTestCoverage
```

## 🐛 Troubleshooting

### Geofences not triggering?
1. Check location permission in Settings
2. Enable location services
3. Use radius ≥ 100 meters
4. See [PromoCodeApp/README.md](PromoCodeApp/README.md#troubleshooting)

### Notifications not appearing?
1. Verify Firebase is configured correctly
2. Check notification channels in app settings
3. See [FIREBASE_SETUP.md](PromoCodeApp/FIREBASE_SETUP.md#troubleshooting)

### Database sync issues?
1. Verify Supabase credentials in AppModule
2. Check internet connectivity
3. See [SUPABASE_SETUP.md](PromoCodeApp/SUPABASE_SETUP.md#troubleshooting)

## 📖 Code Examples

### Creating a Coupon
```kotlin
viewModel.createCoupon(
    code = "SAVE20",
    merchantName = "Target",
    discountType = DiscountType.Percentage(20.0),
    discountValue = 20.0,
    expirationDate = System.currentTimeMillis() + (30 * 24 * 60 * 60 * 1000)
)
```

### Creating a Geofence
```kotlin
val geofence = geofenceManager.createGeofence(
    id = "coupon_123",
    latitude = 40.7128,
    longitude = -74.0060,
    radiusInMeters = 150f
)
```

### Receiving Location Changes
```kotlin
// In ViewModel
val coupons: Flow<List<Coupon>> = couponRepository.getUserCoupons(userId)

// In Compose
val coupons by viewModel.coupons.collectAsStateWithLifecycle()
```

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](PromoCodeApp/CONTRIBUTING.md) for:
- Code style guidelines
- Commit message format
- PR process
- Testing requirements

## 📝 License

PromoCodeApp is licensed under the MIT License. See [LICENSE](PromoCodeApp/LICENSE) for details.

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/mashedheaven/PromoCodeApp/issues)
- **Discussions**: [GitHub Discussions](https://github.com/mashedheaven/PromoCodeApp/discussions)
- **Email**: support@promocodeapp.com

## 🙏 Credits

Built with:
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Modern UI toolkit
- [Room Database](https://developer.android.com/training/data-storage/room) - Local storage
- [Firebase](https://firebase.google.com) - Cloud messaging
- [Supabase](https://supabase.com) - Backend infrastructure
- [Hilt](https://dagger.dev/hilt) - Dependency injection
- [Google Play Services](https://developers.google.com/android/guides/overview) - Location services

## 🎯 Market Opportunity

The global coupon and discount app market is expanding rapidly as consumers seek to maximize savings. Location-based services have proven effective at driving engagement, with businesses reporting significant increases in foot traffic from proximity-based offers. PromoCodeApp combines these powerful trends to capture significant market value.

---

**Last Updated**: November 15, 2025  
**Version**: 1.0.0 (MVP)  
**Maintainer**: Gogul (@mashedheaven)  
**Repository**: [GitHub](https://github.com/mashedheaven/PromoCodeApp)
