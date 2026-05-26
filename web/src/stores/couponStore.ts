import { create } from 'zustand';
import type { Coupon, DiscountType, SortOption, ViewMode } from '../types';
import * as db from '../services/database';

interface CouponState {
  coupons: Coupon[];
  filteredCoupons: Coupon[];
  searchQuery: string;
  activeFilters: {
    category: string | null;
    discountType: DiscountType | null;
    status: 'active' | 'expired' | 'used' | null;
  };
  sortOption: SortOption;
  viewMode: ViewMode;
  isLoading: boolean;
  selectedCoupon: Coupon | null;
  
  loadCoupons: () => Promise<void>;
  addCoupon: (coupon: Omit<Coupon, 'id' | 'createdAt' | 'updatedAt'>) => Promise<void>;
  updateCoupon: (id: string, updates: Partial<Coupon>) => Promise<void>;
  deleteCoupon: (id: string) => Promise<void>;
  toggleFavorite: (id: string) => Promise<void>;
  markUsed: (id: string) => Promise<void>;
  
  setSearchQuery: (query: string) => void;
  setFilter: (filterType: keyof CouponState['activeFilters'], value: any) => void;
  clearFilters: () => void;
  setSortOption: (option: SortOption) => void;
  setViewMode: (mode: ViewMode) => void;
  
  selectCoupon: (coupon: Coupon | null) => void;
}

export const useCouponStore = create<CouponState>((set, get) => ({
  coupons: [],
  filteredCoupons: [],
  searchQuery: '',
  activeFilters: { category: null, discountType: null, status: null },
  sortOption: 'date_added',
  viewMode: 'grid',
  isLoading: false,
  selectedCoupon: null,

  loadCoupons: async () => {
    set({ isLoading: true });
    await db.seedDatabase();
    const coupons = await db.getAllCoupons();
    set({ coupons, isLoading: false });
    get().setSearchQuery(get().searchQuery); // trigger filter/sort
  },

  addCoupon: async (coupon) => {
    await db.addCoupon(coupon);
    await get().loadCoupons();
  },

  updateCoupon: async (id, updates) => {
    await db.updateCoupon(id, updates);
    await get().loadCoupons();
  },

  deleteCoupon: async (id) => {
    await db.deleteCoupon(id);
    await get().loadCoupons();
  },

  toggleFavorite: async (id) => {
    const coupon = get().coupons.find(c => c.id === id);
    if (coupon) {
      await db.updateCoupon(id, { isFavorite: !coupon.isFavorite });
      await get().loadCoupons();
    }
  },

  markUsed: async (id) => {
    const coupon = get().coupons.find(c => c.id === id);
    if (coupon) {
      await db.updateCoupon(id, { isUsed: !coupon.isUsed });
      await get().loadCoupons();
    }
  },

  setSearchQuery: (query) => {
    set({ searchQuery: query });
    applyFiltersAndSort(set, get);
  },

  setFilter: (filterType, value) => {
    set((state) => ({
      activeFilters: { ...state.activeFilters, [filterType]: value }
    }));
    applyFiltersAndSort(set, get);
  },
  
  clearFilters: () => {
    set({ activeFilters: { category: null, discountType: null, status: null }, searchQuery: '' });
    applyFiltersAndSort(set, get);
  },

  setSortOption: (option) => {
    set({ sortOption: option });
    applyFiltersAndSort(set, get);
  },

  setViewMode: (mode) => set({ viewMode: mode }),
  
  selectCoupon: (coupon) => set({ selectedCoupon: coupon }),
}));

function applyFiltersAndSort(set: any, get: any) {
  const { coupons, searchQuery, activeFilters, sortOption } = get();
  let result = [...coupons];

  // Search
  if (searchQuery) {
    const q = searchQuery.toLowerCase();
    result = result.filter(c => 
      c.merchant.toLowerCase().includes(q) ||
      c.code.toLowerCase().includes(q) ||
      c.description.toLowerCase().includes(q)
    );
  }

  // Filters
  if (activeFilters.category) {
    result = result.filter(c => c.category === activeFilters.category);
  }
  if (activeFilters.discountType) {
    result = result.filter(c => c.discountType === activeFilters.discountType);
  }
  if (activeFilters.status) {
    const now = new Date();
    if (activeFilters.status === 'used') result = result.filter(c => c.isUsed);
    if (activeFilters.status === 'expired') result = result.filter(c => !c.isUsed && new Date(c.expirationDate) < now);
    if (activeFilters.status === 'active') result = result.filter(c => !c.isUsed && new Date(c.expirationDate) >= now);
  }

  // Sort
  result.sort((a, b) => {
    switch (sortOption) {
      case 'date_added':
        return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      case 'expiration':
        return new Date(a.expirationDate).getTime() - new Date(b.expirationDate).getTime();
      case 'discount':
        return (b.discountValue || 0) - (a.discountValue || 0);
      case 'alphabetical':
        return a.merchant.localeCompare(b.merchant);
      default:
        return 0;
    }
  });

  set({ filteredCoupons: result });
}
