import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate } from 'react-router-dom';
import { TextField } from '../../components/TextField';
import { PasswordField } from '../../components/PasswordField';
import { DatePicker } from '../../components/DatePicker';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { Banner } from '../../components/Banner';
import { useRegisterMutation } from '../../api/authApi';
import { useAppDispatch } from '../../app/store';
import { setCredentials } from './authSlice';
import { Dumbbell } from 'lucide-react';
import en from '../../i18n/en.json';

// Helper to calculate age in years from YYYY-MM-DD
function calculateAge(dobString: string): number {
  if (!dobString) return 0;
  const dob = new Date(dobString);
  const now = new Date();
  let age = now.getFullYear() - dob.getFullYear();
  const monthDiff = now.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < dob.getDate())) {
    age--;
  }
  return age;
}

const registerSchema = z
  .object({
    fullName: z.string().min(2, 'Full name must be at least 2 characters'),
    email: z.string().email('Please enter a valid email address'),
    dateOfBirth: z
      .string()
      .min(1, 'Date of birth is required')
      .refine((dob) => calculateAge(dob) >= 14, {
        message: en.auth.ageError,
      }),
    password: z
      .string()
      .min(10, 'Password must be at least 10 characters long')
      .regex(/[A-Za-z]/, 'Password must contain at least one letter')
      .regex(/[0-9]/, 'Password must contain at least one digit'),
    phone: z.string().optional(),
  })
  .refine((data) => data.password !== data.email, {
    message: 'Password must not match your email address',
    path: ['password'],
  });

type RegisterFormData = z.infer<typeof registerSchema>;

export const RegisterPage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [registerMutation, { isLoading }] = useRegisterMutation();

  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterFormData>({
    resolver: zodResolver(registerSchema),
  });

  const onSubmit = async (data: RegisterFormData) => {
    setServerError(null);
    try {
      const response = await registerMutation(data).unwrap();
      dispatch(
        setCredentials({
          accessToken: response.accessToken,
          user: response.user,
        })
      );
      navigate('/app/dashboard', { replace: true });
    } catch (err: any) {
      if (err.errors && err.errors.length > 0) {
        setServerError(err.errors.map((e: any) => e.message).join('. '));
      } else {
        setServerError(err.message || en.errors.INTERNAL_ERROR);
      }
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-bg-page text-text-primary">
      <div className="w-full max-w-md my-8">
        <div className="text-center mb-6">
          <Link
            to="/"
            className="inline-flex items-center gap-2 text-brand-primary font-black text-2xl tracking-tight mb-2"
          >
            <div className="w-10 h-10 rounded-xl bg-brand-primary text-text-inverse flex items-center justify-center">
              <Dumbbell className="w-6 h-6" aria-hidden="true" />
            </div>
            <span>FitFlow</span>
          </Link>
          <h1 className="text-xl font-bold">{en.auth.registerTitle}</h1>
          <p className="text-xs text-text-muted mt-1">{en.brand.tagline}</p>
        </div>

        <Card className="p-6 md:p-8 border border-border shadow-md">
          {serverError && (
            <div className="mb-6">
              <Banner type="danger" title={serverError} />
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <TextField
              label={en.auth.fullNameLabel}
              autoComplete="name"
              required
              errorMessage={errors.fullName?.message}
              {...register('fullName')}
            />

            <TextField
              label={en.auth.emailLabel}
              type="email"
              autoComplete="email"
              required
              errorMessage={errors.email?.message}
              {...register('email')}
            />

            <DatePicker
              label={en.auth.dobLabel}
              required
              helperText="You must be at least 14 years old to join FitFlow."
              errorMessage={errors.dateOfBirth?.message}
              {...register('dateOfBirth')}
            />

            <TextField
              label="Phone Number (Optional)"
              type="tel"
              autoComplete="tel"
              placeholder="+91 98765 43210"
              errorMessage={errors.phone?.message}
              {...register('phone')}
            />

            <PasswordField
              label={en.auth.passwordLabel}
              autoComplete="new-password"
              required
              helperText="Min 10 characters with at least one letter and one number."
              errorMessage={errors.password?.message}
              {...register('password')}
            />

            <Button
              type="submit"
              variant="primary"
              size="lg"
              className="w-full mt-4"
              disabled={isLoading}
              isLoading={isLoading}
              loadingText="Creating account..."
            >
              {en.auth.registerButton}
            </Button>
          </form>

          <div className="mt-6 pt-6 border-t border-border text-center text-xs text-text-secondary">
            <span>Already have an account? </span>
            <Link to="/login" className="text-brand-primary font-semibold hover:underline">
              Sign in
            </Link>
          </div>
        </Card>
      </div>
    </div>
  );
};
