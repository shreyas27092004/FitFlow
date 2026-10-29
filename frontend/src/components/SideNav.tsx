import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Calendar,
  BookmarkCheck,
  CreditCard,
  Receipt,
  User,
  HeartPulse,
  Bot,
  Lock,
} from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface NavItemConfig {
  label: string;
  to: string;
  icon: React.ReactNode;
  disabled?: boolean;
  badge?: string;
}

export const MEMBER_NAV_ITEMS: NavItemConfig[] = [
  { label: 'Dashboard', to: '/app/dashboard', icon: <LayoutDashboard className="w-4 h-4" /> },
  { label: 'Browse Classes', to: '/app/classes', icon: <Calendar className="w-4 h-4" /> },
  { label: 'My Bookings', to: '/app/bookings', icon: <BookmarkCheck className="w-4 h-4" /> },
  { label: 'Membership', to: '/app/membership', icon: <CreditCard className="w-4 h-4" /> },
  { label: 'Payments', to: '/app/payments', icon: <Receipt className="w-4 h-4" /> },
  { label: 'Account', to: '/app/account', icon: <User className="w-4 h-4" /> },
  {
    label: 'Wellness',
    to: '/app/wellness/consent',
    icon: <HeartPulse className="w-4 h-4" />,
    disabled: true,
    badge: 'Coming in Stage 12',
  },
  {
    label: 'AI Assistant',
    to: '/app/assistant',
    icon: <Bot className="w-4 h-4" />,
    disabled: true,
    badge: 'Coming in Stage 12',
  },
];

export interface SideNavProps {
  items?: NavItemConfig[];
  className?: string;
}

export const SideNav: React.FC<SideNavProps> = ({
  items = MEMBER_NAV_ITEMS,
  className,
}) => {
  return (
    <nav
      aria-label="Main Navigation"
      className={twMerge(
        clsx(
          'hidden md:flex flex-col w-64 border-r border-border bg-bg-surface p-4 gap-1 min-h-[calc(100vh-4rem)]',
          className
        )
      )}
    >
      <div className="text-[11px] font-semibold uppercase tracking-wider text-text-muted px-3 py-2">
        Member Menu
      </div>

      {items.map((item) => {
        if (item.disabled) {
          return (
            <div
              key={item.label}
              aria-disabled="true"
              title={item.badge}
              className="flex items-center justify-between px-3 py-2.5 rounded-md text-sm text-text-muted opacity-50 cursor-not-allowed select-none"
            >
              <div className="flex items-center gap-3">
                <span aria-hidden="true">{item.icon}</span>
                <span>{item.label}</span>
              </div>
              <Lock className="w-3.5 h-3.5" aria-hidden="true" />
            </div>
          );
        }

        return (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 px-3 py-2.5 rounded-md text-sm font-medium transition-colors outline-none focus-visible:ring-2 focus-visible:ring-brand-focus-ring',
                isActive
                  ? 'bg-brand-primary/10 text-brand-primary font-semibold'
                  : 'text-text-secondary hover:text-text-primary hover:bg-bg-subtle'
              )
            }
          >
            {({ isActive }) => (
              <>
                <span
                  className={isActive ? 'text-brand-primary' : 'text-text-muted'}
                  aria-hidden="true"
                >
                  {item.icon}
                </span>
                <span>{item.label}</span>
              </>
            )}
          </NavLink>
        );
      })}
    </nav>
  );
};
