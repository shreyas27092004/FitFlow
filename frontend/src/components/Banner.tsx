import React from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface BannerProps {
  type?: 'info' | 'warning' | 'danger' | 'success';
  title: string;
  children?: React.ReactNode;
  onDismiss?: () => void;
  className?: string;
}

export const Banner: React.FC<BannerProps> = ({
  type = 'info',
  title,
  children,
  onDismiss,
  className,
}) => {
  const typeConfig = {
    info: {
      container: 'bg-sky-50 border-sky-300 text-sky-950 dark:bg-sky-950/60 dark:border-sky-800 dark:text-sky-200',
      icon: <Info className="w-5 h-5 text-sky-600 dark:text-sky-400 shrink-0" aria-hidden="true" />,
    },
    warning: {
      container: 'bg-amber-50 border-amber-300 text-amber-950 dark:bg-amber-950/60 dark:border-amber-800 dark:text-amber-200',
      icon: <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0" aria-hidden="true" />,
    },
    danger: {
      container: 'bg-red-50 border-red-300 text-red-950 dark:bg-red-950/60 dark:border-red-800 dark:text-red-200',
      icon: <AlertCircle className="w-5 h-5 text-red-600 dark:text-red-400 shrink-0" aria-hidden="true" />,
    },
    success: {
      container: 'bg-green-50 border-green-300 text-green-950 dark:bg-green-950/60 dark:border-green-800 dark:text-green-200',
      icon: <CheckCircle2 className="w-5 h-5 text-green-600 dark:text-green-400 shrink-0" aria-hidden="true" />,
    },
  };

  const { container, icon } = typeConfig[type];

  return (
    <div
      role="region"
      aria-label={`${type} notification banner`}
      className={twMerge(
        clsx(
          'w-full flex items-start gap-3 p-4 rounded-lg border text-sm',
          container,
          className
        )
      )}
    >
      <div className="mt-0.5">{icon}</div>

      <div className="flex-1">
        <h4 className="font-semibold">{title}</h4>
        {children && <div className="mt-1 text-xs opacity-90">{children}</div>}
      </div>

      {onDismiss && (
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Dismiss banner"
          className="shrink-0 p-1 opacity-70 hover:opacity-100 rounded focus:outline-none focus:ring-1 focus:ring-brand-focus-ring"
        >
          <X className="w-4 h-4" aria-hidden="true" />
        </button>
      )}
    </div>
  );
};
