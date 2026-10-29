import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { TextField } from '../../components/TextField';
import { PasswordField } from '../../components/PasswordField';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { Banner } from '../../components/Banner';
import { useLoginMutation } from '../../api/authApi';
import { useAppDispatch } from '../../app/store';
import { setCredentials } from './authSlice';
import { Dumbbell } from 'lucide-react';
import en from '../../i18n/en.json';

const signInSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type SignInFormData = z.infer<typeof signInSchema>;

export const SignInPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const [loginMutation, { isLoading }] = useLoginMutation();

  const [serverError, setServerError] = useState<string | null>(null);
  const [lockoutSeconds, setLockoutSeconds] = useState<number | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignInFormData>({
    resolver: zodResolver(signInSchema),
  });

  // Countdown timer for account lockout
  useEffect(() => {
    if (lockoutSeconds === null || lockoutSeconds <= 0) return;
    const interval = setInterval(() => {
      setLockoutSeconds((prev) => {
        if (prev === null || prev <= 1) {
          clearInterval(interval);
          setServerError(null);
          return null;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [lockoutSeconds]);

  const onSubmit = async (data: SignInFormData) => {
    setServerError(null);
    try {
      const response = await loginMutation(data).unwrap();
      dispatch(
        setCredentials({
          accessToken: response.accessToken,
          user: response.user,
        })
      );

      if (response.user.mustChangePassword) {
        navigate('/forced-password-change', { replace: true });
        return;
      }

      // Route based on roles
      if (response.user.roles.includes('TRAINER')) {
        navigate('/trainer/dashboard', { replace: true });
      } else if (response.user.roles.includes('ADMIN')) {
        navigate('/admin/dashboard', { replace: true });
      } else {
        const from = (location.state as any)?.from?.pathname || '/app/dashboard';
        navigate(from, { replace: true });
      }
    } catch (err: any) {
      if (err.status === 429 || err.code === 'ACCOUNT_LOCKED') {
        const retryAfter = err.retryAfter || 900; // 15 minutes default
        setLockoutSeconds(retryAfter);
        setServerError(en.auth.accountLocked);
      } else if (err.code === 'INVALID_CREDENTIALS' || err.status === 401) {
        setServerError('Invalid email or password.');
      } else {
        setServerError(err.message || en.errors.INTERNAL_ERROR);
      }
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
          <h1 className="text-xl font-bold">{en.auth.signInTitle}</h1>
          <p className="text-xs text-text-muted mt-1">{en.brand.tagline}</p>
        </div>

        <Card className="p-6 md:p-8 border border-border shadow-md">
          {serverError && (
            <div className="mb-6">
              <Banner type="danger" title={serverError}>
                {lockoutSeconds !== null && lockoutSeconds > 0 && (
                  <p className="mt-1 font-semibold">
                    Retry available in {Math.floor(lockoutSeconds / 60)}m {lockoutSeconds % 60}s
                  </p>
                )}
              </Banner>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <TextField
              label={en.auth.emailLabel}
              type="email"
              autoComplete="email"
              required
              errorMessage={errors.email?.message}
              {...register('email')}
            />

            <div>
              <PasswordField
                label={en.auth.passwordLabel}
                autoComplete="current-password"
                required
                errorMessage={errors.password?.message}
                {...register('password')}
              />
              <div className="mt-1 text-right">
                <Link
                  to="/forgot-password"
                  className="text-xs text-brand-primary hover:underline font-medium"
                >
                  {en.auth.forgotPassword}
                </Link>
              </div>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full mt-2"
              disabled={isLoading || (lockoutSeconds !== null && lockoutSeconds > 0)}
              isLoading={isLoading}
              loadingText="Signing in..."
            >
              {en.auth.signInButton}
            </Button>
          </form>

          <div className="mt-6 pt-6 border-t border-border text-center text-xs text-text-secondary">
            <span>Don&apos;t have an account? </span>
            <Link to="/register" className="text-brand-primary font-semibold hover:underline">
              Create an account
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
};
