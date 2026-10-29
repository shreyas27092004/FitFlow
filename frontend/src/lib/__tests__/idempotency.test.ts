import { describe, it, expect } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useIdempotencyKey, generateIdempotencyKey } from '../idempotency';

describe('useIdempotencyKey & generateIdempotencyKey', () => {
  it('generates a valid UUID v4 format string', () => {
    const key = generateIdempotencyKey();
    expect(key).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });

  it('keeps the idempotency key stable across component re-renders', () => {
    const { result, rerender } = renderHook(() => useIdempotencyKey());
    const initialKey = result.current.idempotencyKey;

    rerender();
    expect(result.current.idempotencyKey).toBe(initialKey);

    rerender();
    expect(result.current.idempotencyKey).toBe(initialKey);
  });

  it('generates a new unique key when resetKey is called', () => {
    const { result } = renderHook(() => useIdempotencyKey());
    const key1 = result.current.idempotencyKey;

    act(() => {
      result.current.resetKey();
    });

    const key2 = result.current.idempotencyKey;
    expect(key2).not.toBe(key1);
    expect(key2).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });
});
