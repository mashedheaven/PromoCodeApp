import { LocalNotifications } from '@capacitor/local-notifications';
import type { Coupon } from '../types';

// Generate unique integer IDs from string IDs since capacitor needs int32
function generateIntId(strId: string): number {
  let hash = 0;
  for (let i = 0; i < strId.length; i++) {
    hash = ((hash << 5) - hash) + strId.charCodeAt(i);
    hash |= 0; 
  }
  return Math.abs(hash);
}

export async function requestNotificationPermissions(): Promise<boolean> {
  const result = await LocalNotifications.requestPermissions();
  return result.display === 'granted';
}

export async function scheduleCouponExpiration(coupon: Coupon) {
  if (coupon.isUsed) {
    return cancelCouponExpiration(coupon.id);
  }

  const expDate = new Date(coupon.expirationDate);
  const now = new Date();
  
  if (expDate < now) return;

  const notifications = [];
  const baseId = generateIntId(coupon.id);

  // 1 Week Warning
  const oneWeekBefore = new Date(expDate.getTime() - 7 * 24 * 60 * 60 * 1000);
  if (oneWeekBefore > now) {
    notifications.push({
      id: baseId + 1,
      title: 'Coupon Expiring Soon! ⏰',
      body: `Your ${coupon.merchant} ${coupon.code} coupon expires in 1 week.`,
      schedule: { at: oneWeekBefore }
    });
  }

  // 1 Day Warning
  const oneDayBefore = new Date(expDate.getTime() - 1 * 24 * 60 * 60 * 1000);
  if (oneDayBefore > now) {
    notifications.push({
      id: baseId + 2,
      title: 'Coupon Expires Tomorrow! 🚨',
      body: `Don't forget to use your ${coupon.merchant} discount!`,
      schedule: { at: oneDayBefore }
    });
  }

  if (notifications.length > 0) {
    await LocalNotifications.schedule({ notifications });
  }
}

export async function cancelCouponExpiration(couponId: string) {
  const baseId = generateIntId(couponId);
  await LocalNotifications.cancel({
    notifications: [{ id: baseId + 1 }, { id: baseId + 2 }]
  });
}

export async function triggerNearbyNotification(coupons: Coupon[]) {
  if (coupons.length === 0) return;
  
  const title = coupons.length === 1 
    ? 'Nearby Discount Available! 📍' 
    : `${coupons.length} Discounts Nearby! 📍`;
    
  const body = coupons.length === 1
    ? `You are near ${coupons[0].merchant}. Use code ${coupons[0].code}!`
    : `You are near ${coupons.map(c => c.merchant).join(', ')}. Don't forget to use your codes!`;

  await LocalNotifications.schedule({
    notifications: [{
      id: Math.floor(Math.random() * 1000000),
      title,
      body,
      schedule: { at: new Date(Date.now() + 1000) } // immediate
    }]
  });
}
