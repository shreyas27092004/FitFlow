import React, { forwardRef, useId } from 'react';
import { Calendar } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface DatePickerProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  helperText?: string;
  errorMessage?: string;
}

export const DatePicker = forwardRef<HTMLInputElement, DatePickerProps>(
  (
    {
      label,
      helperText,
      errorMessage,
      id: customId,
      disabled,
      required,
      className,
      min,
      max,
      ...props
    },
    ref
  ) => {
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

        <div className="relative flex items-center">
          <div className="absolute left-3 text-text-muted pointer-events-none" aria-hidden="true">
            <Calendar className="w-4 h-4" />
          </div>

          <input
            ref={ref}
            id={id}
            type="date"
            min={min}
            max={max}
            disabled={disabled}
            required={required}
            aria-invalid={hasError}
            aria-describedby={
              clsx({
                [errorId]: hasError,
                [helperId]: Boolean(helperText) && !hasError,
              }) || undefined
            }
            className={twMerge(
              clsx(
                'w-full h-10 pl-10 pr-3 py-2 rounded-md border text-sm transition-colors',
                'bg-bg-page text-text-primary placeholder:text-text-muted',
                'focus:outline-none focus:ring-2 focus:ring-brand-focus-ring focus:border-brand-primary',
                'disabled:bg-bg-subtle disabled:opacity-60 disabled:cursor-not-allowed',
                hasError
                  ? 'border-red-500 focus:ring-red-500 focus:border-red-500'
                  : 'border-border',
                className
              )
            )}
            {...props}
          />
        </div>

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
  }
);

DatePicker.displayName = 'DatePicker';
