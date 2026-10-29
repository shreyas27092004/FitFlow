import React from 'react';
import * as RadixRadioGroup from '@radix-ui/react-radio-group';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface RadioOption {
  value: string;
  label: string;
  description?: string;
  disabled?: boolean;
}

export interface RadioGroupProps {
  label: string;
  name?: string;
  value?: string;
  onValueChange?: (value: string) => void;
  options: RadioOption[];
  disabled?: boolean;
  className?: string;
}

export const RadioGroup: React.FC<RadioGroupProps> = ({
  label,
  name,
  value,
  onValueChange,
  options,
  disabled,
  className,
}) => {
  return (
    <div className="w-full flex flex-col gap-2">
      <span className="text-sm font-medium text-text-primary" id={`${name || 'radio'}-group-label`}>
        {label}
      </span>

      <RadixRadioGroup.Root
        aria-labelledby={`${name || 'radio'}-group-label`}
        name={name}
        value={value}
        onValueChange={onValueChange}
        disabled={disabled}
        className={twMerge(clsx('flex flex-col gap-2', className))}
      >
        {options.map((option) => {
          const itemKey = `${name || 'radio'}-${option.value}`;
          return (
            <div key={option.value} className="flex items-start gap-3">
              <RadixRadioGroup.Item
                id={itemKey}
                aria-label={option.label}
                value={option.value}
                disabled={option.disabled || disabled}
                className={clsx(
                  'h-5 w-5 rounded-full border border-border text-brand-primary transition-colors',
                  'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-focus-ring focus-visible:ring-offset-2',
                  'disabled:cursor-not-allowed disabled:opacity-50',
                  'data-[state=checked]:border-brand-primary'
                )}
              >
                <RadixRadioGroup.Indicator className="flex items-center justify-center w-full h-full relative after:content-[''] after:block after:w-2.5 after:h-2.5 after:rounded-full after:bg-brand-primary" />
              </RadixRadioGroup.Item>

              <div className="flex flex-col text-sm">
                <label
                  htmlFor={itemKey}
                  className={clsx(
                    'font-medium text-text-primary cursor-pointer select-none',
                    (option.disabled || disabled) && 'cursor-not-allowed opacity-60'
                  )}
                >
                  {option.label}
                </label>
                {option.description && (
                  <p className="text-xs text-text-muted mt-0.5">{option.description}</p>
                )}
              </div>
            </div>
          );
        })}
      </RadixRadioGroup.Root>
    </div>
  );
};
