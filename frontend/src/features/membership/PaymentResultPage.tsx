import React from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { Badge } from '../../components/Badge';
import { Skeleton } from '../../components/Skeleton';
import { useGetPaymentByIdQuery } from '../../api/paymentsApi';
import { formatPaise } from '../../lib/money';
import { formatDateTime } from '../../lib/dates';
import { CheckCircle2, AlertOctagon, ArrowRight } from 'lucide-react';

export const PaymentResultPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const paymentId = searchParams.get('paymentId');
  const fallbackStatus = searchParams.get('status');

  const { data: payment, isLoading } = useGetPaymentByIdQuery(paymentId || '', {
    skip: !paymentId,
  });

  const isSuccess = payment ? payment.status === 'SUCCEEDED' : fallbackStatus === 'SUCCEEDED';

  return (
    <div className="max-w-xl mx-auto py-8">
      <Card className="p-8 border border-border shadow-lg text-center">
        {isLoading ? (
          <div className="space-y-4">
            <Skeleton variant="avatar" className="mx-auto" />
            <Skeleton variant="text" className="w-48 mx-auto" />
            <Skeleton variant="card" className="h-32" />
          </div>
        ) : isSuccess ? (
          <div>
            <div className="w-16 h-16 rounded-full bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-300 flex items-center justify-center mx-auto mb-4">
              <CheckCircle2 className="w-10 h-10" aria-hidden="true" />
            </div>

            <h1 className="text-2xl font-black mb-2">Payment Succeeded!</h1>
            <p className="text-xs text-text-secondary max-w-sm mx-auto mb-6">
              Thank you for your payment. Your FitFlow studio membership has been updated and your session credits are ready to use.
            </p>

            {payment && (
              <div className="p-4 bg-bg-subtle rounded-lg text-left text-sm space-y-2 mb-6">
                <div className="flex justify-between">
                  <span className="text-text-secondary">Amount Paid:</span>
                  <strong className="text-text-primary">
                    {formatPaise(payment.amountPaise)}
                  </strong>
                </div>

                {payment.invoiceNumber && (
                  <div className="flex justify-between">
                    <span className="text-text-secondary">Invoice Number:</span>
                    <strong className="font-mono text-brand-primary">
                      {payment.invoiceNumber}
                    </strong>
                  </div>
                )}

                <div className="flex justify-between">
                  <span className="text-text-secondary">Transaction Status:</span>
                  <Badge status="SUCCEEDED">Verified</Badge>
                </div>

                <div className="flex justify-between text-xs text-text-muted pt-2 border-t border-border">
                  <span>Processed:</span>
                  <span>{formatDateTime(payment.createdAt)}</span>
                </div>
              </div>
            )}

            <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
              <Link to="/app/classes" className="w-full sm:w-auto">
                <Button variant="primary" size="md" className="w-full" rightIcon={<ArrowRight className="w-4 h-4" />}>
                  Book Your First Class
                </Button>
              </Link>
              <Link to="/app/payments" className="w-full sm:w-auto">
                <Button variant="outline" size="md" className="w-full">
                  View All Invoices
                </Button>
              </Link>
            </div>
          </div>
        ) : (
          <div>
            <div className="w-16 h-16 rounded-full bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300 flex items-center justify-center mx-auto mb-4">
              <AlertOctagon className="w-10 h-10" aria-hidden="true" />
            </div>

            <h1 className="text-2xl font-black mb-2">Payment Failed</h1>
            <p className="text-xs text-text-secondary max-w-sm mx-auto mb-6">
              {payment?.failureReason || 'Your transaction was not completed by the issuing bank.'}
            </p>

            <div className="flex items-center justify-center gap-3">
              <Link to="/app/membership">
                <Button variant="primary" size="md">
                  Return to Membership Plans
                </Button>
              </Link>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
};
