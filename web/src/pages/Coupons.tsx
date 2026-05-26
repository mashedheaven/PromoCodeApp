import React, { useEffect, useState } from 'react';
import { Ticket } from 'lucide-react';
import { useCouponStore } from '../stores/couponStore';
import { CouponCard } from '../components/CouponCard';
import { SearchBar } from '../components/SearchBar';
import { EmptyState } from '../components/EmptyState';
import { CouponForm } from '../components/CouponForm';

export function Coupons() {
  const { filteredCoupons, loadCoupons, viewMode, isLoading } = useCouponStore();
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingCoupon, setEditingCoupon] = useState<any>(null);

  useEffect(() => {
    loadCoupons();
  }, [loadCoupons]);

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>My Coupons</h1>
        <button className="btn btn-primary" onClick={() => setIsFormOpen(true)}>
          <Ticket size={18} />
          Add Coupon
        </button>
      </div>

      <SearchBar />

      {isLoading ? (
        <div className={viewMode === 'grid' ? 'grid-view' : 'list-view'}>
          {[1, 2, 3, 4].map(i => (
            <div key={i} className="glass-card skeleton" style={{ height: '200px' }} />
          ))}
        </div>
      ) : filteredCoupons.length > 0 ? (
        <div className={viewMode === 'grid' ? 'grid-view' : 'list-view'}>
          {filteredCoupons.map(coupon => (
            <CouponCard 
              key={coupon.id} 
              coupon={coupon} 
              onEdit={(c) => { setEditingCoupon(c); setIsFormOpen(true); }}
            />
          ))}
        </div>
      ) : (
        <EmptyState 
          icon={Ticket}
          title="No coupons found"
          description="Try adjusting your filters or search query."
          actionLabel="Clear Filters"
          onAction={() => useCouponStore.getState().clearFilters()}
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
