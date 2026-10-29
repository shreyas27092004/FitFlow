import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { TextField } from '../../components/TextField';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { Banner } from '../../components/Banner';
import { useForgotPasswordMutation } from '../../api/authApi';
import { Dumbbell, ArrowLeft } from 'lucide-react';
import en from '../../i18n/en.json';

const forgotSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
});

type ForgotFormData = z.infer<typeof forgotSchema>;

export const ForgotPasswordPage: React.FC = () => {
  const [forgotMutation, { isLoading }] = useForgotPasswordMutation();
  const [submitted, setSubmitted] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotFormData>({
    resolver: zodResolver(forgotSchema),
  });

  const onSubmit = async (data: ForgotFormData) => {
    setServerError(null);
    try {
      await forgotMutation(data).unwrap();
      setSubmitted(true);
    } catch {
      // Security rule: Never reveal whether an email exists
      setSubmitted(true);
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
          <h1 className="text-xl font-bold">Reset your password</h1>
          <p className="text-xs text-text-muted mt-1">
            Enter your email to receive password reset instructions.
          </p>
        </div>

        <Card className="p-6 md:p-8 border border-border shadow-md">
          {submitted ? (
            <div className="text-center space-y-4">
              <Banner
                type="success"
                title="Check your inbox"
              >
                If that email address is registered with FitFlow, we have sent instructions to reset your password.
              </Banner>
              <Link to="/login" className="inline-block mt-4">
                <Button variant="outline" size="sm" leftIcon={<ArrowLeft className="w-4 h-4" />}>
                  Back to Sign In
                </Button>
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              {serverError && <Banner type="danger" title={serverError} />}

              <TextField
                label={en.auth.emailLabel}
                type="email"
                autoComplete="email"
                required
                errorMessage={errors.email?.message}
                {...register('email')}
              />

              <Button
                type="submit"
                variant="primary"
                size="lg"
                className="w-full mt-2"
                disabled={isLoading}
                isLoading={isLoading}
                loadingText="Sending reset link..."
              >
                Send Reset Link
              </Button>

              <div className="text-center pt-4">
                <Link
                  to="/login"
                  className="text-xs text-brand-primary hover:underline font-medium inline-flex items-center gap-1"
                >
                  <ArrowLeft className="w-3.5 h-3.5" aria-hidden="true" />
                  <span>Back to Sign In</span>
                </Link>
              </div>
            </form>
          )}
        </Card>
      </div>
    </div>
  );
};
