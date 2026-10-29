import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { PasswordField } from '../../components/PasswordField';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { Banner } from '../../components/Banner';
import { useResetPasswordMutation } from '../../api/authApi';
import { Dumbbell } from 'lucide-react';

const resetSchema = z
  .object({
    newPassword: z
      .string()
      .min(10, 'Password must be at least 10 characters long')
      .regex(/[A-Za-z]/, 'Password must contain at least one letter')
      .regex(/[0-9]/, 'Password must contain at least one digit'),
    confirmPassword: z.string().min(1, 'Please confirm your new password'),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type ResetFormData = z.infer<typeof resetSchema>;

export const ResetPasswordPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [resetMutation, { isLoading }] = useResetPasswordMutation();

  const [token, setToken] = useState<string | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);
  const [isSuccess, setIsSuccess] = useState(false);

  useEffect(() => {
    const urlToken = searchParams.get('token');
    if (urlToken) {
      setToken(urlToken);
      // Security requirement: Remove token from address bar immediately
      if (typeof window !== 'undefined' && window.history?.replaceState) {
        window.history.replaceState({}, document.title, window.location.pathname);
      }
    }
  }, [searchParams]);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetFormData>({
    resolver: zodResolver(resetSchema),
  });

  const onSubmit = async (data: ResetFormData) => {
    if (!token) {
      setServerError('Reset token is missing or has expired. Please request a new link.');
      return;
    }

    setServerError(null);
    try {
      await resetMutation({
        token,
        newPassword: data.newPassword,
      }).unwrap();
      setIsSuccess(true);
      setTimeout(() => navigate('/login'), 2500);
    } catch (err: any) {
      setServerError(err.message || 'Failed to reset password. Link may be invalid or expired.');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-bg-page text-text-primary">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <Link
            to="/"
            className="inline-flex items-center gap-2 text-brand-primary font-black text-2xl tracking-tight mb-2"
          >
            <div className="w-10 h-10 rounded-xl bg-brand-primary text-text-inverse flex items-center justify-center">
              <Dumbbell className="w-6 h-6" aria-hidden="true" />
            </div>
            <span>FitFlow</span>
          </Link>
          <h1 className="text-xl font-bold">Choose a new password</h1>
        </div>

        <Card className="p-6 md:p-8 border border-border shadow-md">
          {isSuccess ? (
            <div className="space-y-4 text-center">
              <Banner
                type="success"
                title="Password successfully reset!"
              >
                Redirecting you to sign in with your new credentials...
              </Banner>
              <Link to="/login">
                <Button variant="primary" size="md">
                  Sign In Now
                </Button>
              </Link>
            </div>
          ) : !token ? (
            <div className="space-y-4 text-center">
              <Banner
                type="danger"
                title="Invalid or Missing Reset Token"
              >
                No valid reset token was detected in your link. Please request a new link.
              </Banner>
              <Link to="/forgot-password">
                <Button variant="primary" size="md">
                  Request New Reset Link
                </Button>
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              {serverError && <Banner type="danger" title={serverError} />}

              <PasswordField
                label="New Password"
                autoComplete="new-password"
                required
                helperText="At least 10 characters with one letter and one number."
                errorMessage={errors.newPassword?.message}
                {...register('newPassword')}
              />

              <PasswordField
                label="Confirm New Password"
                autoComplete="new-password"
                required
                errorMessage={errors.confirmPassword?.message}
                {...register('confirmPassword')}
              />

              <Button
                type="submit"
                variant="primary"
                size="lg"
                className="w-full mt-2"
                disabled={isLoading}
                isLoading={isLoading}
                loadingText="Updating password..."
              >
                Update Password
              </Button>
            </form>
          )}
        </Card>
      </div>
    </div>
  );
};
