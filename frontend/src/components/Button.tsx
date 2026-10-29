import React, { forwardRef } from 'react';
import { Loader2 } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'link';
  size?: 'sm' | 'md' | 'lg';
  isLoading?: boolean;
  loadingText?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      variant = 'primary',
      size = 'md',
      isLoading = false,
      loadingText,
      leftIcon,
      rightIcon,
      disabled,
      className,
      children,
      ...props
    },
    ref
  ) => {
    const baseStyles =
      'inline-flex items-center justify-center font-medium rounded-md transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:opacity-50 disabled:pointer-events-none select-none';

    const sizeStyles = {
      sm: 'h-8 px-3 text-xs gap-1.5 min-h-[32px]',
      md: 'h-10 px-4 text-sm gap-2 min-h-[40px]',
      lg: 'h-12 px-6 text-base gap-2.5 min-h-[48px]',
    };

    const variantStyles = {
      primary:
        'bg-brand-primary text-text-inverse hover:bg-brand-primary-hover focus-visible:ring-brand-focus-ring',
      secondary:
        'bg-bg-subtle text-text-primary hover:bg-border-subtle focus-visible:ring-border',
      outline:
        'border border-border text-text-primary hover:bg-bg-subtle focus-visible:ring-brand-focus-ring',
      ghost:
        'text-text-primary hover:bg-bg-subtle focus-visible:ring-brand-focus-ring',
      danger:
        'bg-red-600 text-white hover:bg-red-700 focus-visible:ring-red-500',
      link:
        'text-brand-primary underline-offset-4 hover:underline focus-visible:ring-brand-focus-ring p-0 h-auto min-h-0',
    };

    const isDisabled = disabled || isLoading;

    return (
      <button
        ref={ref}
        disabled={isDisabled}
        aria-busy={isLoading}
        aria-disabled={isDisabled}
        className={twMerge(clsx(baseStyles, sizeStyles[size], variantStyles[variant], className))}
        {...props}
      >
        {isLoading ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />
            <span>{loadingText || children}</span>
          </>
        ) : (
          <>
            {leftIcon && <span aria-hidden="true">{leftIcon}</span>}
            {children}
            {rightIcon && <span aria-hidden="true">{rightIcon}</span>}
          </>
        )}
      </button>
    );
  }
);

Button.displayName = 'Button';
