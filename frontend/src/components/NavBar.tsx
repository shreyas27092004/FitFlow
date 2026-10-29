import React from 'react';
import { Dumbbell, Moon, Sun, User, LogOut } from 'lucide-react';
import { Button } from './Button';
import { ThemePreference } from '../lib/theme';
import { clsx } from 'clsx';
import { Link } from 'react-router-dom';

export interface NavBarProps {
  currentRole?: string;
  userName?: string;
  themePreference: ThemePreference;
  onThemeToggle: () => void;
  onLogout?: () => void;
  className?: string;
}

export const NavBar: React.FC<NavBarProps> = ({
  currentRole,
  userName,
  themePreference,
  onThemeToggle,
  onLogout,
  className,
}) => {
  return (
    <header
      className={clsx(
        'sticky top-0 z-sticky w-full border-b border-border bg-bg-surface-elevated/95 backdrop-blur px-4 md:px-8 h-16 flex items-center justify-between transition-colors',
        className
      )}
    >
      <div className="flex items-center gap-3">
        <Link
          to="/"
          className="flex items-center gap-2 text-brand-primary font-extrabold text-xl tracking-tight focus:outline-none focus:ring-2 focus:ring-brand-focus-ring rounded-md p-1"
        >
          <div className="w-8 h-8 rounded-lg bg-brand-primary text-text-inverse flex items-center justify-center">
            <Dumbbell className="w-5 h-5" aria-hidden="true" />
          </div>
          <span>FitFlow</span>
        </Link>
        {currentRole && (
          <span className="hidden sm:inline-block px-2 py-0.5 rounded text-[11px] font-semibold uppercase tracking-wider bg-bg-subtle text-text-muted border border-border">
            {currentRole}
          </span>
        )}
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onThemeToggle}
          aria-label={`Switch theme (currently ${themePreference})`}
          className="p-2 rounded-md border border-border text-text-muted hover:text-text-primary hover:bg-bg-subtle focus:outline-none focus:ring-2 focus:ring-brand-focus-ring transition-colors"
        >
          {themePreference === 'dark' ? (
            <Sun className="w-4 h-4" aria-hidden="true" />
          ) : (
            <Moon className="w-4 h-4" aria-hidden="true" />
          )}
        </button>

        {userName ? (
          <div className="flex items-center gap-2">
            <Link
              to="/app/account"
              aria-label={`View account profile${userName ? ` (${userName})` : ''}`}
              className="flex items-center gap-2 p-1.5 rounded-md hover:bg-bg-subtle text-text-primary text-sm font-medium focus:outline-none focus:ring-2 focus:ring-brand-focus-ring"
            >
              <div className="w-8 h-8 rounded-full bg-brand-primary/10 text-brand-primary flex items-center justify-center font-bold text-xs">
                <User className="w-4 h-4" aria-hidden="true" />
              </div>
              <span className="hidden md:inline-block max-w-[120px] truncate">{userName}</span>
            </Link>

            {onLogout && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={onLogout}
                aria-label="Sign out of account"
                title="Sign out"
              >
                <LogOut className="w-4 h-4 text-text-muted hover:text-red-500" aria-hidden="true" />
              </Button>
            )}
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <Link to="/login">
              <Button type="button" variant="outline" size="sm">
                Sign In
              </Button>
            </Link>
            <Link to="/register">
              <Button type="button" variant="primary" size="sm">
                Register
              </Button>
            </Link>
          </div>
        )}
      </div>
    </header>
  );
};
