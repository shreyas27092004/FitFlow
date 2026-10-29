#!/usr/bin/env node
/**
 * scripts/check-contrast.js
 * WCAG 2.2 AA Contrast Gate
 * Reads docs/design/tokens.json and validates color contrast across all semantic pairs in Light and Dark themes.
 */

const fs = require('fs');
const path = require('path');

const TOKENS_PATH = path.resolve(__dirname, '..', 'docs', 'design', 'tokens.json');

function hexToRgb(hex) {
  const cleanHex = hex.replace('#', '').trim();
  if (cleanHex.length === 3) {
    return [
      parseInt(cleanHex[0] + cleanHex[0], 16),
      parseInt(cleanHex[1] + cleanHex[1], 16),
      parseInt(cleanHex[2] + cleanHex[2], 16)
    ];
  }
  return [
    parseInt(cleanHex.substring(0, 2), 16),
    parseInt(cleanHex.substring(2, 4), 16),
    parseInt(cleanHex.substring(4, 6), 16)
  ];
}

function channelLuminance(val) {
  const s = val / 255;
  return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
}

function relativeLuminance(hex) {
  const [r, g, b] = hexToRgb(hex);
  return (
    0.2126 * channelLuminance(r) +
    0.7152 * channelLuminance(g) +
    0.0722 * channelLuminance(b)
  );
}

function calculateContrastRatio(foregroundHex, backgroundHex) {
  const l1 = relativeLuminance(foregroundHex);
  const l2 = relativeLuminance(backgroundHex);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

function pad(str, len, align = 'left') {
  str = String(str);
  if (str.length >= len) return str.slice(0, len);
  const space = ' '.repeat(len - str.length);
  return align === 'right' ? space + str : str + space;
}

function main() {
  console.log('=== FitFlow Design System WCAG 2.2 AA Contrast Verification ===\n');

  if (!fs.existsSync(TOKENS_PATH)) {
    console.error(`ERROR: Tokens file not found at ${TOKENS_PATH}`);
    process.exit(1);
  }

  const tokens = JSON.parse(fs.readFileSync(TOKENS_PATH, 'utf8'));
  const semantic = tokens.color.semantic;

  let allPassed = true;
  let totalChecked = 0;
  const rows = [];

  for (const theme of ['light', 'dark']) {
    const pairs = semantic[theme]?.pairs || [];
    for (const pair of pairs) {
      totalChecked++;
      const ratio = calculateContrastRatio(pair.text, pair.bg);
      const passed = ratio >= pair.target;
      if (!passed) allPassed = false;

      rows.push({
        theme: theme.toUpperCase(),
        name: pair.name,
        text: pair.text,
        bg: pair.bg,
        ratio: ratio.toFixed(2) + ':1',
        target: pair.target.toFixed(1) + ':1',
        status: passed ? 'PASS' : 'FAIL'
      });
    }
  }

  // Print Table
  console.log('+' + '-'.repeat(7) + '+' + '-'.repeat(44) + '+' + '-'.repeat(10) + '+' + '-'.repeat(10) + '+' + '-'.repeat(10) + '+' + '-'.repeat(9) + '+' + '-'.repeat(8) + '+');
  console.log('| ' + pad('Theme', 5) + ' | ' + pad('Element / Semantic Pair', 42) + ' | ' + pad('Text', 8) + ' | ' + pad('Bg', 8) + ' | ' + pad('Ratio', 8, 'right') + ' | ' + pad('Target', 7, 'right') + ' | ' + pad('Status', 6) + ' |');
  console.log('+' + '-'.repeat(7) + '+' + '-'.repeat(44) + '+' + '-'.repeat(10) + '+' + '-'.repeat(10) + '+' + '-'.repeat(10) + '+' + '-'.repeat(9) + '+' + '-'.repeat(8) + '+');

  for (const r of rows) {
    console.log(
      '| ' + pad(r.theme, 5) +
      ' | ' + pad(r.name, 42) +
      ' | ' + pad(r.text, 8) +
      ' | ' + pad(r.bg, 8) +
      ' | ' + pad(r.ratio, 8, 'right') +
      ' | ' + pad(r.target, 7, 'right') +
      ' | ' + pad(r.status, 6) + ' |'
    );
  }
  console.log('+' + '-'.repeat(7) + '+' + '-'.repeat(44) + '+' + '-'.repeat(10) + '+' + '-'.repeat(10) + '+' + '-'.repeat(10) + '+' + '-'.repeat(9) + '+' + '-'.repeat(8) + '+');

  console.log(`\nTotal pairs evaluated: ${totalChecked}`);
  if (!allPassed) {
    console.error('\nFAILED: One or more color token pairs do not satisfy WCAG 2.2 AA contrast requirements!');
    process.exit(1);
  }

  console.log('SUCCESS: All color token pairs meet or exceed WCAG 2.2 AA contrast standards (4.5:1 body, 3:0:1 UI)!\n');
  process.exit(0);
}

main();
