import { useState, useCallback, useRef } from 'react';

/**
 * Generate a cryptographically random UUID v4 idempotency key
 */
export function generateIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Hook providing a stable idempotency key for an action attempt.
 * Calling resetKey() creates a new key (e.g. when form inputs change or after a successful submit).
 */
export function useIdempotencyKey(): {
  idempotencyKey: string;
  resetKey: () => string;
} {
  const [key, setKey] = useState<string>(() => generateIdempotencyKey());
  const keyRef = useRef<string>(key);
  keyRef.current = key;

  const resetKey = useCallback(() => {
    const next = generateIdempotencyKey();
    setKey(next);
    return next;
  }, []);

  return { idempotencyKey: key, resetKey };
}
