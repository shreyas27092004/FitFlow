import React from 'react';
import { ShieldAlert } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export const MANDATORY_WELLNESS_DISCLAIMER =
  'Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use.';

export interface DisclaimerNoteProps {
  variant?: 'prominent' | 'compact';
  customText?: string;
  className?: string;
}

export const DisclaimerNote: React.FC<DisclaimerNoteProps> = ({
  variant = 'prominent',
  customText,
  className,
}) => {
  const text = customText || MANDATORY_WELLNESS_DISCLAIMER;

  if (variant === 'compact') {
    return (
      <p
        role="note"
        className={twMerge(
          clsx(
            'text-[11px] text-text-muted italic leading-relaxed border-t border-border pt-2',
            className
          )
        )}
      >
        {text}
      </p>
    );
  }

  return (
    <div
      role="note"
      className={twMerge(
        clsx(
          'flex items-start gap-3 p-3.5 rounded-lg border border-amber-200 bg-amber-50/70 text-amber-950 dark:border-amber-900/60 dark:bg-amber-950/30 dark:text-amber-200 text-xs leading-relaxed',
          className
        )
      )}
    >
      <ShieldAlert className="w-4 h-4 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" aria-hidden="true" />
      <div>
        <span className="font-semibold block mb-0.5">Health & Safety Disclaimer:</span>
        <span>{text}</span>
      </div>
    </div>
  );
};
