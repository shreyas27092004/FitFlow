import React, { useState } from 'react';
import { Dialog } from './Dialog';
import { Button } from './Button';
import { TextField } from './TextField';

export interface ConfirmDialogProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  description: string;
  confirmationWord?: string; // If supplied, user must type this exact word to confirm
  confirmLabel?: string;
  cancelLabel?: string;
  isDestructive?: boolean;
  isLoading?: boolean;
  onConfirm: () => void;
  children?: React.ReactNode;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  isOpen,
  onClose,
  title,
  description,
  confirmationWord,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  isDestructive = false,
  isLoading = false,
  onConfirm,
  children,
}) => {
  const [typedInput, setTypedInput] = useState('');

  const isTypedMatch = !confirmationWord || typedInput.trim() === confirmationWord;

  const handleClose = () => {
    setTypedInput('');
    onClose();
  };

  const handleConfirm = () => {
    if (isTypedMatch) {
      onConfirm();
    }
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={handleClose}
      title={title}
      description={description}
      footerButtons={
        <>
          <Button
            type="button"
            variant="outline"
            onClick={handleClose}
            disabled={isLoading}
          >
            {cancelLabel}
          </Button>
          <Button
            type="button"
            variant={isDestructive ? 'danger' : 'primary'}
            onClick={handleConfirm}
            disabled={!isTypedMatch || isLoading}
            isLoading={isLoading}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {children && <div className="mb-4">{children}</div>}

      {confirmationWord && (
        <div className="mt-4 flex flex-col gap-2">
          <p className="text-xs text-text-muted">
            To proceed, please type <strong className="text-text-primary">{confirmationWord}</strong> below:
          </p>
          <TextField
            label="Confirmation Word"
            value={typedInput}
            onChange={(e) => setTypedInput(e.target.value)}
            placeholder={`Type ${confirmationWord}`}
            autoComplete="off"
          />
        </div>
      )}
    </Dialog>
  );
};
