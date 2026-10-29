import React from 'react';
import { Sparkles } from 'lucide-react';
import { Button } from './Button';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface EmptyStateProps {
  title: string;
  description: string;
  icon?: React.ReactNode;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title,
  description,
  icon,
  actionLabel,
  onAction,
  className,
}) => {
  return (
    <div
      className={twMerge(
        clsx(
          'flex flex-col items-center justify-center text-center p-8 rounded-xl border border-dashed border-border bg-bg-surface/50',
          className
        )
      )}
    >
      <div className="w-12 h-12 rounded-full bg-bg-subtle flex items-center justify-center text-text-muted mb-4">
        {icon || <Sparkles className="w-6 h-6" aria-hidden="true" />}
      </div>

      <h3 className="text-base font-semibold text-text-primary mb-1">{title}</h3>
      <p className="text-sm text-text-secondary max-w-sm mb-6">{description}</p>

      {actionLabel && onAction && (
        <Button type="button" variant="primary" size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  );
};
