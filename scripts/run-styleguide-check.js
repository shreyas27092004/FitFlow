/**
 * scripts/run-styleguide-check.js
 * Serves docs/design/styleguide locally, captures 4 screenshots (mobile/desktop, light/dark),
 * and runs an axe-core accessibility audit ensuring zero serious or critical violations.
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require('./node_modules/playwright');
const axeCore = require('./node_modules/axe-core');

const ROOT_DIR = path.resolve(__dirname, '..');
const STYLEGUIDE_DIR = path.join(ROOT_DIR, 'docs', 'design', 'styleguide');
const SCREENSHOTS_DIR = path.join(ROOT_DIR, 'docs', 'evidence', 'stage-10-screenshots');
const PORT = 3333;

if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

// 1. Simple static HTTP server
function startServer() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const urlPath = req.url.split('?')[0];
      let filePath = path.join(STYLEGUIDE_DIR, urlPath === '/' ? 'index.html' : urlPath);

      if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const ext = path.extname(filePath);
        const mimeTypes = {
          '.html': 'text/html',
          '.css': 'text/css',
          '.js': 'text/javascript',
          '.json': 'application/json'
        };
        res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'text/plain' });
        fs.createReadStream(filePath).pipe(res);
      } else {
        res.writeHead(404);
        res.end('Not Found');
      }
    });

    server.listen(PORT, () => {
      resolve(server);
    });
  });
}

async function main() {
  console.log('=== FitFlow Static Style Guide Verification & Accessibility Audit ===\n');

  const server = await startServer();
  console.log(`Local static server listening at http://localhost:${PORT}`);

  const browser = await chromium.launch({
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    headless: true
  });

  try {
    const context = await browser.newContext();
    const page = await context.newPage();

    // -------------------------------------------------------------------------
    // 1. Capture Screenshots
    // -------------------------------------------------------------------------
    console.log('\nCapturing screenshots at 375px (Mobile) and 1280px (Desktop)...');

    // Mobile Light
    await page.setViewportSize({ width: 375, height: 812 });
    await page.goto(`http://localhost:${PORT}/?theme=light`, { waitUntil: 'networkidle' });
    const mobileLightPath = path.join(SCREENSHOTS_DIR, 'mobile-light.png');
    await page.screenshot({ path: mobileLightPath, fullPage: false });
    console.log(`- Saved: ${mobileLightPath}`);

    // Mobile Dark
    await page.goto(`http://localhost:${PORT}/?theme=dark`, { waitUntil: 'networkidle' });
    const mobileDarkPath = path.join(SCREENSHOTS_DIR, 'mobile-dark.png');
    await page.screenshot({ path: mobileDarkPath, fullPage: false });
    console.log(`- Saved: ${mobileDarkPath}`);

    // Desktop Light
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(`http://localhost:${PORT}/?theme=light`, { waitUntil: 'networkidle' });
    const desktopLightPath = path.join(SCREENSHOTS_DIR, 'desktop-light.png');
    await page.screenshot({ path: desktopLightPath, fullPage: false });
    console.log(`- Saved: ${desktopLightPath}`);

    // Desktop Dark
    await page.goto(`http://localhost:${PORT}/?theme=dark`, { waitUntil: 'networkidle' });
    const desktopDarkPath = path.join(SCREENSHOTS_DIR, 'desktop-dark.png');
    await page.screenshot({ path: desktopDarkPath, fullPage: false });
    console.log(`- Saved: ${desktopDarkPath}`);

    // -------------------------------------------------------------------------
    // 2. Run axe-core Accessibility Audit
    // -------------------------------------------------------------------------
    console.log('\nRunning axe-core 4.x WCAG 2.2 AA accessibility evaluation...');

    // Inject axe-core
    await page.evaluate(axeCore.source);

    const axeResults = await page.evaluate(async () => {
      return await window.axe.run(document, {
        runOnly: {
          type: 'tag',
          values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']
        }
      });
    });

    const criticalViolations = axeResults.violations.filter(v => v.impact === 'critical');
    const seriousViolations = axeResults.violations.filter(v => v.impact === 'serious');
    const moderateViolations = axeResults.violations.filter(v => v.impact === 'moderate');
    const minorViolations = axeResults.violations.filter(v => v.impact === 'minor');

    console.log('----------------------------------------------------------------------');
    console.log(`axe-core rules evaluated   : ${axeResults.passes.length + axeResults.violations.length + axeResults.inapplicable.length}`);
    console.log(`Rules passed               : ${axeResults.passes.length}`);
    console.log(`Critical violations        : ${criticalViolations.length}`);
    console.log(`Serious violations         : ${seriousViolations.length}`);
    console.log(`Moderate violations        : ${moderateViolations.length}`);
    console.log(`Minor violations           : ${minorViolations.length}`);
    console.log('----------------------------------------------------------------------\n');

    if (criticalViolations.length > 0 || seriousViolations.length > 0) {
      console.error('FAILED: Found critical or serious accessibility violations:\n');
      [...criticalViolations, ...seriousViolations].forEach((v, idx) => {
        console.error(`[${idx + 1}] Rule: ${v.id} (${v.impact}) - ${v.description}`);
        console.error(`    Help: ${v.helpUrl}`);
        v.nodes.forEach(n => console.error(`    HTML: ${n.html}`));
      });
      process.exit(1);
    }

    console.log('SUCCESS: Style guide passed axe-core check with ZERO serious or critical violations!');
  } finally {
    await browser.close();
    server.close();
  }
}

main().catch(err => {
  console.error('Fatal error during style guide check:', err);
  process.exit(1);
});
