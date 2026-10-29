import { describe, it, expect } from 'vitest';
import { parseApiError, getFriendlyErrorMessage, getRecoveryAction } from '../errors';
import en from '../../i18n/en.json';

describe('Error Mapping & RFC 7807 problem+json parsing', () => {
  it('maps every backend error code defined in copy deck correctly', () => {
    const errorCodes = Object.keys(en.errors) as (keyof typeof en.errors)[];

    for (const code of errorCodes) {
      const friendly = getFriendlyErrorMessage(code);
      expect(friendly).toBe(en.errors[code]);
    }
  });

  it('provides recovery action for UPGRADE_REQUIRED', () => {
    const action = getRecoveryAction('UPGRADE_REQUIRED');
    expect(action).toBeDefined();
    expect(action?.route).toBe('/app/membership');
    expect(action?.label).toContain('Membership');
  });

  it('provides recovery action for SESSION_FULL', () => {
    const action = getRecoveryAction('SESSION_FULL');
    expect(action).toBeDefined();
    expect(action?.route).toBe('/app/classes');
  });

  it('provides recovery action for UNAUTHENTICATED', () => {
    const action = getRecoveryAction('UNAUTHENTICATED');
    expect(action).toBeDefined();
    expect(action?.route).toBe('/login');
  });

  it('parses standard problem+json structure with status, code, and errors array', () => {
    const problem = {
      status: 400,
      code: 'VALIDATION_FAILED',
      title: 'Validation Failed',
      detail: 'Please fix fields',
      errors: [
        { field: 'email', message: 'Email is invalid' },
        { field: 'dateOfBirth', message: 'Must be 14+' },
      ],
      traceId: 'trace-12345-abcde',
    };

    const parsed = parseApiError(problem, 400);
    expect(parsed.status).toBe(400);
    expect(parsed.code).toBe('VALIDATION_FAILED');
    expect(parsed.message).toBe(en.errors.VALIDATION_FAILED);
    expect(parsed.errors).toHaveLength(2);
    expect(parsed.errors[0].field).toBe('email');
    expect(parsed.traceId).toBe('trace-12345-abcde');
  });

  it('extracts retryAfter header value from response', () => {
    const problem = {
      status: 429,
      code: 'RATE_LIMITED',
      retryAfter: 60,
    };

    const parsed = parseApiError(problem, 429);
    expect(parsed.retryAfter).toBe(60);
  });

  it('falls back gracefully to INTERNAL_ERROR on empty or null response', () => {
    const parsed = parseApiError(null, 500);
    expect(parsed.status).toBe(500);
    expect(parsed.code).toBe('INTERNAL_ERROR');
    expect(parsed.message).toBe(en.errors.INTERNAL_ERROR);
  });
});
