import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setupStore } from '../../app/store';
import { membershipApi } from '../membershipApi';
import { setCredentials } from '../../features/auth/authSlice';

describe('Token Refresh Single-Flight & Parallel 401s', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('serializes 10 parallel 401s to trigger exactly one call to /auth/refresh', async () => {
    let refreshCallCount = 0;
    let endpointCallCount = 0;

    // Custom fetch mock simulating 401 on initial requests, 200 on refresh, and 200 on re-queries
    const globalFetch = vi.fn().mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const urlStr = typeof input === 'string' ? input : (input as Request).url;
      const headers = input instanceof Request ? input.headers : new Headers(init?.headers);

      if (urlStr.includes('/auth/refresh')) {
        refreshCallCount++;
        // Simulate real network delay for token refresh endpoint
        await new Promise((resolve) => setTimeout(resolve, 15));
        // Verify CSRF defense header
        expect(headers.get('X-Requested-With')).toBe('fitflow');

        return new Response(
          JSON.stringify({ accessToken: 'new-valid-refreshed-token' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }

      if (urlStr.includes('/me/membership')) {
        endpointCallCount++;
        await new Promise((resolve) => setTimeout(resolve, 5));
        const auth = headers.get('Authorization');

        // Initial requests fail with 401
        if (!auth || auth.includes('initial-stale-token')) {
          return new Response(
            JSON.stringify({ code: 'UNAUTHENTICATED', detail: 'Token expired' }),
            { status: 401, headers: { 'Content-Type': 'application/json' } }
          );
        }

        // Retried requests with refreshed token succeed
        return new Response(
          JSON.stringify({ id: 'mem_1', planName: 'Starter Flow' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        );
      }

      return new Response(JSON.stringify({}), { status: 200 });
    });

    globalThis.fetch = globalFetch;

    const store = setupStore();
    store.dispatch(
      setCredentials({
        accessToken: 'initial-stale-token',
        user: { id: 'usr_1', email: 'test@fitflow.test', fullName: 'Tester', roles: ['MEMBER'] },
      })
    );

    // Launch 10 parallel queries with distinct parameters to trigger separate HTTP requests
    const promises = Array.from({ length: 10 }).map((_, i) =>
      store.dispatch(membershipApi.endpoints.getMyMembership.initiate({ page: i } as any, { forceRefetch: true })).unwrap()
    );

    const results = await Promise.all(promises);

    // Assertions:
    // 1. All 10 queries succeeded
    expect(results).toHaveLength(10);
    expect(endpointCallCount).toBeGreaterThan(0);
    results.forEach((res) => {
      expect(res?.planName).toBe('Starter Flow');
    });

    // 2. Exactly ONE refresh request was made across all 10 parallel 401s!
    expect(refreshCallCount).toBe(1);

    // 3. Store's in-memory accessToken was updated to the refreshed token
    expect(store.getState().auth.accessToken).toBe('new-valid-refreshed-token');
  });

  it('clears state and sets sessionExpired when refresh fails', async () => {
    const globalFetch = vi.fn().mockImplementation(async (url: string) => {
      const urlStr = String(url);

      if (urlStr.includes('/auth/refresh')) {
        // Refresh token invalid or revoked
        return new Response(
          JSON.stringify({ code: 'UNAUTHORIZED', detail: 'Refresh token revoked' }),
          { status: 401, headers: { 'Content-Type': 'application/json' } }
        );
      }

      return new Response(
        JSON.stringify({ code: 'UNAUTHENTICATED' }),
        { status: 401, headers: { 'Content-Type': 'application/json' } }
      );
    });

    globalThis.fetch = globalFetch;

    const store = setupStore();
    store.dispatch(
      setCredentials({
        accessToken: 'stale-token',
        user: { id: 'usr_1', email: 'test@fitflow.test', fullName: 'Tester', roles: ['MEMBER'] },
      })
    );

    await store.dispatch(
      membershipApi.endpoints.getMyMembership.initiate(undefined, { forceRefetch: true })
    );

    // Session state should be cleared and sessionExpired set to true
    expect(store.getState().auth.accessToken).toBeNull();
    expect(store.getState().auth.isAuthenticated).toBe(false);
    expect(store.getState().auth.sessionExpired).toBe(true);
  });
});
