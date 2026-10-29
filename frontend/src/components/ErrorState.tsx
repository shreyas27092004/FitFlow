import React from 'react';
import { AlertOctagon, RotateCcw } from 'lucide-react';
import { Button } from './Button';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface ErrorStateProps {
  title?: string;
  errorMessage: string;
  traceId?: string;
  onRetry?: () => void;
  className?: string;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'Something went wrong',
  errorMessage,
  traceId,
  onRetry,
  className,
}) => {
  return (
    <div
      role="alert"
      className={twMerge(
        clsx(
          'flex flex-col items-center justify-center text-center p-8 rounded-xl border border-red-200 bg-red-50/50 dark:border-red-900/50 dark:bg-red-950/20',
          className
        )
      )}
    >
      <div className="w-12 h-12 rounded-full bg-red-100 dark:bg-red-900/50 flex items-center justify-center text-red-600 dark:text-red-400 mb-4">
        <AlertOctagon className="w-6 h-6" aria-hidden="true" />
      </div>

      <h3 className="text-base font-semibold text-text-primary mb-1">{title}</h3>
      <p className="text-sm text-text-secondary max-w-sm mb-2">{errorMessage}</p>

      {traceId && (
        <p className="text-xs font-mono text-text-muted mb-6">
          Reference: {traceId}
        </p>
      )}

      {onRetry && (
        <Button
          type="button"
          variant="outline"
          size="sm"
          leftIcon={<RotateCcw className="w-4 h-4" />}
          onClick={onRetry}
        >
          Try Again
        </Button>
      )}
    </div>
  );
};
