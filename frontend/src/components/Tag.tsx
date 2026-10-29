import React from 'react';
import { X } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface TagProps {
  label: string;
  onRemove?: () => void;
  onClick?: () => void;
  selected?: boolean;
  className?: string;
}

export const Tag: React.FC<TagProps> = ({
  label,
  onRemove,
  onClick,
  selected = false,
  className,
}) => {
  const isClickable = Boolean(onClick);

  return (
    <span
      onClick={onClick}
      role={isClickable ? 'button' : undefined}
      tabIndex={isClickable ? 0 : undefined}
      onKeyDown={
        isClickable
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick?.();
              }
            }
          : undefined
      }
      className={twMerge(
        clsx(
          'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium transition-colors select-none',
          selected
            ? 'bg-brand-primary text-text-inverse'
            : 'bg-bg-subtle text-text-secondary hover:bg-border-subtle',
          isClickable && 'cursor-pointer focus:outline-none focus:ring-2 focus:ring-brand-focus-ring',
          className
        )
      )}
    >
      <span>{label}</span>
      {onRemove && (
        <button
          type="button"
          onClick={(e) => {
            e.stopPropagation();
            onRemove();
          }}
          aria-label={`Remove ${label}`}
          className="rounded-full p-0.5 hover:bg-black/10 focus:outline-none focus:ring-1 focus:ring-brand-focus-ring"
        >
          <X className="w-3 h-3" aria-hidden="true" />
        </button>
      )}
    </span>
  );
};
