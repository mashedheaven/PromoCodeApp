import Dexie, { type Table } from 'dexie';
import type { Coupon, Membership, DashboardStats } from '../types';

class PromoCodeDatabase extends Dexie {
  coupons!: Table<Coupon, string>;
  memberships!: Table<Membership, string>;

  constructor() {
    super('PromoCodeApp');
    this.version(1).stores({
      coupons: 'id, merchant, code, category, discountType, expirationDate, isFavorite, isUsed, createdAt',
      memberships: 'id, organization, category, renewalDate, isActive, createdAt'
    });
  }
}

export const db = new PromoCodeDatabase();

function generateId(): string {
  return crypto.randomUUID();
}

function now(): string {
  return new Date().toISOString();
}

export async function getAllCoupons(): Promise<Coupon[]> {
  return db.coupons.toArray();
}

export async function getCouponById(id: string): Promise<Coupon | undefined> {
  return db.coupons.get(id);
}

export async function addCoupon(coupon: Omit<Coupon, 'id' | 'createdAt' | 'updatedAt'>): Promise<string> {
  const id = generateId();
  const timestamp = now();
  await db.coupons.add({ ...coupon, id, createdAt: timestamp, updatedAt: timestamp });
  return id;
}

export async function updateCoupon(id: string, updates: Partial<Coupon>): Promise<void> {
  await db.coupons.update(id, { ...updates, updatedAt: now() });
}

export async function deleteCoupon(id: string): Promise<void> {
  await db.coupons.delete(id);
}

export async function searchCoupons(query: string): Promise<Coupon[]> {
  const q = query.toLowerCase();
  return db.coupons.filter(c => 
    c.merchant.toLowerCase().includes(q) ||
    c.code.toLowerCase().includes(q) ||
    c.description.toLowerCase().includes(q) ||
    c.category.toLowerCase().includes(q)
  ).toArray();
}

export async function getAllMemberships(): Promise<Membership[]> {
  return db.memberships.toArray();
}

export async function addMembership(m: Omit<Membership, 'id' | 'createdAt' | 'updatedAt'>): Promise<string> {
  const id = generateId();
  const timestamp = now();
  await db.memberships.add({ ...m, id, createdAt: timestamp, updatedAt: timestamp });
  return id;
}

export async function updateMembership(id: string, updates: Partial<Membership>): Promise<void> {
  await db.memberships.update(id, { ...updates, updatedAt: now() });
}

export async function deleteMembership(id: string): Promise<void> {
  await db.memberships.delete(id);
}

export async function getStats(): Promise<DashboardStats> {
  const coupons = await db.coupons.toArray();
  const memberships = await db.memberships.toArray();
  const currentDate = new Date();
  const weekFromNow = new Date(currentDate.getTime() + 7 * 24 * 60 * 60 * 1000);
  const startOfMonth = new Date(currentDate.getFullYear(), currentDate.getMonth(), 1);
  
  const active = coupons.filter(c => !c.isUsed && new Date(c.expirationDate) > currentDate);
  const expiringSoon = active.filter(c => new Date(c.expirationDate) <= weekFromNow);
  const usedThisMonth = coupons.filter(c => c.isUsed && new Date(c.updatedAt) >= startOfMonth);
  const totalSaved = coupons.filter(c => c.isUsed).reduce((sum, c) => sum + (c.discountValue || 0), 0);
  
  return {
    totalCoupons: coupons.length,
    activeCoupons: active.length,
    expiringSoon: expiringSoon.length,
    totalSaved,
    usedThisMonth: usedThisMonth.length,
    totalMemberships: memberships.filter(m => m.isActive).length
  };
}

export async function seedDatabase(): Promise<void> {
  const count = await db.coupons.count();
  if (count > 0) return;
  
  const timestamp = now();
  const currentDate = new Date();
  
  const sampleCoupons: Coupon[] = [
    {
      id: generateId(),
      code: 'SAVE20',
      merchant: 'Amazon',
      description: '20% off selected electronics and accessories',
      discountType: 'percentage',
      discountValue: 20,
      expirationDate: new Date(currentDate.getTime() + 14 * 24 * 60 * 60 * 1000).toISOString(),
      category: 'Electronics',
      isFavorite: true,
      isUsed: false,
      createdAt: timestamp,
      updatedAt: timestamp
    },
    {
      id: generateId(),
      code: 'COFFEELOVER',
      merchant: 'Starbucks',
      description: '$5 off any order over $15',
      discountType: 'fixed',
      discountValue: 5,
      minimumPurchase: 15,
      expirationDate: new Date(currentDate.getTime() + 3 * 24 * 60 * 60 * 1000).toISOString(),
      category: 'Food & Drink',
      isFavorite: false,
      isUsed: false,
      createdAt: timestamp,
      updatedAt: timestamp
    },
    {
      id: generateId(),
      code: 'BOGOFRIDAY',
      merchant: 'Uber Eats',
      description: 'Buy one get one free on participating restaurants',
      discountType: 'bogo',
      discountValue: 0,
      expirationDate: new Date(currentDate.getTime() + 1 * 24 * 60 * 60 * 1000).toISOString(),
      category: 'Food & Drink',
      isFavorite: true,
      isUsed: false,
      createdAt: timestamp,
      updatedAt: timestamp
    },
    {
      id: generateId(),
      code: 'FREESHIP24',
      merchant: 'Nike',
      description: 'Free shipping on all orders',
      discountType: 'free_shipping',
      discountValue: 0,
      expirationDate: new Date(currentDate.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      category: 'Fashion',
      isFavorite: false,
      isUsed: true,
      createdAt: new Date(currentDate.getTime() - 5 * 24 * 60 * 60 * 1000).toISOString(),
      updatedAt: new Date(currentDate.getTime() - 2 * 24 * 60 * 60 * 1000).toISOString()
    }
  ];

  await db.coupons.bulkAdd(sampleCoupons);
}

export async function exportData(): Promise<string> {
  const coupons = await db.coupons.toArray();
  const memberships = await db.memberships.toArray();
  return JSON.stringify({ coupons, memberships, exportedAt: now() }, null, 2);
}

export async function importData(json: string): Promise<void> {
  const data = JSON.parse(json);
  if (data.coupons) {
    await db.coupons.clear();
    await db.coupons.bulkAdd(data.coupons);
  }
  if (data.memberships) {
    await db.memberships.clear();
    await db.memberships.bulkAdd(data.memberships);
  }
}
