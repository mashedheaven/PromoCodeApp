import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AppSettings, ViewMode } from '../types';

interface SettingsState extends AppSettings {
  updateSettings: (updates: Partial<AppSettings>) => void;
  toggleTheme: () => void;
  resetSettings: () => void;
}

const defaultSettings: AppSettings = {
  theme: 'dark',
  defaultRadius: 5000,
  notificationsEnabled: true,
  expirationWarningDays: 7,
  viewMode: 'grid',
};

export const useSettingsStore = create<SettingsState>()(
  persist(
    (set) => ({
      ...defaultSettings,
      
      updateSettings: (updates) => set((state) => ({ ...state, ...updates })),
      
      toggleTheme: () => set((state) => ({ theme: state.theme === 'dark' ? 'light' : 'dark' })),
      
      resetSettings: () => set(defaultSettings),
    }),
    {
      name: 'promovault-settings',
    }
  )
);
