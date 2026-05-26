import React from 'react';
import { create } from 'zustand';
import { CheckCircle, XCircle, AlertTriangle, Info, X } from 'lucide-react';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

interface Toast {
  id: string;
  message: string;
  type: ToastType;
}

interface ToastStore {
  toasts: Toast[];
  addToast: (message: string, type: ToastType) => void;
  removeToast: (id: string) => void;
}

const useToastStore = create<ToastStore>((set) => ({
  toasts: [],
  addToast: (message, type) => {
    const id = crypto.randomUUID();
    set((state) => ({ toasts: [...state.toasts, { id, message, type }] }));
    setTimeout(() => {
      set((state) => ({ toasts: state.toasts.filter(t => t.id !== id) }));
    }, 3000);
  },
  removeToast: (id) => set((state) => ({ toasts: state.toasts.filter(t => t.id !== id) }))
}));

export function useToast() {
  return useToastStore((state) => state.addToast);
}

export function ToastContainer() {
  const { toasts, removeToast } = useToastStore();

  if (toasts.length === 0) return null;

  return (
    <div className="toast-container">
      {toasts.map(toast => (
        <div key={toast.id} className={`toast toast-${toast.type} slide-in`}>
          {toast.type === 'success' && <CheckCircle size={20} className="text-accent-green" style={{ color: 'var(--accent-green)' }} />}
          {toast.type === 'error' && <XCircle size={20} className="text-accent-red" style={{ color: 'var(--accent-red)' }} />}
          {toast.type === 'warning' && <AlertTriangle size={20} className="text-accent-orange" style={{ color: 'var(--accent-orange)' }} />}
          {toast.type === 'info' && <Info size={20} className="text-accent-cyan" style={{ color: 'var(--accent-cyan)' }} />}
          
          <span style={{ flex: 1, fontSize: '0.875rem' }}>{toast.message}</span>
          
          <button onClick={() => removeToast(toast.id)} className="btn-icon" aria-label="Close toast">
            <X size={16} />
          </button>
        </div>
      ))}
    </div>
  );
}
