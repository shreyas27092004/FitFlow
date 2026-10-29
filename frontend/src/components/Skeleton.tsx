import React from 'react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'text' | 'avatar' | 'card' | 'button' | 'row';
}

export const Skeleton: React.FC<SkeletonProps> = ({
  variant = 'text',
  className,
  ...props
}) => {
  const variantStyles = {
    text: 'h-4 w-full rounded',
    avatar: 'h-10 w-10 rounded-full',
    card: 'h-40 w-full rounded-lg',
    button: 'h-10 w-24 rounded-md',
    row: 'h-12 w-full rounded',
  };

  return (
    <div
      aria-hidden="true"
      className={twMerge(
        clsx(
          'animate-pulse bg-border/60',
          variantStyles[variant],
          className
        )
      )}
      {...props}
    />
  );
};
