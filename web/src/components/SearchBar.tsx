import React, { useState, useEffect } from 'react';
import { Search, Filter, LayoutGrid, List } from 'lucide-react';
import { useCouponStore } from '../stores/couponStore';
import { useDebounce } from '../hooks/useDebounce';
import type { SortOption } from '../types';

const CATEGORIES = ['All', 'Shopping', 'Food & Drink', 'Electronics', 'Fashion', 'Travel', 'Entertainment', 'Health', 'Services'];

export function SearchBar() {
  const { searchQuery, setSearchQuery, activeFilters, setFilter, sortOption, setSortOption, viewMode, setViewMode } = useCouponStore();
  const [localSearch, setLocalSearch] = useState(searchQuery);
  const debouncedSearch = useDebounce(localSearch, 300);

  useEffect(() => {
    setSearchQuery(debouncedSearch);
  }, [debouncedSearch, setSearchQuery]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-md)', marginBottom: 'var(--space-xl)' }}>
      <div style={{ display: 'flex', gap: 'var(--space-md)', flexWrap: 'wrap' }}>
        <div className="search-bar" style={{ flex: '1 1 300px' }}>
          <Search size={20} />
          <input 
            type="text" 
            className="input-field" 
            placeholder="Search merchants, codes, or descriptions..." 
            value={localSearch}
            onChange={(e) => setLocalSearch(e.target.value)}
          />
        </div>
        
        <div style={{ display: 'flex', gap: 'var(--space-sm)' }}>
          <select 
            className="select-field" 
            value={sortOption} 
            onChange={(e) => setSortOption(e.target.value as SortOption)}
            style={{ width: 'auto' }}
          >
            <option value="date_added">Date Added</option>
            <option value="expiration">Expiration Date</option>
            <option value="discount">Discount Amount</option>
            <option value="alphabetical">Alphabetical</option>
          </select>

          <div style={{ display: 'flex', background: 'var(--bg-secondary)', borderRadius: 'var(--radius-md)', padding: '2px' }}>
            <button 
              className={`btn-icon ${viewMode === 'grid' ? 'active' : ''}`}
              onClick={() => setViewMode('grid')}
              style={{ background: viewMode === 'grid' ? 'var(--bg-card)' : 'transparent', color: viewMode === 'grid' ? 'var(--accent-purple)' : 'var(--text-muted)' }}
            >
              <LayoutGrid size={18} />
            </button>
            <button 
              className={`btn-icon ${viewMode === 'list' ? 'active' : ''}`}
              onClick={() => setViewMode('list')}
              style={{ background: viewMode === 'list' ? 'var(--bg-card)' : 'transparent', color: viewMode === 'list' ? 'var(--accent-purple)' : 'var(--text-muted)' }}
            >
              <List size={18} />
            </button>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 'var(--space-sm)', overflowX: 'auto', paddingBottom: 'var(--space-xs)', WebkitOverflowScrolling: 'touch' }}>
        {CATEGORIES.map(category => (
          <button 
            key={category}
            className={`chip ${activeFilters.category === category || (category === 'All' && !activeFilters.category) ? 'chip-active' : ''}`}
            onClick={() => setFilter('category', category === 'All' ? null : category)}
          >
            {category}
          </button>
        ))}
      </div>
    </div>
  );
}
