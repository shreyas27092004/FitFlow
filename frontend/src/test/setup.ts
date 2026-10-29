import '@testing-library/jest-dom';
import axeCore from 'axe-core';
import vm from 'node:vm';

// In Node 24 + JSDOM, JSDOM defines its own webidl AbortSignal which Node's Request constructor rejects.
// We align AbortController/Signal across window and global with Node's native root classes:
try {
  const { AbortController: NodeAbortController, AbortSignal: NodeAbortSignal } =
    new vm.Script('({ AbortController, AbortSignal })').runInThisContext();

  window.AbortController = NodeAbortController;
  window.AbortSignal = NodeAbortSignal;
  (global as any).AbortController = NodeAbortController;
  (global as any).AbortSignal = NodeAbortSignal;
} catch {
  // Ignore in browser environment
}

// Mock matchMedia for jsdom
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});

// Mock ResizeObserver
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Polyfill scrollIntoView
window.HTMLElement.prototype.scrollIntoView = function () {};

/**
 * Universal accessibility assertion helper using axe-core
 */
export async function expectNoA11yViolations(container: HTMLElement) {
  const results = await axeCore.run(container, {
    rules: {
      'color-contrast': { enabled: false }, // JSDOM lacks real layout/rendering engine for contrast
    },
  });

  const seriousOrCritical = results.violations.filter(
    (v) => v.impact === 'serious' || v.impact === 'critical'
  );

  if (seriousOrCritical.length > 0) {
    const errorMessages = seriousOrCritical
      .map(
        (v) =>
          `[${v.impact?.toUpperCase()}] ${v.id}: ${v.description} (${v.helpUrl})\n` +
          v.nodes.map((n) => `  Target: ${n.target.join(' ')}\n  Summary: ${n.failureSummary}`).join('\n')
      )
      .join('\n\n');

    throw new Error(`Accessibility violations found:\n${errorMessages}`);
  }
}
