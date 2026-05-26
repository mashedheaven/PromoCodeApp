import React, { useEffect, useState } from 'react';
import { Ticket, CheckCircle, Clock, DollarSign, ShoppingBag, CreditCard } from 'lucide-react';
import { getStats } from '../services/database';
import type { DashboardStats } from '../types';
import { useCouponStore } from '../stores/couponStore';
import { CouponCard } from '../components/CouponCard';
import { CouponForm } from '../components/CouponForm';

function AnimatedNumber({ value, prefix = '' }: { value: number, prefix?: string }) {
  const [displayValue, setDisplayValue] = useState(0);

  useEffect(() => {
    let start = 0;
    const end = value;
    if (start === end) return;
    
    let totalDuration = 1000;
    let incrementTime = Math.max(Math.abs(Math.floor(totalDuration / end)), 20);
    
    const timer = setInterval(() => {
      start += 1;
      setDisplayValue(start);
      if (start >= end) clearInterval(timer);
    }, incrementTime);
    
    return () => clearInterval(timer);
  }, [value]);

  return <span>{prefix}{displayValue}</span>;
}

export function Dashboard() {
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const { coupons, loadCoupons } = useCouponStore();
  const [editingCoupon, setEditingCoupon] = useState<any>(null);
  const [isFormOpen, setIsFormOpen] = useState(false);

  useEffect(() => {
    loadCoupons();
    getStats().then(setStats);
  }, [loadCoupons]);

  const recentCoupons = coupons.slice(0, 3); // Get 3 most recent

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <h1>Welcome Back</h1>
          <p style={{ color: 'var(--text-secondary)' }}>Here's your savings overview.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setIsFormOpen(true)}>
          <Ticket size={18} />
          Add Coupon
        </button>
      </div>

      <div style={{ 
        display: 'grid', 
        gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', 
        gap: 'var(--space-lg)',
        marginBottom: 'var(--space-2xl)'
      }}>
        <div className="glass-card stats-card">
          <div className="icon-circle" style={{ background: 'linear-gradient(135deg, rgba(139, 92, 246, 0.2), rgba(6, 182, 212, 0.2))', color: 'var(--accent-purple)' }}>
            <Ticket size={24} />
          </div>
          <div className="stats-card-content">
            <span className="stats-card-value"><AnimatedNumber value={stats?.totalCoupons || 0} /></span>
            <span className="stats-card-label">Total Coupons</span>
          </div>
        </div>

        <div className="glass-card stats-card">
          <div className="icon-circle" style={{ background: 'rgba(16, 185, 129, 0.1)', color: 'var(--accent-green)' }}>
            <CheckCircle size={24} />
          </div>
          <div className="stats-card-content">
            <span className="stats-card-value"><AnimatedNumber value={stats?.activeCoupons || 0} /></span>
            <span className="stats-card-label">Active Deals</span>
          </div>
        </div>

        <div className="glass-card stats-card">
          <div className="icon-circle" style={{ background: 'rgba(245, 158, 11, 0.1)', color: 'var(--accent-orange)' }}>
            <Clock size={24} />
          </div>
          <div className="stats-card-content">
            <span className="stats-card-value"><AnimatedNumber value={stats?.expiringSoon || 0} /></span>
            <span className="stats-card-label">Expiring Soon (7d)</span>
          </div>
        </div>

        <div className="glass-card stats-card">
          <div className="icon-circle" style={{ background: 'rgba(6, 182, 212, 0.1)', color: 'var(--accent-cyan)' }}>
            <DollarSign size={24} />
          </div>
          <div className="stats-card-content">
            <span className="stats-card-value"><AnimatedNumber value={stats?.totalSaved || 0} prefix="$" /></span>
            <span className="stats-card-label">Total Saved</span>
          </div>
        </div>

        <div className="glass-card stats-card">
          <div className="icon-circle" style={{ background: 'rgba(236, 72, 153, 0.1)', color: 'var(--accent-magenta)' }}>
            <ShoppingBag size={24} />
          </div>
          <div className="stats-card-content">
            <span className="stats-card-value"><AnimatedNumber value={stats?.usedThisMonth || 0} /></span>
            <span className="stats-card-label">Redeemed This Month</span>
          </div>
        </div>

        <div className="glass-card stats-card">
          <div className="icon-circle" style={{ background: 'rgba(59, 130, 246, 0.1)', color: '#3B82F6' }}>
            <CreditCard size={24} />
          </div>
          <div className="stats-card-content">
            <span className="stats-card-value"><AnimatedNumber value={stats?.totalMemberships || 0} /></span>
            <span className="stats-card-label">Active Memberships</span>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-md)' }}>
        <h2>Recently Added</h2>
      </div>

      <div className="grid-view">
        {recentCoupons.map(coupon => (
          <CouponCard 
            key={coupon.id} 
            coupon={coupon} 
            onEdit={(c) => { setEditingCoupon(c); setIsFormOpen(true); }}
          />
        ))}
        {recentCoupons.length === 0 && (
          <p style={{ color: 'var(--text-muted)' }}>No coupons added yet.</p>
        )}
      </div>

      {isFormOpen && (
        <CouponForm 
          coupon={editingCoupon} 
          onClose={() => { setIsFormOpen(false); setEditingCoupon(null); getStats().then(setStats); }} 
        />
      )}
    </div>
  );
}
