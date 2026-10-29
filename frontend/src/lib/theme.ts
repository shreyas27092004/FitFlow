/**
 * FitFlow Theme Management
 * Stores strictly the theme preference ("light" | "dark" | "system") in localStorage.
 * Security constraint: Never store tokens or personal data in browser storage!
 */

export type ThemePreference = 'light' | 'dark' | 'system';

const THEME_STORAGE_KEY = 'fitflow_theme_preference';

export function getStoredThemePreference(): ThemePreference {
  try {
    const val = localStorage.getItem(THEME_STORAGE_KEY);
    if (val === 'light' || val === 'dark' || val === 'system') {
      return val;
    }
  } catch {
    // localStorage might be unavailable
  }
  return 'system';
}

export function setStoredThemePreference(theme: ThemePreference): void {
  try {
    localStorage.setItem(THEME_STORAGE_KEY, theme);
  } catch {
    // Ignore storage errors
  }
  applyTheme(theme);
}

export function applyTheme(theme: ThemePreference): void {
  const isDark =
    theme === 'dark' ||
    (theme === 'system' &&
      typeof window !== 'undefined' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches);

  const root = document.documentElement;
  if (isDark) {
    root.setAttribute('data-theme', 'dark');
    root.classList.add('dark');
  } else {
    root.setAttribute('data-theme', 'light');
    root.classList.remove('dark');
  }
}

export function initTheme(): void {
  const pref = getStoredThemePreference();
  applyTheme(pref);

  // Listen to system changes if system
  if (typeof window !== 'undefined') {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
      if (getStoredThemePreference() === 'system') {
        applyTheme('system');
      }
    });
  }
}
