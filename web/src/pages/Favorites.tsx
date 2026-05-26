import React, { useEffect, useState } from 'react';
import { Heart } from 'lucide-react';
import { useCouponStore } from '../stores/couponStore';
import { CouponCard } from '../components/CouponCard';
import { EmptyState } from '../components/EmptyState';
import { CouponForm } from '../components/CouponForm';

export function Favorites() {
  const { coupons, loadCoupons, viewMode } = useCouponStore();
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingCoupon, setEditingCoupon] = useState<any>(null);

  useEffect(() => {
    loadCoupons();
  }, [loadCoupons]);

  const favorites = coupons.filter(c => c.isFavorite);

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>Favorites</h1>
      </div>

      {favorites.length > 0 ? (
        <div className={viewMode === 'grid' ? 'grid-view' : 'list-view'}>
          {favorites.map(coupon => (
            <CouponCard 
              key={coupon.id} 
              coupon={coupon} 
              onEdit={(c) => { setEditingCoupon(c); setIsFormOpen(true); }}
            />
          ))}
        </div>
      ) : (
        <EmptyState 
          icon={Heart}
          title="No favorites yet"
          description="Click the heart icon on any coupon to save it to your favorites."
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
