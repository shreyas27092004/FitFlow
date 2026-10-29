import { describe, it, expect } from 'vitest';
import { formatPaise, formatPaiseRaw } from '../money';

describe('formatPaise - Integer Currency Helper', () => {
  it('formats zero paise correctly', () => {
    expect(formatPaise(0)).toBe('Rs 0.00');
    expect(formatPaiseRaw(0)).toBe('0.00');
  });

  it('formats single-digit paise vector: 1 -> Rs 0.01', () => {
    expect(formatPaise(1)).toBe('Rs 0.01');
    expect(formatPaiseRaw(1)).toBe('0.01');
  });

  it('formats sub-rupee paise vector: 99 -> Rs 0.99', () => {
    expect(formatPaise(99)).toBe('Rs 0.99');
    expect(formatPaiseRaw(99)).toBe('0.99');
  });

  it('formats exactly one rupee vector: 100 -> Rs 1.00', () => {
    expect(formatPaise(100)).toBe('Rs 1.00');
    expect(formatPaiseRaw(100)).toBe('1.00');
  });

  it('formats standard subscription vector: 49900 -> Rs 499.00', () => {
    expect(formatPaise(49900)).toBe('Rs 499.00');
    expect(formatPaiseRaw(49900)).toBe('499.00');
  });

  it('formats large enterprise vector: 100000000 -> Rs 1000000.00', () => {
    expect(formatPaise(100000000)).toBe('Rs 1000000.00');
    expect(formatPaiseRaw(100000000)).toBe('1000000.00');
  });

  it('formats negative paise amounts correctly (for refunds or adjustments)', () => {
    expect(formatPaise(-49900)).toBe('-Rs 499.00');
    expect(formatPaiseRaw(-49900)).toBe('-499.00');
    expect(formatPaise(-50)).toBe('-Rs 0.50');
  });

  it('throws an error if a floating-point number is passed to enforce integer math', () => {
    expect(() => formatPaise(499.5)).toThrowError(/requires an integer paise value/);
    expect(() => formatPaiseRaw(100.01)).toThrowError(/requires an integer paise value/);
  });
});
