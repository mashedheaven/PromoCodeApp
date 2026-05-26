import { create } from 'zustand';
import type { Membership } from '../types';
import * as db from '../services/database';

interface MembershipState {
  memberships: Membership[];
  isLoading: boolean;
  selectedMembership: Membership | null;
  
  loadMemberships: () => Promise<void>;
  addMembership: (membership: Omit<Membership, 'id' | 'createdAt' | 'updatedAt'>) => Promise<void>;
  updateMembership: (id: string, updates: Partial<Membership>) => Promise<void>;
  deleteMembership: (id: string) => Promise<void>;
  selectMembership: (membership: Membership | null) => void;
}

export const useMembershipStore = create<MembershipState>((set, get) => ({
  memberships: [],
  isLoading: false,
  selectedMembership: null,

  loadMemberships: async () => {
    set({ isLoading: true });
    const memberships = await db.getAllMemberships();
    set({ memberships, isLoading: false });
  },

  addMembership: async (membership) => {
    await db.addMembership(membership);
    await get().loadMemberships();
  },

  updateMembership: async (id, updates) => {
    await db.updateMembership(id, updates);
    await get().loadMemberships();
  },

  deleteMembership: async (id) => {
    await db.deleteMembership(id);
    await get().loadMemberships();
  },

  selectMembership: (membership) => set({ selectedMembership: membership }),
}));
