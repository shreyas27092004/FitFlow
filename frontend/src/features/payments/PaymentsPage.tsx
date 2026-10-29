import React, { useState } from 'react';
import { Button } from '../../components/Button';
import { Badge } from '../../components/Badge';
import { Table, TableColumn } from '../../components/Table';
import { Dialog } from '../../components/Dialog';
import { EmptyState } from '../../components/EmptyState';
import { Skeleton } from '../../components/Skeleton';
import {
  useGetPaymentsQuery,
  useGetInvoiceByIdQuery,
  PaymentDto,
} from '../../api/paymentsApi';
import { formatPaise } from '../../lib/money';
import { formatDateTime } from '../../lib/dates';
import { FileText } from 'lucide-react';
import en from '../../i18n/en.json';

export const PaymentsPage: React.FC = () => {
  const { data, isLoading } = useGetPaymentsQuery();
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(null);

  const { data: invoice, isLoading: isInvoiceLoading } = useGetInvoiceByIdQuery(
    selectedInvoiceId || '',
    { skip: !selectedInvoiceId }
  );

  const payments = data?.content || [];

  const columns: TableColumn<PaymentDto>[] = [
    {
      key: 'createdAt',
      header: 'Date & Time',
      render: (row) => <span>{formatDateTime(row.createdAt)}</span>,
    },
    {
      key: 'amountPaise',
      header: 'Amount',
      render: (row) => (
        <span className="font-semibold text-text-primary">
          {formatPaise(row.amountPaise)}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (row) => (
        <Badge
          status={
            row.status === 'SUCCEEDED'
              ? 'SUCCEEDED'
              : row.status === 'FAILED'
              ? 'FAILED'
              : 'PENDING'
          }
        />
      ),
    },
    {
      key: 'gateway',
      header: 'Method',
      render: (row) => (
        <span className="text-xs uppercase font-mono text-text-muted">
          {row.gateway}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Invoice',
      render: (row) =>
        row.invoiceId ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setSelectedInvoiceId(row.invoiceId!)}
            leftIcon={<FileText className="w-3.5 h-3.5" />}
          >
            {row.invoiceNumber || 'View Invoice'}
          </Button>
        ) : (
          <span className="text-xs text-text-muted italic">N/A</span>
        ),
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">Payments & Invoices</h1>
        <p className="text-sm text-text-secondary mt-1">
          Review all subscription transactions, membership receipts, and GST invoices.
        </p>
      </div>

      {isLoading ? (
        <Skeleton variant="card" className="h-64" />
      ) : payments.length > 0 ? (
        <Table<PaymentDto>
          caption="Member payment history and invoices"
          columns={columns}
          data={payments}
        />
      ) : (
        <EmptyState
          title="No past payments found"
          description={en.empty.invoices}
          actionLabel="View Membership Plans"
          onAction={() => {
            window.location.href = '/app/membership';
          }}
        />
      )}

      {/* Invoice Detail Modal */}
      {selectedInvoiceId && (
        <Dialog
          isOpen={Boolean(selectedInvoiceId)}
          onClose={() => setSelectedInvoiceId(null)}
          title={`Invoice: ${invoice?.invoiceNumber || selectedInvoiceId}`}
          maxWidth="md"
          footerButtons={
            <Button
              variant="outline"
              size="sm"
              onClick={() => setSelectedInvoiceId(null)}
            >
              Close
            </Button>
          }
        >
          {isInvoiceLoading ? (
            <div className="space-y-3 py-4">
              <Skeleton variant="text" />
              <Skeleton variant="text" />
              <Skeleton variant="card" className="h-24" />
            </div>
          ) : invoice ? (
            <div className="space-y-4 text-sm">
              <div className="flex justify-between pb-3 border-b border-border">
                <div>
                  <span className="text-xs text-text-muted block">Status</span>
                  <Badge status={invoice.status === 'PAID' ? 'SUCCEEDED' : 'CANCELLED'}>
                    {invoice.status}
                  </Badge>
                </div>
                <div className="text-right">
                  <span className="text-xs text-text-muted block">Invoice Date</span>
                  <strong>{formatDateTime(invoice.createdAt)}</strong>
                </div>
              </div>

              <div>
                <h4 className="text-xs font-semibold uppercase tracking-wider text-text-muted mb-2">
                  Line Items
                </h4>
                <div className="p-3 bg-bg-subtle rounded-md space-y-2">
                  {invoice.lines.map((line, idx) => (
                    <div key={idx} className="flex justify-between text-xs">
                      <span>{line.description}</span>
                      <strong>{formatPaise(line.amountPaise)}</strong>
                    </div>
                  ))}
                  <div className="pt-2 border-t border-border flex justify-between text-xs text-text-muted">
                    <span>Tax (GST):</span>
                    <span>{formatPaise(invoice.taxPaise)}</span>
                  </div>
                  <div className="pt-2 border-t border-border flex justify-between text-base font-bold text-text-primary">
                    <span>Total Amount:</span>
                    <span className="text-brand-primary">{formatPaise(invoice.totalPaise)}</span>
                  </div>
                </div>
              </div>
            </div>
          ) : (
            <p className="text-sm text-text-muted py-4">Could not load invoice details.</p>
          )}
        </Dialog>
      )}
    </div>
  );
};
