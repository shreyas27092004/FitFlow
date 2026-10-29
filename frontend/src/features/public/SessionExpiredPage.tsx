import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Clock, LogIn } from 'lucide-react';
import en from '../../i18n/en.json';

export const SessionExpiredPage: React.FC = () => {
  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-bg-page text-text-primary text-center">
      <div className="max-w-md">
        <div className="w-16 h-16 rounded-2xl bg-amber-500/10 text-amber-600 flex items-center justify-center mx-auto mb-4">
          <Clock className="w-8 h-8" aria-hidden="true" />
        </div>
        <h1 className="text-2xl font-bold mb-2">Session Expired</h1>
        <p className="text-sm text-text-secondary mb-6">{en.errors.UNAUTHENTICATED}</p>
        <Link to="/login">
          <Button variant="primary" size="md" leftIcon={<LogIn className="w-4 h-4" />}>
            Sign In Again
          </Button>
        </Link>
      </div>
    </div>
  );
};
