import React from 'react';
import { Check } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface StepItem {
  id: string;
  label: string;
}

export interface StepperProps {
  steps: StepItem[];
  currentStepIndex: number;
  className?: string;
}

export const Stepper: React.FC<StepperProps> = ({
  steps,
  currentStepIndex,
  className,
}) => {
  return (
    <nav
      aria-label="Progress Stepper"
      className={twMerge(clsx('w-full py-4', className))}
    >
      <ol className="flex items-center justify-between w-full">
        {steps.map((step, idx) => {
          const isCompleted = idx < currentStepIndex;
          const isCurrent = idx === currentStepIndex;

          return (
            <li
              key={step.id}
              className={clsx(
                'relative flex flex-1 flex-col items-center text-center',
                idx !== steps.length - 1 &&
                  'after:content-[""] after:w-full after:h-0.5 after:border-b after:border-border after:absolute after:top-4 after:left-1/2 after:z-0',
                idx !== steps.length - 1 && isCompleted && 'after:border-brand-primary'
              )}
            >
              <div
                className={clsx(
                  'relative z-10 flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold transition-colors',
                  isCompleted
                    ? 'bg-brand-primary text-text-inverse'
                    : isCurrent
                    ? 'border-2 border-brand-primary bg-bg-page text-brand-primary'
                    : 'border border-border bg-bg-subtle text-text-muted'
                )}
                aria-current={isCurrent ? 'step' : undefined}
              >
                {isCompleted ? <Check className="w-4 h-4 stroke-[3]" aria-hidden="true" /> : idx + 1}
              </div>
              <span
                className={clsx(
                  'mt-2 text-xs font-medium max-w-[100px] truncate',
                  isCurrent ? 'text-brand-primary font-semibold' : 'text-text-muted'
                )}
              >
                {step.label}
              </span>
            </li>
          );
        })}
      </ol>
    </nav>
  );
};
