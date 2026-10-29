import { RouteObject, Navigate } from 'react-router-dom';
import { RequireAuth } from '../layouts/RequireAuth';
import { RequireRole } from '../layouts/RequireRole';
import { MemberShell } from '../layouts/MemberShell';

// Public & Auth Pages
import { LandingPage } from '../features/public/LandingPage';
import { SignInPage } from '../features/auth/SignInPage';
import { RegisterPage } from '../features/auth/RegisterPage';
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage';
import { ResetPasswordPage } from '../features/auth/ResetPasswordPage';
import { ForcedPasswordChangePage } from '../features/auth/ForcedPasswordChangePage';
import { PublicPlansPage } from '../features/public/PublicPlansPage';
import { SessionExpiredPage } from '../features/public/SessionExpiredPage';
import { NotFoundPage } from '../features/public/NotFoundPage';
import { ForbiddenPage } from '../features/public/ForbiddenPage';
import { RolePlaceholderPage } from '../features/public/RolePlaceholderPage';

// Member Core Pages
import { HomeDashboard } from '../features/dashboard/HomeDashboard';
import { BrowseClassesPage } from '../features/classes/BrowseClassesPage';
import { ClassDetailsPage } from '../features/classes/ClassDetailsPage';
import { SessionDetailsPage } from '../features/sessions/SessionDetailsPage';
import { MyBookingsPage } from '../features/bookings/MyBookingsPage';
import { MembershipPage } from '../features/membership/MembershipPage';
import { PaymentResultPage } from '../features/membership/PaymentResultPage';
import { PaymentsPage } from '../features/payments/PaymentsPage';
import { AccountPage } from '../features/account/AccountPage';

export interface RouteMeta {
  screenId: string;
}

export const routes: RouteObject[] = [
  // --- Public Routes ---
  {
    path: '/',
    // screenId: public-landing
    element: <LandingPage />,
  },
  {
    path: '/login',
    // screenId: public-login
    element: <SignInPage />,
  },
  {
    path: '/register',
    // screenId: public-register
    element: <RegisterPage />,
  },
  {
    path: '/forgot-password',
    // screenId: public-forgot-password
    element: <ForgotPasswordPage />,
  },
  {
    path: '/reset-password',
    // screenId: public-reset-password
    element: <ResetPasswordPage />,
  },
  {
    path: '/plans',
    // screenId: public-plans
    element: <PublicPlansPage />,
  },
  {
    path: '/session-expired',
    // screenId: public-session-expired
    element: <SessionExpiredPage />,
  },
  {
    path: '/403',
    // screenId: public-forbidden
    element: <ForbiddenPage />,
  },
  {
    path: '/404',
    // screenId: public-not-found
    element: <NotFoundPage />,
  },
  {
    path: '/forced-password-change',
    element: (
      <RequireAuth>
        <ForcedPasswordChangePage />
      </RequireAuth>
    ),
  },

  // --- Member Core App Routes ---
  {
    path: '/app',
    element: (
      <RequireAuth>
        <RequireRole roles={['MEMBER']}>
          <MemberShell />
        </RequireRole>
      </RequireAuth>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/app/dashboard" replace />,
      },
      {
        path: 'dashboard',
        // screenId: member-home-dashboard
        element: <HomeDashboard />,
      },
      {
        path: 'classes',
        // screenId: member-browse-classes
        element: <BrowseClassesPage />,
      },
      {
        path: 'classes/:id',
        // screenId: member-class-details
        element: <ClassDetailsPage />,
      },
      {
        path: 'sessions/:id',
        // screenId: member-session-details
        element: <SessionDetailsPage />,
      },
      {
        path: 'bookings',
        // screenId: member-my-bookings
        element: <MyBookingsPage />,
      },
      {
        path: 'membership',
        // screenId: member-membership
        element: <MembershipPage />,
      },
      {
        path: 'membership/checkout',
        // screenId: member-checkout
        element: <MembershipPage />,
      },
      {
        path: 'membership/payment-result',
        // screenId: member-payment-result
        element: <PaymentResultPage />,
      },
      {
        path: 'payments',
        // screenId: member-payments-invoices
        element: <PaymentsPage />,
      },
      {
        path: 'account',
        // screenId: member-account
        element: <AccountPage />,
      },
    ],
  },

  // --- Trainer & Admin Placeholders (Full implementations in Stage 12) ---
  {
    path: '/trainer/dashboard',
    element: (
      <RequireAuth>
        <RequireRole roles={['TRAINER']}>
          <RolePlaceholderPage roleName="TRAINER" />
        </RequireRole>
      </RequireAuth>
    ),
  },
  {
    path: '/admin/dashboard',
    element: (
      <RequireAuth>
        <RequireRole roles={['ADMIN']}>
          <RolePlaceholderPage roleName="ADMIN" />
        </RequireRole>
      </RequireAuth>
    ),
  },

  // Fallback Wildcard
  {
    path: '*',
    element: <Navigate to="/404" replace />,
  },
];
