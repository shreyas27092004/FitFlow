import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { Banner } from '../../components/Banner';
import { formatPaise } from '../../lib/money';
import { useIdempotencyKey } from '../../lib/idempotency';
import {
  useSubscribeMutation,
  useChangePlanMutation,
  ChangePreviewDto,
} from '../../api/membershipApi';
import {
  paymentsApi,
  useConfirmPaymentMutation,
  ClientPayloadDto,
} from '../../api/paymentsApi';
import { useAppDispatch } from '../../app/store';
import { Loader2, ShieldCheck, RefreshCw } from 'lucide-react';

declare global {
  interface Window {
    Razorpay?: any;
  }
}

export interface CheckoutLauncherProps {
  targetPlanId: string;
  targetPlanName: string;
  isNewSubscription?: boolean;
  preview: ChangePreviewDto;
  onClose: () => void;
  onRefreshPreview?: () => Promise<ChangePreviewDto | void>;
}

export type CheckoutStep =
  | 'INITIAL'
  | 'INITIATING'
  | 'STALE_PREVIEW'
  | 'CHECKOUT_OPEN'
  | 'DISMISSED'
  | 'POLLING'
  | 'POLL_TIMEOUT'
  | 'SUCCESS'
  | 'FAILED';

export const CheckoutLauncher: React.FC<CheckoutLauncherProps> = ({
  targetPlanId,
  targetPlanName,
  isNewSubscription = false,
  preview: initialPreview,
  onClose,
  onRefreshPreview,
}) => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { idempotencyKey, resetKey } = useIdempotencyKey();

  const [preview, setPreview] = useState<ChangePreviewDto>(initialPreview);
  const [step, setStep] = useState<CheckoutStep>('INITIAL');
  const [paymentId, setPaymentId] = useState<string | null>(null);
  const [clientPayload, setClientPayload] = useState<ClientPayloadDto | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [scriptLoadError, setScriptLoadError] = useState(false);

  const [subscribeMutation] = useSubscribeMutation();
  const [changePlanMutation] = useChangePlanMutation();
  const [confirmPaymentMutation, { isLoading: isConfirming }] = useConfirmPaymentMutation();

  const pollIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const pollCountRef = useRef(0);

  // Clear polling on unmount
  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
      }
    };
  }, []);

  // 1. Dynamically load https://checkout.razorpay.com/v1/checkout.js on demand
  const loadRazorpayScript = useCallback((): Promise<boolean> => {
    if (typeof window !== 'undefined' && window.Razorpay) {
      return Promise.resolve(true);
    }
    return new Promise((resolve) => {
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.async = true;
      script.onload = () => {
        setScriptLoadError(false);
        resolve(true);
      };
      script.onerror = () => {
        setScriptLoadError(true);
        resolve(false);
      };
      document.body.appendChild(script);
    });
  }, []);

  // Poll GET /payments/{paymentId} every 2 seconds up to 60 seconds (30 polls)
  const startPolling = useCallback(
    (id: string) => {
      setStep('POLLING');
      pollCountRef.current = 0;

      if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);

      pollIntervalRef.current = setInterval(async () => {
        pollCountRef.current += 1;

        try {
          const res = await dispatch(
            paymentsApi.endpoints.getPaymentById.initiate(id, { forceRefetch: true })
          ).unwrap();

          if (res.status === 'SUCCEEDED') {
            if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
            setStep('SUCCESS');
            navigate(`/app/membership/payment-result?paymentId=${id}&status=SUCCEEDED`);
          } else if (res.status === 'FAILED') {
            if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
            setStep('FAILED');
            setErrorMessage(res.failureReason || 'Payment failed or was declined by your bank.');
          } else if (pollCountRef.current >= 30) {
            // 60 seconds limit reached
            if (pollIntervalRef.current) clearInterval(pollIntervalRef.current);
            setStep('POLL_TIMEOUT');
          }
        } catch (err: any) {
          console.error('Polling error:', err);
        }
      }, 2000);
    },
    [dispatch, navigate]
  );

  // Open Razorpay Modal
  const openRazorpayCheckout = useCallback(
    (payload: ClientPayloadDto) => {
      if (!window.Razorpay) {
        setErrorMessage('Payment gateway failed to initialize.');
        return;
      }

      setStep('CHECKOUT_OPEN');

      const options = {
        key: payload.keyId,
        order_id: payload.orderId,
        amount: payload.amountPaise,
        currency: payload.currency,
        name: 'FitFlow',
        description: `FitFlow Membership - ${targetPlanName}`,
        prefill: {
          email: payload.email,
        },
        modal: {
          ondismiss: () => {
            setStep('DISMISSED');
          },
        },
        handler: async (response: {
          razorpay_payment_id: string;
          razorpay_order_id: string;
          razorpay_signature: string;
        }) => {
          try {
            await confirmPaymentMutation({
              paymentId: payload.paymentId,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpayOrderId: response.razorpay_order_id,
              razorpaySignature: response.razorpay_signature,
            }).unwrap();
            startPolling(payload.paymentId);
          } catch (err: any) {
            setStep('FAILED');
            setErrorMessage(err.message || 'Payment confirmation failed.');
          }
        },
      };

      const rzp = new window.Razorpay(options);
      rzp.open();
    },
    [confirmPaymentMutation, startPolling, targetPlanName]
  );

  // Execute subscription or change
  const handleProceed = async () => {
    setStep('INITIATING');
    setErrorMessage(null);

    try {
      let res;
      if (isNewSubscription) {
        res = await subscribeMutation({
          planId: targetPlanId,
          expectedAmountDuePaise: preview.amountDuePaise,
          idempotencyKey,
        }).unwrap();
      } else {
        res = await changePlanMutation({
          targetPlanId,
          expectedAmountDuePaise: preview.amountDuePaise,
          idempotencyKey,
        }).unwrap();
      }

      if (res.status === 'SUCCEEDED') {
        // No payment needed (e.g. 100% credit offset or refund)
        setStep('SUCCESS');
        navigate(
          `/app/membership/payment-result?status=SUCCEEDED${
            res.paymentId ? `&paymentId=${res.paymentId}` : ''
          }`
        );
        return;
      }

      if (res.status === 'REQUIRES_PAYMENT' && res.paymentId) {
        setPaymentId(res.paymentId);

        // Fetch client payload
        const payload = await dispatch(
          paymentsApi.endpoints.getClientPayload.initiate(res.paymentId, { forceRefetch: true })
        ).unwrap();

        setClientPayload(payload);

        if (payload.gateway === 'MOCK') {
          // In MOCK mode, stay on this screen to display the test buttons
          setStep('INITIAL');
        } else {
          // In real RAZORPAY mode, load Razorpay script and open modal
          const ok = await loadRazorpayScript();
          if (ok) {
            openRazorpayCheckout(payload);
          } else {
            setStep('INITIAL');
          }
        }
      }
    } catch (err: any) {
      if (err.status === 409 || err.code === 'CONFLICT' || err.code === 'STALE_PREVIEW') {
        setStep('STALE_PREVIEW');
        if (onRefreshPreview) {
          const fresh = await onRefreshPreview();
          if (fresh) setPreview(fresh);
        }
      } else {
        setStep('FAILED');
        setErrorMessage(err.detail || err.message || 'Unable to initiate membership plan change.');
      }
    }
  };

  // Mock Gateway Test Trigger (Success, Decline, Timeout)
  const handleMockAction = async (mockToken: 'tok_ok' | 'tok_fail' | 'tok_timeout') => {
    if (!paymentId) return;
    try {
      await confirmPaymentMutation({
        paymentId,
        mockToken,
      }).unwrap();
      startPolling(paymentId);
    } catch (err: any) {
      setStep('FAILED');
      setErrorMessage(err.detail || err.message || 'Mock payment confirmation failed.');
    }
  };

  // Retry with new idempotency key
  const handleRetry = () => {
    resetKey();
    setStep('INITIAL');
    setErrorMessage(null);
    setPaymentId(null);
    setClientPayload(null);
  };

  // Resume dismissed payment
  const handleResume = async () => {
    if (!paymentId) return;
    const payload = await dispatch(
      paymentsApi.endpoints.getClientPayload.initiate(paymentId, { forceRefetch: true })
    ).unwrap();
    setClientPayload(payload);
    if (payload.gateway === 'RAZORPAY') {
      openRazorpayCheckout(payload);
    }
  };

  return (
    <Card className="p-6 md:p-8 border border-border shadow-lg">
      <div className="flex items-center justify-between mb-4 pb-3 border-b border-border">
        <div>
          <h2 className="text-xl font-bold">Review Membership Change</h2>
          <p className="text-xs text-text-muted">Target: {targetPlanName}</p>
        </div>
        <span className="text-xs font-semibold px-2 py-0.5 rounded bg-brand-primary/10 text-brand-primary">
          Step 2 of 2
        </span>
      </div>

      {scriptLoadError && (
        <div className="mb-4">
          <Banner type="danger" title="Payment Gateway Load Error">
            Unable to connect to Razorpay secure checkout. Please verify your internet connection and try again.
            <div className="mt-2">
              <Button size="sm" variant="outline" onClick={() => loadRazorpayScript()}>
                Retry Loading
              </Button>
            </div>
          </Banner>
        </div>
      )}

      {errorMessage && (
        <div className="mb-4">
          <Banner type="danger" title={errorMessage} />
        </div>
      )}

      {step === 'STALE_PREVIEW' && (
        <div className="mb-6">
          <Banner
            type="warning"
            title="Membership Preview Updated (409 Conflict)"
          >
            The billing calculations changed because another transaction or studio credit usage was recorded. Please review the updated numbers below and re-confirm.
          </Banner>
        </div>
      )}

      {step === 'POLLING' && (
        <div className="text-center py-8 space-y-4">
          <Loader2 className="w-10 h-10 animate-spin text-brand-primary mx-auto" />
          <h3 className="text-lg font-bold">Payment received, finishing up...</h3>
          <p className="text-xs text-text-muted max-w-sm mx-auto">
            We are confirming the payment receipt with the issuing bank and updating your studio entitlement balance.
          </p>
        </div>
      )}

      {step === 'POLL_TIMEOUT' && (
        <div className="text-center py-6 space-y-4">
          <Banner type="info" title="Payment Confirmation in Progress">
            We are still confirming your payment with the banking gateway. You can safely leave this page; we will email your confirmation and invoice as soon as it clears.
          </Banner>
          <Button variant="outline" size="sm" onClick={() => navigate('/app/payments')}>
            View Payments & Invoices
          </Button>
        </div>
      )}

      {step === 'DISMISSED' && (
        <div className="mb-6 space-y-3">
          <Banner type="warning" title="Payment not completed">
            The Razorpay checkout window was dismissed. Your payment intent remains pending and valid.
          </Banner>
          <div className="flex gap-2">
            <Button size="sm" variant="primary" onClick={handleResume}>
              Resume Payment
            </Button>
            <Button size="sm" variant="outline" onClick={onClose}>
              Cancel
            </Button>
          </div>
        </div>
      )}

      {/* Render Price Details Verbatim from Backend Preview */}
      {step !== 'POLLING' && step !== 'POLL_TIMEOUT' && (
        <div className="space-y-4">
          <div className="p-4 bg-bg-subtle rounded-lg space-y-2.5 text-sm">
            <div className="flex justify-between">
              <span className="text-text-secondary">Plan Price:</span>
              <strong className="text-text-primary" data-testid="target-price">
                {formatPaise(preview.targetPricePaise)}
              </strong>
            </div>

            <div className="flex justify-between">
              <span className="text-text-secondary">Unused Credit Applied:</span>
              <span className="text-green-600 dark:text-green-400 font-semibold" data-testid="credit-applied">
                -{formatPaise(preview.creditPaise)}
              </span>
            </div>

            {preview.refundPaise > 0 && (
              <div className="flex justify-between">
                <span className="text-text-secondary">Refund Issued to Original Payment:</span>
                <span className="text-green-600 dark:text-green-400 font-bold" data-testid="refund-amount">
                  {formatPaise(preview.refundPaise)}
                </span>
              </div>
            )}

            <div className="pt-2 border-t border-border flex justify-between text-base">
              <span className="font-bold">Total Amount Due:</span>
              <span className="font-extrabold text-brand-primary" data-testid="amount-due">
                {formatPaise(preview.amountDuePaise)}
              </span>
            </div>

            {preview.cappedByCashPaise && (
              <p className="text-[11px] text-amber-700 dark:text-amber-300 italic pt-1">
                Note: Refund is capped at the total cash previously paid for this billing cycle.
              </p>
            )}
          </div>

          {/* Explanation Lines Verbatim */}
          {preview.explanationLines && preview.explanationLines.length > 0 && (
            <div className="text-xs text-text-secondary space-y-1">
              <span className="font-semibold text-text-primary block">Calculation Details:</span>
              <ul className="list-disc pl-4 space-y-0.5">
                {preview.explanationLines.map((line, idx) => (
                  <li key={idx}>{line}</li>
                ))}
              </ul>
            </div>
          )}

          {/* MOCK Gateway Test Panel - Strictly only renders when clientPayload?.gateway === 'MOCK' */}
          {clientPayload && clientPayload.gateway === 'MOCK' && (
            <div
              data-testid="mock-gateway-panel"
              className="p-4 rounded-lg border-2 border-dashed border-amber-400 bg-amber-50/60 dark:bg-amber-950/20 my-4"
            >
              <div className="flex items-center gap-2 text-amber-900 dark:text-amber-200 font-bold text-xs uppercase tracking-wider mb-2">
                <ShieldCheck className="w-4 h-4 text-amber-600" aria-hidden="true" />
                Test payment (no money moves)
              </div>
              <p className="text-xs text-text-secondary mb-3">
                Select a simulated payment result to test backend reconciliation:
              </p>
              <div className="flex flex-wrap gap-2">
                <Button
                  size="sm"
                  variant="primary"
                  onClick={() => handleMockAction('tok_ok')}
                  disabled={isConfirming}
                >
                  Success (tok_ok)
                </Button>
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() => handleMockAction('tok_fail')}
                  disabled={isConfirming}
                >
                  Decline (tok_fail)
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => handleMockAction('tok_timeout')}
                  disabled={isConfirming}
                >
                  Timeout (tok_timeout)
                </Button>
              </div>
            </div>
          )}

          {/* Bottom Actions */}
          <div className="flex items-center justify-end gap-3 pt-4 border-t border-border">
            <Button variant="outline" size="md" onClick={onClose} disabled={step === 'INITIATING'}>
              Cancel
            </Button>

            {step === 'FAILED' ? (
              <Button
                variant="primary"
                size="md"
                onClick={handleRetry}
                leftIcon={<RefreshCw className="w-4 h-4" />}
              >
                Try Again
              </Button>
            ) : !clientPayload || clientPayload.gateway !== 'MOCK' ? (
              <Button
                variant="primary"
                size="md"
                onClick={handleProceed}
                disabled={step === 'INITIATING'}
                isLoading={step === 'INITIATING'}
                loadingText="Securing payment..."
              >
                {step === 'STALE_PREVIEW'
                  ? 'Confirm & Pay Updated Amount'
                  : preview.amountDuePaise > 0
                  ? `Pay ${formatPaise(preview.amountDuePaise)}`
                  : 'Confirm Plan Change'}
              </Button>
            ) : null}
          </div>
        </div>
      )}
    </Card>
  );
};
