import React, { useEffect } from 'react';
import { CreditCard } from 'lucide-react';
import { useMembershipStore } from '../stores/membershipStore';
import { EmptyState } from '../components/EmptyState';

export function Memberships() {
  const { memberships, loadMemberships, isLoading } = useMembershipStore();

  useEffect(() => {
    loadMemberships();
  }, [loadMemberships]);

  const totalMonthlySpend = memberships
    .filter(m => m.isActive)
    .reduce((sum, m) => {
      if (m.feePeriod === 'monthly') return sum + m.fee;
      if (m.feePeriod === 'annual') return sum + (m.fee / 12);
      if (m.feePeriod === 'quarterly') return sum + (m.fee / 3);
      return sum;
    }, 0);

  return (
    <div className="fade-in">
      <div className="page-header">
        <h1>Memberships</h1>
        <button className="btn btn-primary">
          <CreditCard size={18} />
          Add Membership
        </button>
      </div>

      <div className="glass-card" style={{ marginBottom: 'var(--space-xl)', padding: 'var(--space-lg)' }}>
        <h3 style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: 'var(--space-xs)' }}>Total Monthly Spend</h3>
        <div style={{ fontSize: '2rem', fontWeight: 700, color: 'var(--accent-cyan)' }}>
          ${totalMonthlySpend.toFixed(2)}
        </div>
      </div>

      {isLoading ? (
        <div className="grid-view">
          {[1, 2].map(i => <div key={i} className="glass-card skeleton" style={{ height: '150px' }} />)}
        </div>
      ) : memberships.length > 0 ? (
        <div className="grid-view">
          {memberships.map(m => (
            <div key={m.id} className="glass-card" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <h3 style={{ margin: 0 }}>{m.organization}</h3>
                  <span style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>{m.name}</span>
                </div>
                <div style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>
                  ${m.fee}/{m.feePeriod.substring(0, 2)}
                </div>
              </div>
              
              <div style={{ display: 'flex', gap: 'var(--space-sm)' }}>
                <span className="badge" style={{ background: m.isActive ? 'rgba(16, 185, 129, 0.1)' : 'rgba(255, 255, 255, 0.1)', color: m.isActive ? 'var(--accent-green)' : 'var(--text-muted)' }}>
                  {m.isActive ? 'Active' : 'Inactive'}
                </span>
                <span className="chip" style={{ fontSize: '0.75rem', padding: '0.125rem 0.5rem' }}>
                  Renews: {new Date(m.renewalDate).toLocaleDateString()}
                </span>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState 
          icon={CreditCard}
          title="No memberships tracked"
          description="Keep track of your gym memberships, subscriptions, and loyalty programs."
        />
      )}
    </div>
  );
}
