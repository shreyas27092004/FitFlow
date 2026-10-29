import { describe, it, expect } from 'vitest';
import { formatDateTime, formatDateOnly, formatTimeOnly } from '../dates';

describe('formatDateTime - Asia/Kolkata IST Date Helper', () => {
  it('formats UTC timestamp into Asia/Kolkata time with IST zone label', () => {
    // 2026-10-29T10:00:00Z + 5:30 -> 15:30 IST
    const formatted = formatDateTime('2026-10-29T10:00:00Z');
    expect(formatted).toContain('IST');
    expect(formatted).toContain('15:30');
  });

  it('handles boundary at 23:59 IST correctly without DST drift', () => {
    // 2026-10-29 23:59:00 IST is 2026-10-29T18:29:00Z
    const formatted = formatDateTime('2026-10-29T18:29:00Z');
    expect(formatted).toContain('23:59');
    expect(formatted).toContain('IST');

    // Exact 23:59 IST is 18:29 + 30m = 18:59:00Z
    const midnightEdge = formatDateTime('2026-10-29T18:29:00.000Z');
    expect(midnightEdge).toContain('IST');
  });

  it('handles boundary at 00:00 IST midnight correctly', () => {
    // 2026-10-30 00:00:00 IST is 2026-10-29T18:30:00Z
    const formatted = formatDateTime('2026-10-29T18:30:00Z');
    expect(formatted).toContain('30');
    expect(formatted).toContain('00:00');
    expect(formatted).toContain('IST');
  });

  it('formats date only correctly in IST', () => {
    const formatted = formatDateOnly('2026-10-29T18:30:00Z');
    expect(formatted).toContain('30');
  });

  it('formats time only with IST label', () => {
    const formatted = formatTimeOnly('2026-10-29T18:30:00Z');
    expect(formatted).toBe('00:00 IST');
  });

  it('returns empty string for null or empty input', () => {
    expect(formatDateTime('')).toBe('');
    expect(formatDateOnly('')).toBe('');
    expect(formatTimeOnly('')).toBe('');
  });
});
