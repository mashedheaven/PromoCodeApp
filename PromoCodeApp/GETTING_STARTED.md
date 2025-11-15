# Getting Started Guide

## Quick Start (5 minutes)

### 1. Clone the Repository
```bash
git clone https://github.com/mashedheaven/PromoCodeApp.git
cd PromoCodeApp
```

### 2. Open in Android Studio
- Android Studio > File > Open
- Navigate to `PromoCodeApp` folder
- Click "Open"

### 3. Wait for Gradle Sync
- Let Gradle download all dependencies
- This may take 2-5 minutes on first run

### 4. Setup Firebase
- Download `google-services.json` from Firebase Console
- Place in `PromoCodeApp/app/` directory
- Sync Gradle again

### 5. Setup Supabase
- Update `BASE_URL` in `app/src/main/java/com/promocodeapp/di/AppModule.kt`
- Replace `YOUR-PROJECT` with your Supabase project name
- Replace `YOUR-ANON-KEY` with your Supabase anon key

### 6. Run the App
- Connect Android device or start emulator
- Click "Run" (Shift + F10) in Android Studio
- App should install and launch

## Detailed Setup

### System Requirements
- OS: macOS, Linux, or Windows
- RAM: 8GB minimum (16GB recommended)
- Disk: 5GB free space
- Android SDK: API level 24+ (Android 7.0)

### Installation Steps

#### Step 1: Install Android Studio
1. Download from [developer.android.com](https://developer.android.com/studio)
2. Run installer and follow wizard
3. Choose "Standard" installation
4. Accept default locations

#### Step 2: Install Required SDKs
1. Open Android Studio > Settings > SDK Manager
2. Install:
   - Android SDK 34
   - Android SDK Build-Tools 34.0.0
   - Android Emulator
   - Android SDK Platform-Tools
   - Google Play Services (optional)

#### Step 3: Setup Development Environment
```bash
# Set ANDROID_SDK_ROOT (macOS/Linux)
export ANDROID_SDK_ROOT=$HOME/Library/Android/sdk
export PATH=$ANDROID_SDK_ROOT/platform-tools:$PATH

# Or set via ~/.bash_profile or ~/.zshrc for persistence
echo 'export ANDROID_SDK_ROOT=$HOME/Library/Android/sdk' >> ~/.zshrc
echo 'export PATH=$ANDROID_SDK_ROOT/platform-tools:$PATH' >> ~/.zshrc
source ~/.zshrc
```

#### Step 4: Create Virtual Device
1. Android Studio > Device Manager > Create new device
2. Select device (e.g., Pixel 7)
3. Select system image (API 34)
4. Finish configuration
5. Launch emulator

#### Step 5: Configure Backend Services

**Firebase Setup:**
1. Go to https://console.firebase.google.com
2. Create new project or use existing
3. Register Android app
4. Download `google-services.json`
5. Place in `app/` directory

**Supabase Setup:**
1. Go to https://supabase.com
2. Create new project
3. Copy Project URL and anon key
4. Run SQL migrations (see SUPABASE_SETUP.md)
5. Update AppModule.kt

#### Step 6: Build and Run
```bash
cd PromoCodeApp

# Build
./gradlew build

# Run on device
./gradlew installDebug

# View logs
adb logcat | grep "PromoCode"
```

## Common Issues and Solutions

### Issue: Gradle sync fails
**Solution:**
```bash
# Clear cache
./gradlew clean

# Force dependency update
./gradlew build --refresh-dependencies

# Check internet connection
```

### Issue: Emulator won't start
**Solution:**
1. Check if virtualization is enabled in BIOS
2. Try different emulator version
3. Allocate more RAM to emulator
4. Use physical device instead

### Issue: FCM token not generating
**Solution:**
1. Ensure Google Play Services is installed on emulator
2. Verify Firebase project is properly configured
3. Check app has internet permission
4. Wait 10-30 seconds after app launch

### Issue: Geofencing not working
**Solution:**
1. Grant location permission in app settings
2. Enable location services on device
3. Use actual device (geofencing limited on some emulators)
4. Create geofence with radius >= 100 meters

### Issue: Database migration fails
**Solution:**
1. Check if database exists
2. Verify tables are created
3. Run migration manually via SQL editor
4. Check database connectivity

## Development Workflow

### Daily Development
```bash
# Start emulator
emulator -avd Pixel_7_API_34

# In another terminal, watch app changes
cd PromoCodeApp
./gradlew build

# Run app
./gradlew installDebug

# Monitor logs
adb logcat -s "PromoCode"
```

### Making Changes
1. Edit code in `app/src/main/java/com/promocodeapp/`
2. Build with Ctrl+B
3. Run with Shift+F10
4. Check logcat for errors

### Running Tests
```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# With coverage
./gradlew testDebugUnitTestCoverage
```

## Project Structure Tour

```
PromoCodeApp/
├── app/                              # Main app module
│   ├── src/main/java/com/promocodeapp/
│   │   ├── presentation/             # UI Screens (Jetpack Compose)
│   │   │   ├── ui/
│   │   │   │   └── CouponListScreen.kt
│   │   │   ├── viewmodel/
│   │   │   │   └── CouponViewModel.kt
│   │   │   └── navigation/
│   │   │       └── Navigation.kt
│   │   │
│   │   ├── domain/                   # Business Logic
│   │   │   ├── model/
│   │   │   │   └── Models.kt
│   │   │   └── repository/
│   │   │       └── Repositories.kt
│   │   │
│   │   ├── data/                     # Data Management
│   │   │   ├── db/
│   │   │   │   ├── entity/
│   │   │   │   │   └── Entities.kt
│   │   │   │   ├── dao/
│   │   │   │   │   └── Daos.kt
│   │   │   │   └── AppDatabase.kt
│   │   │   ├── api/
│   │   │   │   └── SupabaseApiService.kt
│   │   │   └── repository/
│   │   │       └── CouponRepositoryImpl.kt
│   │   │
│   │   ├── service/                  # Background Services
│   │   │   ├── PromoCodeMessagingService.kt
│   │   │   └── GeofenceBroadcastReceiver.kt
│   │   │
│   │   ├── util/                     # Utilities
│   │   │   └── Utilities.kt
│   │   │
│   │   ├── di/                       # Dependency Injection
│   │   │   └── AppModule.kt
│   │   │
│   │   ├── ui/theme/                 # UI Theme
│   │   │   ├── Theme.kt
│   │   │   └── Type.kt
│   │   │
│   │   ├── MainActivity.kt           # Entry Point
│   │   └── PromoCodeApplication.kt   # App Class
│   │
│   └── build.gradle.kts              # Dependencies
│
├── gradle/                           # Gradle Config
│   └── libs.versions.toml
│
└── build.gradle.kts                  # Root Config
```

## Next Steps

1. **Read the Architecture**: Open `README.md` for detailed architecture
2. **Setup Backend**: Follow `FIREBASE_SETUP.md` and `SUPABASE_SETUP.md`
3. **Explore Code**: Start with `MainActivity.kt` and `CouponListScreen.kt`
4. **Write Tests**: Add tests in `app/src/test/` and `app/src/androidTest/`
5. **Build Features**: Follow the phased roadmap in README.md

## Useful Commands

```bash
# Show all tasks
./gradlew tasks

# Clean
./gradlew clean

# Build
./gradlew build

# Install on device
./gradlew installDebug

# Run specific activity
./gradlew installDebug && adb shell am start -n com.promocodeapp/.MainActivity

# Show logs
adb logcat

# Filter logs
adb logcat | grep "PromoCode"

# Clear logs
adb logcat -c

# View device info
adb devices

# Take screenshot
adb shell screencap -p /sdcard/screenshot.png

# List installed packages
adb shell pm list packages
```

## Getting Help

- **Android Documentation**: https://developer.android.com
- **Jetpack Compose**: https://developer.android.com/jetpack/compose
- **Room Database**: https://developer.android.com/training/data-storage/room
- **Firebase**: https://firebase.google.com/docs
- **Supabase**: https://supabase.com/docs
- **GitHub Issues**: https://github.com/mashedheaven/PromoCodeApp/issues

---

**Ready to start?** Let's build something amazing! 🚀
