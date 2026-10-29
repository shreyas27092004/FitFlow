import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Home } from 'lucide-react';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-bg-page text-text-primary text-center">
      <div className="max-w-md">
        <h1 className="text-7xl font-extrabold text-brand-primary mb-4">404</h1>
        <h2 className="text-xl font-bold mb-2">Page Not Found</h2>
        <p className="text-sm text-text-secondary mb-6">
          The page or studio resource you requested does not exist or has been moved.
        </p>
        <Link to="/">
          <Button variant="primary" size="md" leftIcon={<Home className="w-4 h-4" />}>
            Return Home
          </Button>
        </Link>
      </div>
    </div>
  );
};
