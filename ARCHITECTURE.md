# Application Architecture

## The Capacitor Bridge

PromoVault relies on **Capacitor** to bridge the web application (React/Vite) to native mobile platforms (iOS and Android).

Instead of maintaining three separate codebases (Kotlin, Swift, and React), we maintain exactly **one** codebase located in the `/web` directory. 

When `npm run build` is executed, Vite compiles the React code into static HTML/CSS/JS in `web/dist`. 
When `npx cap sync` is executed, Capacitor copies those static assets into the native `/web/android` and `/web/ios` projects, wrapping them in an optimized native WebView.

## File Structure

```text
/
├── README.md               # Main entry point documentation
├── ARCHITECTURE.md         # This file
├── app description.txt     # Original app requirements and user stories
└── web/                    # The SINGLE source of truth for all code
    ├── android/            # Generated Android Studio project
    ├── ios/                # Generated Xcode project
    ├── public/             # PWA assets (manifest, service worker, icons)
    ├── src/
    │   ├── components/     # Reusable React components (Cards, Forms, etc.)
    │   ├── hooks/          # Custom React hooks (useNearby, useOffline, useDebounce)
    │   ├── pages/          # Top-level React views (Dashboard, Coupons, etc.)
    │   ├── services/       # Core business logic (database.ts)
    │   ├── stores/         # Zustand global state (couponStore, settingsStore)
    │   ├── types/          # TypeScript interfaces (Coupon, Membership, etc.)
    │   ├── App.tsx         # React Router and Theme initialization
    │   ├── main.tsx        # React DOM entry point
    │   └── index.css       # Global design system and CSS variables
    ├── index.html          # HTML Shell
    ├── package.json        # NPM dependencies and scripts
    ├── capacitor.config.ts # Capacitor configuration
    └── vite.config.ts      # Vite bundler configuration
```

## Data Layer (Local-First)

Currently, the app is entirely **Local-First**. 

- **Dexie.js**: A minimalist wrapper for IndexedDB is used in `web/src/services/database.ts`.
- **State**: The database is the source of truth. Zustand stores (`couponStore.ts`, `membershipStore.ts`) fetch data from Dexie and hold it in memory for instant React rendering.
- **Sync**: Since all data is local, the `Settings` page provides a JSON export/import mechanism for users to manually backup their data.

*Note: The architecture is designed so that a cloud backend (like Supabase) can be easily reintroduced by modifying the functions in `services/database.ts` to push/pull from a remote API without changing the UI components.*
