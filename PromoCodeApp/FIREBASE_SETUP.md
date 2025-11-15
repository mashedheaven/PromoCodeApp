# Firebase Setup Guide

## Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a project"
3. Enter project name: "PromoCodeApp"
4. Enable Google Analytics (optional)
5. Select or create Google Cloud project
6. Click "Create project"

## Step 2: Register Android App

1. In Firebase Console, click "Android" icon
2. Enter package name: `com.promocodeapp`
3. Enter app nickname: "PromoCode App"
4. Download `google-services.json`
5. Place in `PromoCodeApp/app/` directory
6. Follow setup instructions

## Step 3: Set Up Firebase Cloud Messaging

1. In Firebase Console, go to "Cloud Messaging"
2. Note the Server API Key (for backend notifications)
3. Go to "Service Accounts" and generate private key JSON
4. Store securely for backend notification sending

## Step 4: Configure Notification Channels

The app automatically creates three notification channels:

- **Proximity Alerts** (High priority) - for geofence entries
- **Expiration Warnings** (Default) - for upcoming expiration
- **Membership Reminders** (Default) - for renewal reminders

Users can manage these in Settings > Apps > PromoCodeApp > Notifications

## Step 5: Test FCM

```bash
# Get FCM token from app logs
adb logcat | grep "FCM Token"

# Send test notification via Firebase Console
# Messaging > Send your first message
```

## Sending Notifications (Backend)

### Using Firebase Admin SDK (Node.js Example)

```javascript
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
});

const message = {
    notification: {
        title: 'You\'re near a store with coupons!',
        body: 'Target has 3 coupons available'
    },
    data: {
        type: 'proximity',
        coupon_id: '123',
        merchant_name: 'Target'
    },
    token: 'FCM_TOKEN_HERE'
};

admin.messaging().send(message)
    .then((response) => {
        console.log('Successfully sent message:', response);
    })
    .catch((error) => {
        console.log('Error sending message:', error);
    });
```

## Troubleshooting

- **google-services.json not found**: Ensure it's in `PromoCodeApp/app/`
- **FCM token not generating**: Check if Google Play Services is installed on device/emulator
- **Notifications not delivered**: Verify notification channel is not disabled by user
