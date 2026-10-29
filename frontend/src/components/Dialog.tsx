import React from 'react';
import * as RadixDialog from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface DialogProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: React.ReactNode;
  footerButtons?: React.ReactNode;
  maxWidth?: 'sm' | 'md' | 'lg' | 'xl';
}

export const Dialog: React.FC<DialogProps> = ({
  isOpen,
  onClose,
  title,
  description,
  children,
  footerButtons,
  maxWidth = 'md',
}) => {
  const maxWidthStyles = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-xl',
  };

  return (
    <RadixDialog.Root open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <RadixDialog.Portal>
        <RadixDialog.Overlay className="fixed inset-0 z-modal-backdrop bg-black/50 backdrop-blur-sm animate-in fade-in" />
        <RadixDialog.Content
          className={twMerge(
            clsx(
              'fixed left-[50%] top-[50%] z-modal w-full translate-x-[-50%] translate-y-[-50%]',
              'rounded-lg border border-border bg-bg-surface-elevated p-6 shadow-xl animate-in fade-in zoom-in-95',
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-focus-ring',
              maxWidthStyles[maxWidth]
            )
          )}
        >
          <div className="flex items-start justify-between gap-4">
            <RadixDialog.Title className="text-lg font-semibold text-text-primary">
              {title}
            </RadixDialog.Title>
            <RadixDialog.Close asChild>
              <button
                type="button"
                onClick={onClose}
                aria-label="Close dialog"
                className="rounded-md p-1 text-text-muted hover:text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-focus-ring"
              >
                <X className="w-5 h-5" aria-hidden="true" />
              </button>
            </RadixDialog.Close>
          </div>

          {description ? (
            <RadixDialog.Description className="mt-2 text-sm text-text-secondary">
              {description}
            </RadixDialog.Description>
          ) : (
            <RadixDialog.Description className="sr-only">
              {title}
            </RadixDialog.Description>
          )}

          <div className="mt-4">{children}</div>

          {footerButtons && (
            <div className="mt-6 flex items-center justify-end gap-3 border-t border-border pt-4">
              {footerButtons}
            </div>
          )}
        </RadixDialog.Content>
      </RadixDialog.Portal>
    </RadixDialog.Root>
  );
};
