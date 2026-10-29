import React, { useId } from 'react';
import * as RadixSelect from '@radix-ui/react-select';
import { Check, ChevronDown } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps {
  label: string;
  options: SelectOption[];
  value?: string;
  onValueChange?: (value: string) => void;
  placeholder?: string;
  helperText?: string;
  errorMessage?: string;
  disabled?: boolean;
  required?: boolean;
  className?: string;
  id?: string;
}

export const Select: React.FC<SelectProps> = ({
  label,
  options,
  value,
  onValueChange,
  placeholder = 'Select an option',
  helperText,
  errorMessage,
  disabled,
  required,
  className,
  id: customId,
}) => {
  const defaultId = useId();
  const id = customId || defaultId;
  const errorId = `${id}-error`;
  const helperId = `${id}-helper`;
  const hasError = Boolean(errorMessage);

  return (
    <div className="w-full flex flex-col gap-1.5">
      <label
        htmlFor={id}
        className="text-sm font-medium text-text-primary flex items-center justify-between"
      >
        <span>
          {label}
          {required && <span className="text-red-500 ml-1" aria-hidden="true">*</span>}
        </span>
      </label>

      <RadixSelect.Root value={value} onValueChange={onValueChange} disabled={disabled}>
        <RadixSelect.Trigger
          id={id}
          aria-label={label}
          aria-invalid={hasError}
          aria-describedby={
            clsx({
              [errorId]: hasError,
              [helperId]: Boolean(helperText) && !hasError,
            }) || undefined
          }
          className={twMerge(
            clsx(
              'w-full h-10 px-3 py-2 rounded-md border text-sm flex items-center justify-between transition-colors',
              'bg-bg-page text-text-primary',
              'focus:outline-none focus:ring-2 focus:ring-brand-focus-ring focus:border-brand-primary',
              'disabled:bg-bg-subtle disabled:opacity-60 disabled:cursor-not-allowed',
              hasError
                ? 'border-red-500 focus:ring-red-500 focus:border-red-500'
                : 'border-border',
              className
            )
          )}
        >
          <RadixSelect.Value placeholder={placeholder} />
          <RadixSelect.Icon asChild>
            <ChevronDown className="w-4 h-4 text-text-muted" aria-hidden="true" />
          </RadixSelect.Icon>
        </RadixSelect.Trigger>

        <RadixSelect.Portal>
          <RadixSelect.Content
            className="z-dropdown min-w-[8rem] overflow-hidden rounded-md border border-border bg-bg-surface-elevated text-text-primary shadow-md animate-in fade-in-80"
            position="popper"
            sideOffset={4}
          >
            <RadixSelect.Viewport className="p-1">
              {options.map((opt) => (
                <RadixSelect.Item
                  key={opt.value}
                  value={opt.value}
                  disabled={opt.disabled}
                  className={clsx(
                    'relative flex w-full cursor-default select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none transition-colors',
                    'hover:bg-bg-subtle focus:bg-bg-subtle focus:text-text-primary',
                    'data-[disabled]:pointer-events-none data-[disabled]:opacity-50'
                  )}
                >
                  <span className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                    <RadixSelect.ItemIndicator>
                      <Check className="h-4 w-4 text-brand-primary" aria-hidden="true" />
                    </RadixSelect.ItemIndicator>
                  </span>
                  <RadixSelect.ItemText>{opt.label}</RadixSelect.ItemText>
                </RadixSelect.Item>
              ))}
            </RadixSelect.Viewport>
          </RadixSelect.Content>
        </RadixSelect.Portal>
      </RadixSelect.Root>

      {hasError ? (
        <p id={errorId} role="alert" className="text-xs text-red-500 font-medium">
          {errorMessage}
        </p>
      ) : helperText ? (
        <p id={helperId} className="text-xs text-text-muted">
          {helperText}
        </p>
      ) : null}
    </div>
  );
};
