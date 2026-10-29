import React, { useId } from 'react';
import * as RadixCheckbox from '@radix-ui/react-checkbox';
import { Check } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface CheckboxProps {
  id?: string;
  label: React.ReactNode;
  description?: string;
  checked?: boolean | 'indeterminate';
  onCheckedChange?: (checked: boolean | 'indeterminate') => void;
  disabled?: boolean;
  required?: boolean;
  className?: string;
}

export const Checkbox: React.FC<CheckboxProps> = ({
  id: customId,
  label,
  description,
  checked,
  onCheckedChange,
  disabled,
  required,
  className,
}) => {
  const defaultId = useId();
  const id = customId || defaultId;
  const descId = `${id}-desc`;

  return (
    <div className="flex items-start gap-3">
      <RadixCheckbox.Root
        id={id}
        aria-label={typeof label === 'string' ? label : 'Checkbox'}
        checked={checked}
        onCheckedChange={onCheckedChange}
        disabled={disabled}
        required={required}
        aria-describedby={description ? descId : undefined}
        className={twMerge(
          clsx(
            'peer h-5 w-5 shrink-0 rounded border border-border transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-focus-ring focus-visible:ring-offset-2',
            'disabled:cursor-not-allowed disabled:opacity-50',
            'data-[state=checked]:bg-brand-primary data-[state=checked]:text-text-inverse data-[state=checked]:border-brand-primary',
            className
          )
        )}
      >
        <RadixCheckbox.Indicator className="flex items-center justify-center text-current">
          <Check className="h-3.5 w-3.5 stroke-[3]" aria-hidden="true" />
        </RadixCheckbox.Indicator>
      </RadixCheckbox.Root>

      <div className="flex flex-col text-sm">
        <label
          htmlFor={id}
          className={clsx(
            'font-medium text-text-primary select-none cursor-pointer',
            disabled && 'cursor-not-allowed opacity-60'
          )}
        >
          {label}
        </label>
        {description && (
          <p id={descId} className="text-xs text-text-muted mt-0.5">
            {description}
          </p>
        )}
      </div>
    </div>
  );
};
