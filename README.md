# PromoVault (formerly PromoCodeApp)

PromoVault is a modern, offline-first application designed to help users organize, track, and utilize their promotional codes, coupons, and memberships.

## 🚀 Architecture Overview

The application has been completely rebuilt using a **Web-First PWA (Progressive Web App)** approach wrapped in **Capacitor**. This single unified codebase powers the Web, Android, and iOS applications with absolute feature parity.

- **Frontend**: React + TypeScript + Vite
- **Styling**: Custom CSS Design System (Dark mode, Glassmorphism)
- **State Management**: Zustand
- **Database**: Dexie.js (IndexedDB) for local-first offline storage
- **Native Wrappers**: Capacitor (Android & iOS)

For detailed technical architecture and file structure, see [ARCHITECTURE.md](./ARCHITECTURE.md).

## ✨ Features

- **Offline-First**: Add, edit, and delete coupons completely offline. Data syncs locally via IndexedDB.
- **Cross-Platform**: Runs beautifully on Web, Android, and iOS.
- **Near Me**: Uses native geolocation to find saved coupons for stores nearby (calculated via Haversine formula).
- **Dashboard Analytics**: Tracks active deals, expiring soon, and total money saved.
- **Memberships**: Keep track of gym memberships and subscriptions with renewal countdowns.
- **Import/Export**: Full JSON data export and import capabilities.

## 🛠 Getting Started

### 1. Web Application (Development)

To run the web app locally in your browser:

```bash
cd web
npm install
npm run dev
```

### 2. Android Native App

The native Android Studio project is automatically kept in sync with the web app via Capacitor.

```bash
cd web
npm run build
npx cap sync android
```
Then, open the `web/android` folder in **Android Studio** and click **Run**.

### 3. iOS Native App

The native Xcode project is automatically kept in sync with the web app via Capacitor.

```bash
cd web
npm run build
npx cap sync ios
```
Then, open `web/ios/App/App.xcworkspace` in **Xcode** and click **Run**.

## 🎨 Design System

The application utilizes a premium dark-mode aesthetic with glassmorphism elements. 
All design tokens (colors, spacing, animations) are defined in `web/src/index.css`. 
Key visual elements include:
- Deep navy/black backgrounds (`--bg-primary`)
- Vibrant purple, cyan, and magenta gradients (`--gradient-primary`)
- Blurred, semi-transparent cards (`.glass-card`)

## 📝 Historical Context

The original project (PromoCodeApp) started as a pure Kotlin native Android app with a Supabase backend. Due to architectural issues and broken compilation, the project was migrated to this single-codebase Capacitor architecture in May 2026. The original requirements can still be found in `app description.txt`.
