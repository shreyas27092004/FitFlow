import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Calendar,
  BookmarkCheck,
  CreditCard,
  User,
} from 'lucide-react';
import { clsx } from 'clsx';

export const MOBILE_MEMBER_TABS = [
  { label: 'Dashboard', to: '/app/dashboard', icon: <LayoutDashboard className="w-5 h-5" /> },
  { label: 'Classes', to: '/app/classes', icon: <Calendar className="w-5 h-5" /> },
  { label: 'Bookings', to: '/app/bookings', icon: <BookmarkCheck className="w-5 h-5" /> },
  { label: 'Plans', to: '/app/membership', icon: <CreditCard className="w-5 h-5" /> },
  { label: 'Account', to: '/app/account', icon: <User className="w-5 h-5" /> },
];

export const BottomTabs: React.FC = () => {
  return (
    <nav
      aria-label="Mobile Navigation"
      className="md:hidden fixed bottom-0 left-0 right-0 z-sticky h-16 border-t border-border bg-bg-surface-elevated flex items-center justify-around px-2"
    >
      {MOBILE_MEMBER_TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          className={({ isActive }) =>
            clsx(
              'flex flex-col items-center justify-center flex-1 py-1 text-[11px] font-medium transition-colors outline-none focus-visible:ring-2 focus-visible:ring-brand-focus-ring',
              isActive ? 'text-brand-primary font-semibold' : 'text-text-muted hover:text-text-primary'
            )
          }
        >
          {({ isActive }) => (
            <>
              <span className={clsx('mb-0.5', isActive && 'text-brand-primary')} aria-hidden="true">
                {tab.icon}
              </span>
              <span>{tab.label}</span>
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );
};
