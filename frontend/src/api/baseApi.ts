import {
  createApi,
  fetchBaseQuery,
  BaseQueryFn,
  FetchArgs,
  FetchBaseQueryError,
} from '@reduxjs/toolkit/query/react';
import { RootState } from '../app/store';
import {
  updateAccessToken,
  setSessionExpired,
} from '../features/auth/authSlice';
import { parseApiError, AppError } from '../lib/errors';
import { refreshMutex } from './mutex';

// Base URL: resolves to absolute URL for Node/JSDOM testing and browser proxy compatibility
const baseUrl =
  typeof window !== 'undefined' &&
  window.location?.origin &&
  window.location.origin !== 'null' &&
  !window.location.origin.includes('about:')
    ? `${window.location.origin}/api/v1`
    : 'http://localhost:5173/api/v1';

const rawBaseQuery = fetchBaseQuery({
  baseUrl,
  prepareHeaders: (headers, { getState, endpoint }) => {
    // 1. Attach unique X-Request-Id UUID
    const requestId =
      typeof crypto !== 'undefined' && crypto.randomUUID
        ? crypto.randomUUID()
        : Math.random().toString(36).substring(2, 15);
    headers.set('X-Request-Id', requestId);

    // 2. Attach Authorization Bearer token from in-memory state
    const token = (getState() as RootState).auth?.accessToken;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }

    // 3. For auth endpoints or refresh, ensure credentials / custom header
    if (endpoint === 'refresh') {
      headers.set('X-Requested-With', 'fitflow');
    }

    return headers;
  },
  credentials: 'include', // Needed for HttpOnly refresh token cookie on /auth endpoints
});

const sanitizeApiSignal = (api: any) => {
  try {
    if (api?.signal && typeof Request !== 'undefined') {
      new Request('http://localhost', { signal: api.signal });
    }
    return api;
  } catch {
    return { ...api, signal: undefined };
  }
};

const baseQueryWithReauth: BaseQueryFn<
  string | FetchArgs,
  unknown,
  AppError
> = async (args, rawApi, extraOptions) => {
  const api = sanitizeApiSignal(rawApi);
  const url = typeof args === 'string' ? args : args.url;
  const isAuthEndpoint = url.includes('/auth/');

  let result = await rawBaseQuery(args, api, extraOptions);

  // If 401 Unauthorized and not an auth endpoint, serialize token refresh via mutex
  if (result.error && result.error.status === 401 && !isAuthEndpoint) {
    if (refreshMutex.isLocked()) {
      // Another refresh is already running: wait for it to unlock
      await refreshMutex.waitForUnlock();
      // Retry with new access token
      result = await rawBaseQuery(args, api, extraOptions);
    } else {
      // We are the first 401: acquire mutex and refresh
      await refreshMutex.acquire();
      try {
        const refreshResult = await rawBaseQuery(
          {
            url: '/auth/refresh',
            method: 'POST',
            headers: {
              'X-Requested-With': 'fitflow',
            },
          },
          api,
          extraOptions
        );

        if (refreshResult.data) {
          const data = refreshResult.data as { accessToken: string };
          api.dispatch(updateAccessToken(data.accessToken));
          // Retry original request with newly refreshed token
          result = await rawBaseQuery(args, api, extraOptions);
        } else {
          // Refresh failed: clear state and set session expired
          api.dispatch(setSessionExpired());
        }
      } finally {
        refreshMutex.release();
      }
    }
  }

  // Handle errors and map to typed AppError
  if (result.error) {
    const fetchErr = result.error as FetchBaseQueryError;
    const appError = parseApiError(fetchErr.data, typeof fetchErr.status === 'number' ? fetchErr.status : 500);
    return { error: appError };
  }

  return result;
};

export const baseApi = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithReauth,
  tagTypes: [
    'User',
    'Classes',
    'Sessions',
    'Bookings',
    'Membership',
    'Payments',
    'Invoices',
  ],
  endpoints: () => ({}),
});
