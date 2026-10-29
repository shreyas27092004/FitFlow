import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  getStoredThemePreference,
  setStoredThemePreference,
  applyTheme,
  initTheme,
} from '../theme';
import { subscribeToEvents } from '../sse';

describe('Theme and Client Utilities Suite', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
    document.documentElement.removeAttribute('data-theme');
  });

  // 1. getStoredThemePreference default
  it('getStoredThemePreference: returns system by default', () => {
    expect(getStoredThemePreference()).toBe('system');
  });

  // 2. setStoredThemePreference updates localStorage
  it('setStoredThemePreference: stores preference in localStorage', () => {
    setStoredThemePreference('dark');
    expect(localStorage.getItem('fitflow_theme_preference')).toBe('dark');
    expect(getStoredThemePreference()).toBe('dark');
  });

  // 3. applyTheme dark mode
  it('applyTheme: adds dark class and data-theme attribute for dark', () => {
    applyTheme('dark');
    expect(document.documentElement).toHaveClass('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  // 4. applyTheme light mode
  it('applyTheme: removes dark class and sets light attribute', () => {
    applyTheme('dark');
    expect(document.documentElement).toHaveClass('dark');

    applyTheme('light');
    expect(document.documentElement).not.toHaveClass('dark');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  // 5. initTheme applies stored preference
  it('initTheme: initializes DOM using stored theme preference', () => {
    localStorage.setItem('fitflow_theme_preference', 'dark');
    initTheme();
    expect(document.documentElement).toHaveClass('dark');
  });

  // 6. subscribeToEvents cleanup function
  it('subscribeToEvents: returns cleanup teardown function without error', () => {
    const onMessage = vi.fn();
    const unsubscribe = subscribeToEvents('/api/v1/events', { onMessage });

    expect(typeof unsubscribe).toBe('function');
    expect(() => unsubscribe()).not.toThrow();
  });
});
