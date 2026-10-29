import React, { createContext, useContext, useState, useCallback } from 'react';
import * as RadixToast from '@radix-ui/react-toast';
import { CheckCircle2, AlertTriangle, AlertOctagon, Info, X } from 'lucide-react';
import { clsx } from 'clsx';

export type ToastType = 'success' | 'warning' | 'error' | 'info';

export interface ToastMessage {
  id: string;
  type: ToastType;
  title: string;
  description?: string;
  traceId?: string;
  durationMs?: number;
}

interface ToastContextType {
  showToast: (toast: Omit<ToastMessage, 'id'>) => void;
  showError: (title: string, description?: string, traceId?: string) => void;
  showSuccess: (title: string, description?: string) => void;
}

const ToastContext = createContext<ToastContextType | null>(null);

export const useToast = () => {
  const context = useContext(ToastContext);
  if (!context) {
    return {
      showToast: () => {},
      showError: () => {},
      showSuccess: () => {},
    };
  }
  return context;
};

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const showToast = useCallback((toast: Omit<ToastMessage, 'id'>) => {
    const id = Math.random().toString(36).substring(2, 9);
    setToasts((prev) => [...prev, { ...toast, id }]);
  }, []);

  const showError = useCallback(
    (title: string, description?: string, traceId?: string) => {
      showToast({ type: 'error', title, description, traceId });
    },
    [showToast]
  );

  const showSuccess = useCallback(
    (title: string, description?: string) => {
      showToast({ type: 'success', title, description });
    },
    [showToast]
  );

  const removeToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  return (
    <ToastContext.Provider value={{ showToast, showError, showSuccess }}>
      <RadixToast.Provider swipeDirection="right">
        {children}

        {toasts.map((toast) => (
          <RadixToast.Root
            key={toast.id}
            asChild
            duration={toast.durationMs || 5000}
            onOpenChange={(open) => !open && removeToast(toast.id)}
          >
            <div
              className={clsx(
                'z-toast flex w-full max-w-sm items-start gap-3 rounded-lg border p-4 shadow-lg transition-all animate-in slide-in-from-top-full',
                toast.type === 'error' && 'bg-red-50 border-red-200 text-red-900 dark:bg-red-950 dark:border-red-900 dark:text-red-200',
                toast.type === 'success' && 'bg-green-50 border-green-200 text-green-900 dark:bg-green-950 dark:border-green-900 dark:text-green-200',
                toast.type === 'warning' && 'bg-amber-50 border-amber-200 text-amber-900 dark:bg-amber-950 dark:border-amber-900 dark:text-amber-200',
                toast.type === 'info' && 'bg-sky-50 border-sky-200 text-sky-900 dark:bg-sky-950 dark:border-sky-900 dark:text-sky-200'
              )}
            >
              <div className="mt-0.5 shrink-0" aria-hidden="true">
                {toast.type === 'error' && <AlertOctagon className="w-5 h-5 text-red-600 dark:text-red-400" />}
                {toast.type === 'success' && <CheckCircle2 className="w-5 h-5 text-green-600 dark:text-green-400" />}
                {toast.type === 'warning' && <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400" />}
                {toast.type === 'info' && <Info className="w-5 h-5 text-sky-600 dark:text-sky-400" />}
              </div>

              <div className="flex-1">
                <RadixToast.Title className="text-sm font-semibold">{toast.title}</RadixToast.Title>
                {toast.description && (
                  <RadixToast.Description className="mt-1 text-xs opacity-90">
                    {toast.description}
                  </RadixToast.Description>
                )}
                {toast.traceId && (
                  <p className="mt-1 font-mono text-[11px] text-text-muted">
                    Reference: {toast.traceId}
                  </p>
                )}
              </div>

              <RadixToast.Close asChild>
                <button
                  type="button"
                  aria-label="Close notification"
                  className="shrink-0 p-1 opacity-70 hover:opacity-100 rounded focus:outline-none focus:ring-1 focus:ring-brand-focus-ring"
                >
                  <X className="w-4 h-4" aria-hidden="true" />
                </button>
              </RadixToast.Close>
            </div>
          </RadixToast.Root>
        ))}

        <RadixToast.Viewport
          asChild
          className="fixed top-4 right-4 z-toast flex max-h-screen w-full max-w-[420px] flex-col-reverse gap-2 p-4 outline-none"
        >
          <div />
        </RadixToast.Viewport>
      </RadixToast.Provider>
    </ToastContext.Provider>
  );
};
