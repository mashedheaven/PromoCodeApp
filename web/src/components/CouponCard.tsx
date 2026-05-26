import React, { useState } from 'react';
import { Heart, Copy, Check, Edit2, Trash2, CheckCircle2 } from 'lucide-react';
import type { Coupon } from '../types';
import { useCouponStore } from '../stores/couponStore';
import { useToast } from './Toast';

interface CouponCardProps {
  coupon: Coupon;
  onEdit: (coupon: Coupon) => void;
}

export function CouponCard({ coupon, onEdit }: CouponCardProps) {
  const [copied, setCopied] = useState(false);
  const { toggleFavorite, markUsed, deleteCoupon } = useCouponStore();
  const showToast = useToast();

  const handleCopy = () => {
    navigator.clipboard.writeText(coupon.code);
    setCopied(true);
    showToast('Code copied to clipboard!', 'success');
    setTimeout(() => setCopied(false), 2000);
  };

  const getBadgeClass = () => {
    switch (coupon.discountType) {
      case 'percentage': return 'badge-percentage';
      case 'fixed': return 'badge-fixed';
      case 'bogo': return 'badge-bogo';
      case 'free_shipping': return 'badge-shipping';
      default: return '';
    }
  };

  const getDiscountText = () => {
    switch (coupon.discountType) {
      case 'percentage': return `${coupon.discountValue}% OFF`;
      case 'fixed': return `$${coupon.discountValue} OFF`;
      case 'bogo': return 'BOGO';
      case 'free_shipping': return 'FREE SHIPPING';
      default: return '';
    }
  };

  const getExpirationStatus = () => {
    const now = new Date();
    const exp = new Date(coupon.expirationDate);
    const diffTime = Math.abs(exp.getTime() - now.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    
    if (exp < now) return { color: 'var(--text-muted)', text: 'Expired' };
    if (diffDays <= 1) return { color: 'var(--accent-red)', text: 'Expires Today' };
    if (diffDays <= 7) return { color: 'var(--accent-orange)', text: `Expires in ${diffDays} days` };
    return { color: 'var(--accent-green)', text: `Expires in ${diffDays} days` };
  };

  const expStatus = getExpirationStatus();

  return (
    <div className={`glass-card fade-in ${coupon.isUsed ? 'opacity-70' : ''}`} style={{ 
      borderLeft: `4px solid ${
        coupon.discountType === 'percentage' ? 'var(--accent-green)' : 
        coupon.discountType === 'fixed' ? 'var(--accent-cyan)' : 
        coupon.discountType === 'bogo' ? 'var(--accent-orange)' : 'var(--accent-purple)'
      }`,
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-md)'
    }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '1.25rem' }}>{coupon.merchant}</h3>
          <span className={`badge ${getBadgeClass()}`} style={{ marginTop: 'var(--space-xs)', display: 'inline-block' }}>
            {getDiscountText()}
          </span>
        </div>
        <button 
          className="btn-icon" 
          onClick={() => toggleFavorite(coupon.id)}
          style={{ color: coupon.isFavorite ? 'var(--accent-magenta)' : 'var(--text-secondary)' }}
        >
          <Heart fill={coupon.isFavorite ? 'currentColor' : 'none'} size={20} />
        </button>
      </div>

      <div 
        style={{ 
          background: 'var(--bg-secondary)', 
          padding: 'var(--space-md)', 
          borderRadius: 'var(--radius-md)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          border: '1px dashed var(--border-subtle)',
          cursor: 'pointer'
        }}
        onClick={handleCopy}
      >
        <span style={{ fontFamily: 'monospace', fontSize: '1.125rem', fontWeight: 600, letterSpacing: '1px' }}>
          {coupon.code}
        </span>
        {copied ? <Check size={18} className="text-accent-green" style={{ color: 'var(--accent-green)' }} /> : <Copy size={18} color="var(--text-muted)" />}
      </div>

      <div>
        <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', margin: 0 }}>
          {coupon.description}
        </p>
        <div style={{ fontSize: '0.75rem', color: expStatus.color, marginTop: 'var(--space-sm)', fontWeight: 500 }}>
          {expStatus.text}
        </div>
      </div>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'auto', paddingTop: 'var(--space-md)', borderTop: '1px solid var(--border-subtle)' }}>
        <span className="chip" style={{ fontSize: '0.75rem', padding: '0.25rem 0.5rem' }}>
          {coupon.category}
        </span>
        
        <div style={{ display: 'flex', gap: 'var(--space-xs)' }}>
          <button 
            className={`btn-icon ${coupon.isUsed ? 'active' : ''}`}
            onClick={() => markUsed(coupon.id)}
            title="Mark as used"
            style={{ color: coupon.isUsed ? 'var(--accent-green)' : 'var(--text-muted)' }}
          >
            <CheckCircle2 size={18} />
          </button>
          <button className="btn-icon" onClick={() => onEdit(coupon)} title="Edit">
            <Edit2 size={18} />
          </button>
          <button 
            className="btn-icon" 
            onClick={() => {
              if(confirm('Are you sure you want to delete this coupon?')) {
                deleteCoupon(coupon.id);
                showToast('Coupon deleted', 'info');
              }
            }} 
            title="Delete"
            style={{ color: 'var(--text-muted)' }}
          >
            <Trash2 size={18} />
          </button>
        </div>
      </div>
    </div>
  );
}
