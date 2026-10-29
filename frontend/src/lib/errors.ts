import en from '../i18n/en.json';

export interface FieldError {
  field?: string;
  message: string;
}

export interface AppError {
  status: number;
  code: string;
  message: string;
  detail?: string;
  errors: FieldError[];
  traceId?: string;
  retryAfter?: number;
  recoveryAction?: {
    label: string;
    route?: string;
  };
}

/**
 * Maps backend error codes to user-friendly messages from the copy deck
 */
export function getFriendlyErrorMessage(code: string, fallbackMessage?: string): string {
  const codeKey = code as keyof typeof en.errors;
  if (en.errors[codeKey]) {
    return en.errors[codeKey];
  }
  return fallbackMessage || en.errors.INTERNAL_ERROR;
}

/**
 * Determines appropriate recovery actions based on error code
 */
export function getRecoveryAction(
  code: string
): { label: string; route?: string } | undefined {
  switch (code) {
    case 'UPGRADE_REQUIRED':
      return { label: 'View Membership Plans', route: '/app/membership' };
    case 'SESSION_FULL':
      return { label: 'Browse Other Sessions', route: '/app/classes' };
    case 'UNAUTHENTICATED':
      return { label: 'Sign In', route: '/login' };
    case 'CONSENT_REQUIRED':
      return { label: 'Review Wellness Consent', route: '/app/wellness/consent' };
    default:
      return undefined;
  }
}

/**
 * Parses a fetch or RTK Query error response into a typed AppError
 */
export function parseApiError(response: any, status: number = 500): AppError {
  if (!response) {
    return {
      status,
      code: 'INTERNAL_ERROR',
      message: en.errors.INTERNAL_ERROR,
      errors: [],
    };
  }

  const code = response.code || response.title || (status === 401 ? 'UNAUTHENTICATED' : status === 403 ? 'FORBIDDEN' : 'INTERNAL_ERROR');
  const traceId = response.traceId || response['X-Trace-Id'];
  const rawMessage = response.detail || response.message;
  const friendlyMessage = getFriendlyErrorMessage(code, rawMessage);
  const recoveryAction = getRecoveryAction(code);

  let errors: FieldError[] = [];
  if (Array.isArray(response.errors)) {
    errors = response.errors.map((e: any) =>
      typeof e === 'string' ? { message: e } : { field: e.field, message: e.message || e.defaultMessage || 'Invalid field' }
    );
  }

  const retryAfter = response.retryAfter !== undefined ? Number(response.retryAfter) : undefined;

  return {
    status,
    code,
    message: friendlyMessage,
    detail: rawMessage,
    errors,
    traceId,
    retryAfter,
    recoveryAction,
  };
}
