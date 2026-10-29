import { baseApi } from './baseApi';

export interface PlanDto {
  id: string;
  name: string;
  description: string;
  pricePaise: number;
  sessionsPerMonth: number;
  features?: string[];
  active: boolean;
}

export interface MembershipDto {
  id: string;
  planId: string;
  planName: string;
  status: 'ACTIVE' | 'CANCELLED' | 'EXPIRED' | 'PAST_DUE';
  startsAt: string;
  renewsAt: string;
  sessionsRemaining: number;
  sessionsTotal: number;
  freeSessionsRemaining?: number;
}

export interface ChangePreviewDto {
  targetPlanId: string;
  targetPlanName: string;
  targetPricePaise: number;
  creditPaise: number;
  amountDuePaise: number;
  refundPaise: number;
  usedRatio: number;
  unusedRatio: number;
  explanationLines: string[];
  cappedByCashPaise?: boolean | number;
}

export interface SubscribeRequest {
  planId: string;
  expectedAmountDuePaise?: number;
  idempotencyKey: string;
}

export interface ChangePlanRequest {
  targetPlanId: string;
  expectedAmountDuePaise?: number;
  idempotencyKey: string;
}

export interface SubscribeResponse {
  status: 'SUCCEEDED' | 'REQUIRES_PAYMENT';
  membershipId?: string;
  paymentId?: string;
}

export const membershipApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getPlans: builder.query<PlanDto[], void>({
      query: () => '/plans',
      providesTags: ['Membership'],
    }),

    getMyMembership: builder.query<MembershipDto | null, void>({
      query: () => '/me/membership',
      providesTags: ['Membership'],
    }),

    getChangePreview: builder.mutation<ChangePreviewDto, { targetPlanId: string }>({
      query: (body) => ({
        url: '/me/membership/change-preview',
        method: 'POST',
        body,
      }),
    }),

    subscribe: builder.mutation<SubscribeResponse, SubscribeRequest>({
      query: ({ planId, expectedAmountDuePaise, idempotencyKey }) => ({
        url: '/memberships/subscribe',
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: { planId, expectedAmountDuePaise },
      }),
      invalidatesTags: ['Membership', 'Payments'],
    }),

    changePlan: builder.mutation<SubscribeResponse, ChangePlanRequest>({
      query: ({ targetPlanId, expectedAmountDuePaise, idempotencyKey }) => ({
        url: '/memberships/change',
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: { targetPlanId, expectedAmountDuePaise },
      }),
      invalidatesTags: ['Membership', 'Payments'],
    }),

    cancelMembership: builder.mutation<void, { idempotencyKey: string }>({
      query: ({ idempotencyKey }) => ({
        url: '/memberships/cancel',
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
      }),
      invalidatesTags: ['Membership'],
    }),
  }),
});

export const {
  useGetPlansQuery,
  useGetMyMembershipQuery,
  useGetChangePreviewMutation,
  useSubscribeMutation,
  useChangePlanMutation,
  useCancelMembershipMutation,
} = membershipApi;
