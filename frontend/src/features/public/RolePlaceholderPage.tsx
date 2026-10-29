import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { useAppDispatch, useAppSelector } from '../../app/store';
import { clearCredentials } from '../auth/authSlice';
import { useLogoutMutation } from '../../api/authApi';
import { Sparkles, LogOut } from 'lucide-react';

export const RolePlaceholderPage: React.FC<{ roleName: 'TRAINER' | 'ADMIN' }> = ({
  roleName,
}) => {
  const { user } = useAppSelector((state) => state.auth);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [logoutMutation, { isLoading }] = useLogoutMutation();

  const handleLogout = async () => {
    try {
      await logoutMutation().unwrap();
    } catch {
      // Ignore
    } finally {
      dispatch(clearCredentials());
      navigate('/login');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-bg-page text-text-primary text-center">
      <Card className="max-w-md p-8 border border-border shadow-lg">
        <div className="w-16 h-16 rounded-2xl bg-brand-primary/10 text-brand-primary flex items-center justify-center mx-auto mb-4">
          <Sparkles className="w-8 h-8" aria-hidden="true" />
        </div>
        <h1 className="text-2xl font-bold mb-2">
          {roleName === 'TRAINER' ? 'Trainer Workspace' : 'Administrator Workspace'}
        </h1>
        <p className="text-sm text-text-secondary mb-2">
          Welcome, <strong>{user?.fullName || user?.email}</strong>!
        </p>
        <p className="text-xs text-text-muted mb-8 leading-relaxed">
          The full dedicated management suites for Trainers and Administrators are scheduled for release in Stage 12.
        </p>

        <Button
          type="button"
          variant="outline"
          size="md"
          onClick={handleLogout}
          isLoading={isLoading}
          leftIcon={<LogOut className="w-4 h-4" />}
          className="w-full"
        >
          Sign Out
        </Button>
      </Card>
    </div>
  );
};
