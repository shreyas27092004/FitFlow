import { forwardRef, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { TextField, TextFieldProps } from './TextField';

export interface PasswordFieldProps extends Omit<TextFieldProps, 'type' | 'rightIcon'> {
  showPasswordToggle?: boolean;
}

export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(
  ({ showPasswordToggle = true, ...props }, ref) => {
    const [isVisible, setIsVisible] = useState(false);

    const toggleVisibility = () => {
      setIsVisible((prev) => !prev);
    };

    return (
      <div className="relative">
        <TextField
          ref={ref}
          type={isVisible ? 'text' : 'password'}
          rightIcon={
            showPasswordToggle ? (
              <button
                type="button"
                tabIndex={0}
                disabled={props.disabled}
                onClick={toggleVisibility}
                aria-label={isVisible ? 'Hide password' : 'Show password'}
                className="pointer-events-auto p-1 text-text-muted hover:text-text-primary focus:outline-none focus:ring-2 focus:ring-brand-focus-ring rounded disabled:opacity-50 disabled:pointer-events-none"
              >
                {isVisible ? (
                  <EyeOff className="w-4 h-4" aria-hidden="true" />
                ) : (
                  <Eye className="w-4 h-4" aria-hidden="true" />
                )}
              </button>
            ) : undefined
          }
          {...props}
        />
      </div>
    );
  }
);

PasswordField.displayName = 'PasswordField';
