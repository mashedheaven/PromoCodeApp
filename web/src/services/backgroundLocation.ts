import { registerPlugin } from '@capacitor/core';
import type { BackgroundGeolocationPlugin, Location } from '@capacitor-community/background-geolocation';
import { db } from './database';
import { triggerNearbyNotification } from './notifications';

const BackgroundGeolocation = registerPlugin<BackgroundGeolocationPlugin>('BackgroundGeolocation');

let isWatching = false;
let lastNotifiedLocation: { lat: number, lng: number } | null = null;
const NOTIFY_COOLDOWN_DISTANCE = 500; // meters

function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371e3;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = 
    Math.sin(dLat/2) * Math.sin(dLat/2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * 
    Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

export async function initializeBackgroundGeolocation(defaultRadius: number) {
  if (isWatching) return;
  
  BackgroundGeolocation.addWatcher(
    {
      backgroundMessage: "Cancel to prevent battery drain.",
      backgroundTitle: "Tracking Nearby Deals",
      requestPermissions: true,
      stale: false,
      distanceFilter: 100 // update every 100 meters
    },
    async (location: Location | undefined, error: any) => {
      if (error) {
        console.error("Background location error", error);
        return;
      }
      
      if (!location) return;

      if (lastNotifiedLocation) {
        const distFromLast = calculateDistance(
          location.latitude, location.longitude,
          lastNotifiedLocation.lat, lastNotifiedLocation.lng
        );
        if (distFromLast < NOTIFY_COOLDOWN_DISTANCE) return;
      }

      const coupons = await db.coupons.toArray();
      const now = new Date();
      const activeCoupons = coupons.filter(c => !c.isUsed && new Date(c.expirationDate) >= now && c.location);
      
      const nearbyCoupons = activeCoupons.filter(c => {
        const dist = calculateDistance(location.latitude, location.longitude, c.location!.latitude, c.location!.longitude);
        return dist <= (c.location!.radius || defaultRadius);
      });

      if (nearbyCoupons.length > 0) {
        await triggerNearbyNotification(nearbyCoupons);
        lastNotifiedLocation = { lat: location.latitude, lng: location.longitude };
      }
    }
  ).then((watcher_id: string) => {
    isWatching = true;
  });
}
