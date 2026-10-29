import React from 'react';
import { CheckCircle2, AlertTriangle, AlertOctagon, Info, ShieldAlert } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export type BadgeStatus =
  | 'ALLOWED'
  | 'CAUTION'
  | 'RESTRICTED'
  | 'RED_FLAG'
  | 'ACTIVE'
  | 'PENDING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'CONFIRMED'
  | 'WAITLISTED';

export interface BadgeProps {
  status?: BadgeStatus;
  variant?: 'success' | 'warning' | 'danger' | 'info' | 'neutral';
  children?: React.ReactNode;
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({
  status,
  variant,
  children,
  className,
}) => {
  let effectiveVariant: 'success' | 'warning' | 'danger' | 'info' | 'neutral' = variant || 'neutral';
  let defaultIcon: React.ReactNode = null;
  let text = children;

  if (status) {
    switch (status) {
      case 'ALLOWED':
      case 'ACTIVE':
      case 'SUCCEEDED':
      case 'CONFIRMED':
        effectiveVariant = 'success';
        defaultIcon = <CheckCircle2 className="w-3.5 h-3.5" aria-hidden="true" />;
        text = text || status;
        break;
      case 'CAUTION':
      case 'PENDING':
      case 'WAITLISTED':
        effectiveVariant = 'warning';
        defaultIcon = <AlertTriangle className="w-3.5 h-3.5" aria-hidden="true" />;
        text = text || status;
        break;
      case 'RESTRICTED':
      case 'RED_FLAG':
      case 'FAILED':
      case 'CANCELLED':
        effectiveVariant = 'danger';
        defaultIcon = status === 'RED_FLAG' ? (
          <ShieldAlert className="w-3.5 h-3.5" aria-hidden="true" />
        ) : (
          <AlertOctagon className="w-3.5 h-3.5" aria-hidden="true" />
        );
        text = text || status;
        break;
      default:
        effectiveVariant = 'info';
        defaultIcon = <Info className="w-3.5 h-3.5" aria-hidden="true" />;
        text = text || status;
    }
  }

  const variantStyles = {
    success: 'bg-green-100 text-green-900 border-green-300 dark:bg-green-900/40 dark:text-green-300 dark:border-green-800',
    warning: 'bg-amber-100 text-amber-900 border-amber-300 dark:bg-amber-900/40 dark:text-amber-300 dark:border-amber-800',
    danger: 'bg-red-100 text-red-900 border-red-300 dark:bg-red-900/40 dark:text-red-300 dark:border-red-800',
    info: 'bg-sky-100 text-sky-900 border-sky-300 dark:bg-sky-900/40 dark:text-sky-300 dark:border-sky-800',
    neutral: 'bg-slate-100 text-slate-800 border-slate-300 dark:bg-slate-800 dark:text-slate-200 dark:border-slate-700',
  };

  return (
    <span
      className={twMerge(
        clsx(
          'inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-medium border select-none',
          variantStyles[effectiveVariant],
          className
        )
      )}
    >
      {defaultIcon}
      <span>{text}</span>
    </span>
  );
};
