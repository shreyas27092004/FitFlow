import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// All screens expected for Stage 11
const STAGE_11_SCREEN_IDS = [
  // Public / Auth
  'public-landing',
  'public-login',
  'public-register',
  'public-forgot-password',
  'public-reset-password',
  'public-plans',
  'public-not-found',
  'public-forbidden',
  'public-session-expired',
  // Member Core
  'member-home-dashboard',
  'member-browse-classes',
  'member-class-details',
  'member-session-details',
  'member-my-bookings',
  'member-membership',
  'member-checkout',
  'member-payment-result',
  'member-payments-invoices',
  'member-account',
];

const routesFile = path.resolve(__dirname, '../src/app/routes.tsx');

if (!fs.existsSync(routesFile)) {
  console.error(`Routes file not found at: ${routesFile}`);
  process.exit(1);
}

const content = fs.readFileSync(routesFile, 'utf8');

const missingScreens = [];
for (const screenId of STAGE_11_SCREEN_IDS) {
  // Check if screenId is mentioned in the router definitions / metadata
  if (!content.includes(screenId)) {
    missingScreens.push(screenId);
  }
}

if (missingScreens.length > 0) {
  console.error('ERROR: Missing route mappings for the following Stage 11 screen IDs:');
  missingScreens.forEach((id) => console.error(`  - ${id}`));
  process.exit(1);
}

console.log(
  `SUCCESS: All ${STAGE_11_SCREEN_IDS.length} Stage 11 screen IDs are mapped in src/app/routes.tsx`
);
