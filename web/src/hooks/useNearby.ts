import { useState, useEffect } from 'react';
import type { Coupon } from '../types';

export interface NearbyCoupon extends Coupon {
  distance: number; // in meters
}

function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371e3; // Earth radius in meters
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = 
    Math.sin(dLat/2) * Math.sin(dLat/2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * 
    Math.sin(dLon/2) * Math.sin(dLon/2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  return R * c;
}

export function useNearby(coupons: Coupon[], radius: number = 5000) {
  const [nearbyCoupons, setNearbyCoupons] = useState<NearbyCoupon[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!navigator.geolocation) {
      setError('Geolocation is not supported by your browser');
      setIsLoading(false);
      return;
    }

    const success = (position: GeolocationPosition) => {
      const { latitude, longitude } = position.coords;
      const nearby: NearbyCoupon[] = [];

      coupons.forEach(coupon => {
        if (coupon.location && !coupon.isUsed) {
          const distance = calculateDistance(
            latitude, longitude,
            coupon.location.latitude, coupon.location.longitude
          );
          if (distance <= (coupon.location.radius || radius)) {
            nearby.push({ ...coupon, distance });
          }
        }
      });

      nearby.sort((a, b) => a.distance - b.distance);
      setNearbyCoupons(nearby);
      setIsLoading(false);
      setError(null);
    };

    const handleError = (error: GeolocationPositionError) => {
      setError(error.message);
      setIsLoading(false);
    };

    navigator.geolocation.getCurrentPosition(success, handleError, {
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 0
    });

    const watchId = navigator.geolocation.watchPosition(success, handleError, {
      enableHighAccuracy: true,
      timeout: 10000,
      maximumAge: 0
    });

    return () => navigator.geolocation.clearWatch(watchId);
  }, [coupons, radius]);

  return { nearbyCoupons, error, isLoading };
}
