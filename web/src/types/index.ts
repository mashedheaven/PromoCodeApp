export type DiscountType = 'percentage' | 'fixed' | 'bogo' | 'free_shipping';
export type CouponStatus = 'active' | 'expired' | 'used' | 'archived';
export type SortOption = 'date_added' | 'expiration' | 'discount' | 'alphabetical';
export type ViewMode = 'grid' | 'list';

export interface Location {
  id: string;
  latitude: number;
  longitude: number;
  address: string;
  radius: number;
}

export interface Coupon {
  id: string;
  code: string;
  merchant: string;
  description: string;
  discountType: DiscountType;
  discountValue: number;
  minimumPurchase?: number;
  expirationDate: string;
  category: string;
  isFavorite: boolean;
  isUsed: boolean;
  location?: Location;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Membership {
  id: string;
  name: string;
  organization: string;
  membershipNumber?: string;
  startDate: string;
  renewalDate: string;
  fee: number;
  feePeriod: 'monthly' | 'quarterly' | 'annual';
  benefits: string[];
  category: string;
  isActive: boolean;
  location?: Location;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AppSettings {
  theme: 'dark' | 'light';
  defaultRadius: number;
  notificationsEnabled: boolean;
  expirationWarningDays: number;
  viewMode: ViewMode;
}

export interface DashboardStats {
  totalCoupons: number;
  activeCoupons: number;
  expiringSoon: number;
  totalSaved: number;
  usedThisMonth: number;
  totalMemberships: number;
}
