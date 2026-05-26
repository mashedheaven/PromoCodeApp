import React, { useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './pages/Dashboard';
import { Coupons } from './pages/Coupons';
import { Favorites } from './pages/Favorites';
import { NearMe } from './pages/NearMe';
import { Memberships } from './pages/Memberships';
import { Settings } from './pages/Settings';
import { useSettingsStore } from './stores/settingsStore';

function App() {
  const { theme } = useSettingsStore();

  useEffect(() => {
    document.documentElement.style.colorScheme = theme;
    if (theme === 'light') {
      document.documentElement.style.setProperty('--bg-primary', 'hsl(225, 25%, 95%)');
      document.documentElement.style.setProperty('--bg-secondary', 'hsl(225, 20%, 100%)');
      document.documentElement.style.setProperty('--bg-card', 'rgba(0, 0, 0, 0.03)');
      document.documentElement.style.setProperty('--bg-card-hover', 'rgba(0, 0, 0, 0.06)');
      document.documentElement.style.setProperty('--border-subtle', 'rgba(0, 0, 0, 0.1)');
      document.documentElement.style.setProperty('--text-primary', 'rgba(0, 0, 0, 0.95)');
      document.documentElement.style.setProperty('--text-secondary', 'rgba(0, 0, 0, 0.6)');
      document.documentElement.style.setProperty('--text-muted', 'rgba(0, 0, 0, 0.35)');
    } else {
      // Reset to dark defaults (which are in CSS :root)
      document.documentElement.style.removeProperty('--bg-primary');
      document.documentElement.style.removeProperty('--bg-secondary');
      document.documentElement.style.removeProperty('--bg-card');
      document.documentElement.style.removeProperty('--bg-card-hover');
      document.documentElement.style.removeProperty('--border-subtle');
      document.documentElement.style.removeProperty('--text-primary');
      document.documentElement.style.removeProperty('--text-secondary');
      document.documentElement.style.removeProperty('--text-muted');
    }
  }, [theme]);

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/coupons" element={<Coupons />} />
          <Route path="/favorites" element={<Favorites />} />
          <Route path="/nearby" element={<NearMe />} />
          <Route path="/memberships" element={<Memberships />} />
          <Route path="/settings" element={<Settings />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
