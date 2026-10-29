import { baseApi } from './baseApi';

export interface BookingDto {
  id: string;
  sessionId: string;
  classTitle: string;
  category: string;
  trainerName: string;
  roomName: string;
  startsAt: string;
  endsAt: string;
  status: 'CONFIRMED' | 'WAITLISTED' | 'CANCELLED' | 'ATTENDED' | 'NO_SHOW';
  waitlistPosition?: number;
  bookedAt: string;
}

export interface BookSessionRequest {
  sessionId: string;
  idempotencyKey: string;
}

export const bookingsApi = baseApi.injectEndpoints({
  endpoints: (builder) => ({
    getMyBookings: builder.query<{ content: BookingDto[]; totalElements: number }, { status?: string; page?: number; size?: number } | void>({
      query: (params) => ({
        url: '/me/bookings',
        params: params || undefined,
      }),
      providesTags: ['Bookings'],
    }),

    bookSession: builder.mutation<BookingDto, BookSessionRequest>({
      query: ({ sessionId, idempotencyKey }) => ({
        url: '/bookings',
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        },
        body: { sessionId },
      }),
      invalidatesTags: ['Bookings', 'Sessions', 'Membership'],
    }),

    cancelBooking: builder.mutation<void, { bookingId: string; idempotencyKey?: string }>({
      query: ({ bookingId, idempotencyKey }) => ({
        url: `/bookings/${bookingId}`,
        method: 'DELETE',
        headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
      }),
      invalidatesTags: ['Bookings', 'Sessions', 'Membership'],
    }),
  }),
});

export const {
  useGetMyBookingsQuery,
  useBookSessionMutation,
  useCancelBookingMutation,
} = bookingsApi;
