import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { PasswordField } from '../../components/PasswordField';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { Banner } from '../../components/Banner';
import { useChangePasswordMutation } from '../../api/authApi';
import { useAppDispatch } from '../../app/store';
import { updateUserProfile } from './authSlice';
import { ShieldAlert } from 'lucide-react';

const forcedSchema = z
  .object({
    currentPassword: z.string().min(1, 'Current password is required'),
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

type ForcedFormData = z.infer<typeof forcedSchema>;

export const ForcedPasswordChangePage: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [changePasswordMutation, { isLoading }] = useChangePasswordMutation();

  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForcedFormData>({
    resolver: zodResolver(forcedSchema),
  });

  const onSubmit = async (data: ForcedFormData) => {
    setServerError(null);
    try {
      await changePasswordMutation({
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      }).unwrap();

      dispatch(updateUserProfile({ mustChangePassword: false }));
      navigate('/app/dashboard', { replace: true });
    } catch (err: any) {
      setServerError(err.message || 'Failed to change password.');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-bg-page text-text-primary">
      <div className="w-full max-w-md">
        <div className="text-center mb-6">
          <div className="w-12 h-12 rounded-xl bg-amber-500/10 text-amber-600 flex items-center justify-center mx-auto mb-3">
            <ShieldAlert className="w-7 h-7" aria-hidden="true" />
          </div>
          <h1 className="text-xl font-bold">Password Change Required</h1>
          <p className="text-xs text-text-muted mt-1">
            An administrator created or reset your account. Please set a secure personal password to continue.
          </p>
        </div>

        <Card className="p-6 md:p-8 border border-border shadow-md">
          {serverError && (
            <div className="mb-6">
              <Banner type="danger" title={serverError} />
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <PasswordField
              label="Temporary Password"
              autoComplete="current-password"
              required
              errorMessage={errors.currentPassword?.message}
              {...register('currentPassword')}
            />

            <PasswordField
              label="New Password"
              autoComplete="new-password"
              required
              helperText="Min 10 characters with at least one letter and one number."
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
              className="w-full mt-4"
              disabled={isLoading}
              isLoading={isLoading}
              loadingText="Updating password..."
            >
              Set New Password & Continue
            </Button>
          </form>
        </Card>
      </div>
    </div>
  );
};
