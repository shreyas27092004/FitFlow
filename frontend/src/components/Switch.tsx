import React, { useId } from 'react';
import * as RadixSwitch from '@radix-ui/react-switch';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface SwitchProps {
  id?: string;
  label: React.ReactNode;
  description?: string;
  checked?: boolean;
  onCheckedChange?: (checked: boolean) => void;
  disabled?: boolean;
  className?: string;
}

export const Switch: React.FC<SwitchProps> = ({
  id: customId,
  label,
  description,
  checked,
  onCheckedChange,
  disabled,
  className,
}) => {
  const defaultId = useId();
  const id = customId || defaultId;
  const descId = `${id}-desc`;

  return (
    <div className="flex items-center justify-between gap-4">
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

      <RadixSwitch.Root
        id={id}
        aria-label={typeof label === 'string' ? label : 'Switch'}
        checked={checked}
        onCheckedChange={onCheckedChange}
        disabled={disabled}
        aria-describedby={description ? descId : undefined}
        className={twMerge(
          clsx(
            'peer inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-focus-ring focus-visible:ring-offset-2',
            'disabled:cursor-not-allowed disabled:opacity-50',
            'data-[state=checked]:bg-brand-primary data-[state=unchecked]:bg-border',
            className
          )
        )}
      >
        <RadixSwitch.Thumb
          className={clsx(
            'pointer-events-none block h-5 w-5 rounded-full bg-white shadow-lg ring-0 transition-transform',
            'data-[state=checked]:translate-x-5 data-[state=unchecked]:translate-x-0'
          )}
        />
      </RadixSwitch.Root>
    </div>
  );
};
