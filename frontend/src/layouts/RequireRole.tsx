import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAppSelector } from '../app/store';

export interface RequireRoleProps {
  roles: string[];
  children: React.ReactElement;
}

export const RequireRole: React.FC<RequireRoleProps> = ({ roles, children }) => {
  const { user } = useAppSelector((state) => state.auth);

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const hasRole = roles.some((role) => user.roles?.includes(role));

  if (!hasRole) {
    // If user is a TRAINER or ADMIN and trying to access member routes, route to their role dashboard/placeholder
    if (user.roles?.includes('TRAINER')) {
      return <Navigate to="/trainer/dashboard" replace />;
    }
    if (user.roles?.includes('ADMIN')) {
      return <Navigate to="/admin/dashboard" replace />;
    }
    return <Navigate to="/403" replace />;
  }

  return children;
};
