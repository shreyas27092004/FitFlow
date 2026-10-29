import React from 'react';
import { Card } from './Card';
import { ArrowUpRight, ArrowDownRight } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface StatCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  trendLabel?: string;
  trendDirection?: 'up' | 'down' | 'neutral';
  icon?: React.ReactNode;
  className?: string;
}

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  subtitle,
  trendLabel,
  trendDirection,
  icon,
  className,
}) => {
  return (
    <Card className={twMerge(clsx('flex flex-col justify-between', className))}>
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-medium uppercase tracking-wider text-text-muted">
          {title}
        </span>
        {icon && (
          <div className="p-2 rounded-md bg-bg-subtle text-text-muted shrink-0" aria-hidden="true">
            {icon}
          </div>
        )}
      </div>

      <div>
        <div className="text-2xl font-bold text-text-primary tracking-tight">{value}</div>

        {(subtitle || trendLabel) && (
          <div className="mt-1 flex items-center gap-1.5 text-xs">
            {trendLabel && (
              <span
                className={clsx(
                  'flex items-center font-medium',
                  trendDirection === 'up' && 'text-green-600 dark:text-green-400',
                  trendDirection === 'down' && 'text-red-600 dark:text-red-400',
                  trendDirection === 'neutral' && 'text-text-muted'
                )}
              >
                {trendDirection === 'up' && (
                  <ArrowUpRight className="w-3.5 h-3.5 mr-0.5" aria-hidden="true" />
                )}
                {trendDirection === 'down' && (
                  <ArrowDownRight className="w-3.5 h-3.5 mr-0.5" aria-hidden="true" />
                )}
                {trendLabel}
              </span>
            )}
            {subtitle && <span className="text-text-muted">{subtitle}</span>}
          </div>
        )}
      </div>
    </Card>
  );
};
