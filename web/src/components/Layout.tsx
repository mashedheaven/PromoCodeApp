import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { LayoutDashboard, Ticket, Heart, MapPin, CreditCard, Settings, WifiOff } from 'lucide-react';
import { useOffline } from '../hooks/useOffline';
import { ToastContainer } from './Toast';

export function Layout() {
  const isOffline = useOffline();

  const navLinks = [
    { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
    { to: '/coupons', icon: Ticket, label: 'Coupons' },
    { to: '/favorites', icon: Heart, label: 'Favorites' },
    { to: '/nearby', icon: MapPin, label: 'Near Me' },
    { to: '/memberships', icon: CreditCard, label: 'Memberships' },
    { to: '/settings', icon: Settings, label: 'Settings' },
  ];

  return (
    <div className="app-layout">
      {/* Desktop Sidebar */}
      <aside className="sidebar">
        <div className="nav-header">
          <Ticket className="text-accent-purple" style={{ color: 'var(--accent-purple)' }} size={28} />
          <h1>PromoVault</h1>
        </div>
        
        <nav className="sidebar-nav">
          {navLinks.map((link) => (
            <NavLink 
              key={link.to} 
              to={link.to} 
              className={({ isActive }) => `sidebar-link ${isActive ? 'active' : ''}`}
            >
              <link.icon size={20} />
              <span>{link.label}</span>
            </NavLink>
          ))}
        </nav>

        {isOffline && (
          <div style={{ padding: 'var(--space-md)', margin: 'var(--space-md)', background: 'rgba(245, 158, 11, 0.1)', borderRadius: 'var(--radius-md)', display: 'flex', alignItems: 'center', gap: 'var(--space-sm)', color: 'var(--accent-orange)' }}>
            <WifiOff size={18} />
            <span style={{ fontSize: '0.875rem', fontWeight: 500 }}>Offline Mode</span>
          </div>
        )}
      </aside>

      {/* Main Content Area */}
      <main className="main-content">
        <Outlet />
      </main>

      {/* Mobile Tab Bar */}
      <nav className="mobile-nav">
        {navLinks.filter(l => l.label !== 'Settings').map((link) => (
          <NavLink 
            key={link.to} 
            to={link.to} 
            className={({ isActive }) => `mobile-nav-link ${isActive ? 'active' : ''}`}
          >
            <link.icon size={24} />
            <span>{link.label}</span>
          </NavLink>
        ))}
      </nav>
      
      <ToastContainer />
    </div>
  );
}
