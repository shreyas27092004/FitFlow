import { baseApi } from './baseApi';

export interface PaymentDto {
  id: string;
  amountPaise: number;
  currency: string;
  status: 'PENDING' | 'SUCCEEDED' | 'FAILED';
  gateway: 'RAZORPAY' | 'MOCK';
  orderId?: string;
  paymentId?: string;
  invoiceId?: string;
  invoiceNumber?: string;
  failureReason?: string;
  createdAt: string;
}

export interface ClientPayloadDto {
  paymentId: string;
  gateway: 'RAZORPAY' | 'MOCK';
  keyId?: string;
  orderId?: string;
  amountPaise: number;
  currency: string;
  email?: string;
}

export interface ConfirmPaymentRequest {
  paymentId: string;
  razorpayPaymentId?: string;
  razorpayOrderId?: string;
  razorpaySignature?: string;
  mockToken?: 'tok_ok' | 'tok_fail' | 'tok_timeout';
}

export interface InvoiceDto {
  id: string;
  invoiceNumber: string;
  amountPaise: number;
  taxPaise: number;
  totalPaise: number;
  currency: string;
  status: 'PAID' | 'VOID' | 'REFUNDED';
  paidAt?: string;
  createdAt: string;
  lines: {
    description: string;
    amountPaise: number;
  }[];
}

export const paymentsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getPayments: builder.query<{ content: PaymentDto[]; totalElements: number }, { page?: number; size?: number } | void>({
      query: (params) => ({
        url: '/me/payments',
        params: params || undefined,
      }),
      providesTags: ['Payments'],
    }),

    getPaymentById: builder.query<PaymentDto, string>({
      query: (id) => `/payments/${id}`,
      providesTags: (_res, _err, id) => [{ type: 'Payments', id }],
    }),

    getClientPayload: builder.query<ClientPayloadDto, string>({
      query: (paymentId) => `/payments/${paymentId}/client-payload`,
    }),

    confirmPayment: builder.mutation<PaymentDto, ConfirmPaymentRequest>({
      query: ({ paymentId, ...body }) => ({
        url: `/payments/${paymentId}/confirm`,
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Payments', 'Membership'],
    }),

    getInvoiceById: builder.query<InvoiceDto, string>({
      query: (id) => `/me/invoices/${id}`,
      providesTags: (_res, _err, id) => [{ type: 'Invoices', id }],
    }),
  }),
});

export const {
  useGetPaymentsQuery,
  useGetPaymentByIdQuery,
  useGetClientPayloadQuery,
  useConfirmPaymentMutation,
  useGetInvoiceByIdQuery,
} = paymentsApi;
