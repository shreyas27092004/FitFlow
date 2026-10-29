import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PlanCard } from '../../components/PlanCard';
import { Button } from '../../components/Button';
import { DisclaimerNote } from '../../components/DisclaimerNote';
import { useGetPlansQuery } from '../../api/membershipApi';
import { Dumbbell } from 'lucide-react';

export const PublicPlansPage: React.FC = () => {
  const navigate = useNavigate();
  const { data: plans, isLoading, error } = useGetPlansQuery();

  return (
    <div className="min-h-screen flex flex-col bg-bg-page text-text-primary">
      <header className="border-b border-border bg-bg-surface px-4 md:px-8 h-16 flex items-center justify-between">
        <Link to="/" className="flex items-center gap-2 text-brand-primary font-black text-xl">
          <div className="w-8 h-8 rounded-lg bg-brand-primary text-text-inverse flex items-center justify-center">
            <Dumbbell className="w-5 h-5" aria-hidden="true" />
          </div>
          <span>FitFlow</span>
        </Link>
        <div className="flex items-center gap-3">
          <Link to="/login">
            <Button variant="outline" size="sm">
              Sign In
            </Button>
          </Link>
          <Link to="/register">
            <Button variant="primary" size="sm">
              Register
            </Button>
          </Link>
        </div>
      </header>

      <main className="flex-1 max-w-6xl mx-auto px-4 py-12 w-full">
        <div className="text-center max-w-xl mx-auto mb-12">
          <h1 className="text-3xl font-extrabold mb-3">Studio Membership Plans</h1>
          <p className="text-sm text-text-secondary">
            Select the tier that matches your monthly training goals. All memberships include live booking, waitlist priority, and verified safety screening.
          </p>
        </div>

        {isLoading ? (
          <p className="text-center text-sm text-text-muted">Loading plans...</p>
        ) : error ? (
          <p className="text-center text-sm text-red-500">Failed to load membership plans.</p>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {plans?.map((plan) => (
              <PlanCard
                key={plan.id}
                plan={plan}
                actionLabel="Choose Plan"
                onSelect={() => navigate('/register')}
              />
            ))}
          </div>
        )}

        <div className="mt-16">
          <DisclaimerNote />
        </div>
      </main>

      <footer className="border-t border-border bg-bg-surface px-4 py-6 text-center text-xs text-text-muted">
        <p>&copy; {new Date().getFullYear()} FitFlow. All rights reserved.</p>
      </footer>
    </div>
  );
};
