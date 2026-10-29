import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'elevated' | 'outlined' | 'subtle';
  padding?: 'none' | 'sm' | 'md' | 'lg';
  isInteractive?: boolean;
}

export const Card: React.FC<CardProps> = ({
  variant = 'elevated',
  padding = 'md',
  isInteractive = false,
  className,
  children,
  onClick,
  ...props
}) => {
  const paddingStyles = {
    none: 'p-0',
    sm: 'p-3',
    md: 'p-5',
    lg: 'p-6',
  };

  const variantStyles = {
    elevated: 'bg-bg-surface border border-border shadow-sm',
    outlined: 'bg-bg-page border border-border',
    subtle: 'bg-bg-subtle border border-transparent',
  };

  return (
    <div
      role={isInteractive ? 'button' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
      onClick={onClick}
      onKeyDown={
        isInteractive
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick?.(e as any);
              }
            }
          : undefined
      }
      className={twMerge(
        clsx(
          'rounded-lg transition-all text-text-primary',
          variantStyles[variant],
          paddingStyles[padding],
          isInteractive && 'cursor-pointer hover:border-brand-primary focus:outline-none focus:ring-2 focus:ring-brand-focus-ring',
          className
        )
      )}
      {...props}
    >
      {children}
    </div>
  );
};
