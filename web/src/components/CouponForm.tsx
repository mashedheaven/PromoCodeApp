import React, { useRef, useEffect, useState } from 'react';
import { X, MapPin } from 'lucide-react';
import type { Coupon, DiscountType, Location } from '../types';
import { useCouponStore } from '../stores/couponStore';
import { useToast } from './Toast';

interface CouponFormProps {
  coupon?: Coupon;
  onClose: () => void;
}

const CATEGORIES = ['Shopping', 'Food & Drink', 'Electronics', 'Fashion', 'Travel', 'Entertainment', 'Health', 'Services', 'Other'];

export function CouponForm({ coupon, onClose }: CouponFormProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const { addCoupon, updateCoupon } = useCouponStore();
  const showToast = useToast();

  const [formData, setFormData] = useState({
    merchant: coupon?.merchant || '',
    code: coupon?.code || '',
    description: coupon?.description || '',
    discountType: (coupon?.discountType || 'percentage') as DiscountType,
    discountValue: coupon?.discountValue || '',
    expirationDate: coupon?.expirationDate ? new Date(coupon.expirationDate).toISOString().split('T')[0] : '',
    category: coupon?.category || 'Shopping',
    notes: coupon?.notes || '',
  });

  const [location, setLocation] = useState<Location | undefined>(coupon?.location);
  const [isLocating, setIsLocating] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) {
      dialog.showModal();
    }
    
    const handleCancel = (e: Event) => {
      e.preventDefault();
      onClose();
    };
    
    dialog?.addEventListener('cancel', handleCancel);
    return () => dialog?.removeEventListener('cancel', handleCancel);
  }, [onClose]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleGetCurrentLocation = () => {
    setIsLocating(true);
    if (!navigator.geolocation) {
      showToast('Geolocation is not supported', 'error');
      setIsLocating(false);
      return;
    }
    
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocation({
          id: crypto.randomUUID(),
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          address: 'Current Location',
          radius: 500 // default 500m
        });
        showToast('Location attached successfully', 'success');
        setIsLocating(false);
      },
      (error) => {
        showToast('Failed to get location. Ensure permissions are granted.', 'error');
        setIsLocating(false);
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    const submitData: any = {
      ...formData,
      discountValue: formData.discountValue ? Number(formData.discountValue) : 0,
      expirationDate: new Date(formData.expirationDate).toISOString(),
      location
    };

    if (coupon) {
      await updateCoupon(coupon.id, submitData);
      showToast('Coupon updated', 'success');
    } else {
      await addCoupon({
        ...submitData,
        isFavorite: false,
        isUsed: false
      });
      showToast('Coupon added', 'success');
    }
    
    dialogRef.current?.close();
    onClose();
  };

  return (
    <dialog ref={dialogRef} className="slide-up">
      <div className="modal-content" style={{ maxHeight: '90vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-xl)' }}>
          <h2 style={{ margin: 0 }}>{coupon ? 'Edit Coupon' : 'Add Coupon'}</h2>
          <button type="button" className="btn-icon" onClick={onClose}>
            <X size={24} />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="merchant">Merchant *</label>
              <input 
                required 
                type="text" 
                id="merchant" 
                name="merchant"
                className="input-field" 
                value={formData.merchant}
                onChange={handleChange}
                placeholder="e.g. Amazon"
              />
            </div>
            
            <div className="form-group">
              <label className="form-label" htmlFor="code">Promo Code *</label>
              <input 
                required 
                type="text" 
                id="code" 
                name="code"
                className="input-field" 
                value={formData.code}
                onChange={handleChange}
                style={{ fontFamily: 'monospace', textTransform: 'uppercase' }}
                placeholder="e.g. SAVE20"
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="description">Description *</label>
            <input 
              required
              type="text"
              id="description"
              name="description"
              className="input-field"
              value={formData.description}
              onChange={handleChange}
              placeholder="e.g. 20% off all electronics"
            />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="discountType">Discount Type</label>
              <select 
                id="discountType" 
                name="discountType"
                className="select-field"
                value={formData.discountType}
                onChange={handleChange}
              >
                <option value="percentage">Percentage (%)</option>
                <option value="fixed">Fixed Amount ($)</option>
                <option value="bogo">Buy One Get One</option>
                <option value="free_shipping">Free Shipping</option>
              </select>
            </div>
            
            <div className="form-group">
              <label className="form-label" htmlFor="discountValue">Value {formData.discountType === 'percentage' ? '(%)' : formData.discountType === 'fixed' ? '($)' : ''}</label>
              <input 
                type="number" 
                id="discountValue" 
                name="discountValue"
                className="input-field" 
                value={formData.discountValue}
                onChange={handleChange}
                min="0"
                step="0.01"
                disabled={['bogo', 'free_shipping'].includes(formData.discountType)}
              />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-md)' }}>
            <div className="form-group">
              <label className="form-label" htmlFor="expirationDate">Expiration Date *</label>
              <input 
                required 
                type="date" 
                id="expirationDate" 
                name="expirationDate"
                className="input-field" 
                value={formData.expirationDate}
                onChange={handleChange}
              />
            </div>
            
            <div className="form-group">
              <label className="form-label" htmlFor="category">Category</label>
              <select 
                id="category" 
                name="category"
                className="select-field"
                value={formData.category}
                onChange={handleChange}
              >
                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
              </select>
            </div>
          </div>
          
          <div className="form-group">
            <label className="form-label">Store Location</label>
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-sm)' }}>
              <button 
                type="button" 
                className="btn btn-secondary" 
                onClick={handleGetCurrentLocation}
                disabled={isLocating}
                style={{ width: '100%' }}
              >
                <MapPin size={18} />
                {isLocating ? 'Locating...' : (location ? 'Update Location' : 'Use My Current Location')}
              </button>
            </div>
            {location && (
              <span style={{ fontSize: '0.75rem', color: 'var(--accent-green)', marginTop: 'var(--space-xs)' }}>
                ✓ Location attached ({location.latitude.toFixed(4)}, {location.longitude.toFixed(4)})
              </span>
            )}
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="notes">Notes (Optional)</label>
            <textarea 
              id="notes" 
              name="notes"
              className="input-field" 
              value={formData.notes}
              onChange={handleChange}
              rows={2}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-md)', marginTop: 'var(--space-md)' }}>
            <button type="button" className="btn btn-secondary" onClick={() => { dialogRef.current?.close(); onClose(); }}>
              Cancel
            </button>
            <button type="submit" className="btn btn-primary">
              {coupon ? 'Save Changes' : 'Add Coupon'}
            </button>
          </div>
        </form>
      </div>
    </dialog>
  );
}
