import React, { useEffect, useState } from 'react';
import { MapPin, Navigation } from 'lucide-react';
import { useCouponStore } from '../stores/couponStore';
import { useSettingsStore } from '../stores/settingsStore';
import { useNearby } from '../hooks/useNearby';
import { CouponCard } from '../components/CouponCard';
import { EmptyState } from '../components/EmptyState';
import { CouponForm } from '../components/CouponForm';

export function NearMe() {
  const { coupons, loadCoupons, viewMode } = useCouponStore();
  const { defaultRadius } = useSettingsStore();
  const { nearbyCoupons, error, isLoading } = useNearby(coupons, defaultRadius);
  
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingCoupon, setEditingCoupon] = useState<any>(null);
  const [permissionState, setPermissionState] = useState<PermissionState | null>(null);

  useEffect(() => {
    loadCoupons();
    
    // Check permission status
    if (navigator.permissions && navigator.permissions.query) {
      navigator.permissions.query({ name: 'geolocation' }).then(result => {
        setPermissionState(result.state);
        result.onchange = () => setPermissionState(result.state);
      });
    }
  }, [loadCoupons]);

  const requestLocation = () => {
    navigator.geolocation.getCurrentPosition(() => {}, () => {});
  };

  const formatDistance = (meters: number) => {
    if (meters < 1000) return `${Math.round(meters)}m away`;
    return `${(meters / 1000).toFixed(1)}km away`;
  };

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>Near Me</h1>
      </div>

      {permissionState === 'prompt' || error === 'User denied Geolocation' ? (
        <div className="glass-card" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', padding: 'var(--space-2xl)' }}>
          <MapPin size={48} className="text-accent-cyan" style={{ marginBottom: 'var(--space-md)' }} />
          <h2 style={{ marginBottom: 'var(--space-sm)' }}>Location Access Required</h2>
          <p style={{ color: 'var(--text-secondary)', marginBottom: 'var(--space-xl)', maxWidth: '400px' }}>
            We need your location to show you relevant coupons when you're near participating stores.
          </p>
          <button className="btn btn-primary" onClick={requestLocation}>
            <Navigation size={18} />
            Enable Location
          </button>
        </div>
      ) : isLoading && !error ? (
        <div className="grid-view">
          {[1, 2].map(i => <div key={i} className="glass-card skeleton" style={{ height: '200px' }} />)}
        </div>
      ) : nearbyCoupons.length > 0 ? (
        <div className={viewMode === 'grid' ? 'grid-view' : 'list-view'}>
          {nearbyCoupons.map(coupon => (
            <div key={coupon.id} style={{ position: 'relative' }}>
              <div style={{ position: 'absolute', top: '-10px', right: '10px', zIndex: 10 }}>
                <span className="badge" style={{ background: 'var(--accent-purple)', color: 'white', boxShadow: '0 4px 6px rgba(0,0,0,0.3)' }}>
                  <MapPin size={12} style={{ display: 'inline', marginRight: '4px' }} />
                  {formatDistance(coupon.distance)}
                </span>
              </div>
              <CouponCard 
                coupon={coupon} 
                onEdit={(c) => { setEditingCoupon(c); setIsFormOpen(true); }}
              />
            </div>
          ))}
        </div>
      ) : (
        <EmptyState 
          icon={MapPin}
          title="No coupons nearby"
          description={`We couldn't find any saved coupons within ${defaultRadius / 1000}km of your current location.`}
        />
      )}

      {isFormOpen && (
        <CouponForm 
          coupon={editingCoupon} 
          onClose={() => { setIsFormOpen(false); setEditingCoupon(null); }} 
        />
      )}
    </div>
  );
}
