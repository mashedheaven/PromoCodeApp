import React, { useRef } from 'react';
import { Settings as SettingsIcon, Download, Upload, Trash2, Moon, Sun, Bell } from 'lucide-react';
import { useSettingsStore } from '../stores/settingsStore';
import { exportData, importData } from '../services/database';
import { useCouponStore } from '../stores/couponStore';
import { useToast } from '../components/Toast';

export function Settings() {
  const { theme, toggleTheme, notificationsEnabled, updateSettings, defaultRadius, expirationWarningDays } = useSettingsStore();
  const { loadCoupons } = useCouponStore();
  const showToast = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleExport = async () => {
    try {
      const data = await exportData();
      const blob = new Blob([data], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `promovault-export-${new Date().toISOString().split('T')[0]}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      showToast('Data exported successfully', 'success');
    } catch (e) {
      showToast('Export failed', 'error');
    }
  };

  const handleImport = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      const text = await file.text();
      await importData(text);
      await loadCoupons();
      showToast('Data imported successfully', 'success');
    } catch (e) {
      showToast('Import failed. Invalid file format.', 'error');
    }
    
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>Settings</h1>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-xl)' }}>
        <section>
          <h2 style={{ fontSize: '1.25rem', marginBottom: 'var(--space-md)', color: 'var(--text-secondary)' }}>Appearance</h2>
          <div className="glass-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
              {theme === 'dark' ? <Moon size={24} /> : <Sun size={24} />}
              <div>
                <h3 style={{ margin: 0 }}>Theme</h3>
                <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Toggle dark/light mode</p>
              </div>
            </div>
            <button className="btn btn-secondary" onClick={toggleTheme}>
              {theme === 'dark' ? 'Switch to Light' : 'Switch to Dark'}
            </button>
          </div>
        </section>

        <section>
          <h2 style={{ fontSize: '1.25rem', marginBottom: 'var(--space-md)', color: 'var(--text-secondary)' }}>Notifications & Location</h2>
          <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-lg)' }}>
            
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-md)' }}>
                <Bell size={24} />
                <div>
                  <h3 style={{ margin: 0 }}>Push Notifications</h3>
                  <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Alerts for expiring coupons and nearby deals</p>
                </div>
              </div>
              <button 
                className={`btn ${notificationsEnabled ? 'btn-primary' : 'btn-secondary'}`}
                onClick={() => updateSettings({ notificationsEnabled: !notificationsEnabled })}
              >
                {notificationsEnabled ? 'Enabled' : 'Disabled'}
              </button>
            </div>
            
            <div className="divider" />
            
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-sm)' }}>
                <label className="form-label">Geofence Radius (meters)</label>
                <span>{defaultRadius}m</span>
              </div>
              <input 
                type="range" 
                min="100" 
                max="10000" 
                step="100" 
                value={defaultRadius} 
                onChange={(e) => updateSettings({ defaultRadius: Number(e.target.value) })}
                style={{ width: '100%' }}
              />
            </div>
            
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 'var(--space-sm)' }}>
                <label className="form-label">Expiration Warning (days)</label>
                <span>{expirationWarningDays} days</span>
              </div>
              <input 
                type="range" 
                min="1" 
                max="30" 
                value={expirationWarningDays} 
                onChange={(e) => updateSettings({ expirationWarningDays: Number(e.target.value) })}
                style={{ width: '100%' }}
              />
            </div>
          </div>
        </section>

        <section>
          <h2 style={{ fontSize: '1.25rem', marginBottom: 'var(--space-md)', color: 'var(--text-secondary)' }}>Data Management</h2>
          <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
            
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0 }}>Export Data</h3>
                <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Save your data as a JSON file</p>
              </div>
              <button className="btn btn-secondary" onClick={handleExport}>
                <Download size={18} /> Export
              </button>
            </div>
            
            <div className="divider" />
            
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0 }}>Import Data</h3>
                <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Restore from a JSON file</p>
              </div>
              <div>
                <input 
                  type="file" 
                  accept=".json" 
                  style={{ display: 'none' }} 
                  ref={fileInputRef}
                  onChange={handleImport}
                />
                <button className="btn btn-secondary" onClick={() => fileInputRef.current?.click()}>
                  <Upload size={18} /> Import
                </button>
              </div>
            </div>
            
            <div className="divider" />
            
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0, color: 'var(--accent-red)' }}>Danger Zone</h3>
                <p style={{ margin: 0, fontSize: '0.875rem', color: 'var(--text-secondary)' }}>Permanently delete all data</p>
              </div>
              <button 
                className="btn btn-danger" 
                onClick={() => {
                  if (confirm('Are you sure you want to delete ALL your data? This cannot be undone.')) {
                    // db.delete() logic would go here
                    showToast('Clear data functionality placeholder', 'warning');
                  }
                }}
              >
                <Trash2 size={18} /> Clear All
              </button>
            </div>
            
          </div>
        </section>
        
        <div style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.75rem', marginTop: 'var(--space-xl)' }}>
          PromoVault v1.0.0 &copy; 2026
        </div>
      </div>
    </div>
  );
}
