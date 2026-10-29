#!/usr/bin/env node
/**
 * scripts/check-screen-map.js
 * Anti-Drift Gate: Verifies docs/design/screen-endpoint-map.yml against docs/api/openapi.json
 *
 * Checks:
 * 1. Zero duplicate screen IDs
 * 2. Zero duplicate screen routes
 * 3. Every role must be strictly 'MEMBER', 'TRAINER', or 'ADMIN'
 * 4. Every endpoint in screen-endpoint-map.yml must exist in openapi.json
 */

const fs = require('fs');
const path = require('path');

const ROOT_DIR = path.resolve(__dirname, '..');
const OPENAPI_PATH = path.join(ROOT_DIR, 'docs', 'api', 'openapi.json');
const SCREEN_MAP_PATH = path.join(ROOT_DIR, 'docs', 'design', 'screen-endpoint-map.yml');

function parseSimpleYaml(content) {
  // If js-yaml is available, use it; otherwise parse our structured YAML
  try {
    const yaml = require('js-yaml');
    return yaml.load(content);
  } catch (_) {
    // Built-in parser for our clean YAML schema
    const lines = content.split('\n');
    const screens = [];
    let currentScreen = null;
    let currentListKey = null;

    for (let rawLine of lines) {
      const line = rawLine.replace(/#.*$/, ''); // strip comments
      const trimmed = line.trim();
      if (!trimmed) continue;

      if (trimmed.startsWith('- id:')) {
        if (currentScreen) screens.push(currentScreen);
        currentScreen = {
          id: trimmed.replace('- id:', '').trim(),
          roles: [],
          endpoints: [],
          states: [],
          notes: ''
        };
        currentListKey = null;
        continue;
      }

      if (!currentScreen) continue;

      if (trimmed.startsWith('route:')) {
        currentScreen.route = trimmed.replace('route:', '').trim();
        currentListKey = null;
      } else if (trimmed.startsWith('notes:')) {
        currentScreen.notes = trimmed.replace('notes:', '').trim();
        currentListKey = null;
      } else if (trimmed === 'roles:') {
        currentListKey = 'roles';
      } else if (trimmed.startsWith('endpoints:')) {
        currentListKey = 'endpoints';
        // Handle inline empty list "endpoints: []"
        if (trimmed.includes('[]')) {
          currentScreen.endpoints = [];
          currentListKey = null;
        }
      } else if (trimmed === 'states:') {
        currentListKey = 'states';
      } else if (trimmed.startsWith('- ') && currentListKey) {
        const itemVal = trimmed.replace(/^- /, '').trim();
        currentScreen[currentListKey].push(itemVal);
      }
    }
    if (currentScreen) screens.push(currentScreen);
    return { screens };
  }
}

function main() {
  console.log('=== FitFlow Screen-to-Endpoint Map Verification ===\n');

  if (!fs.existsSync(OPENAPI_PATH)) {
    console.error(`ERROR: OpenAPI spec file not found at ${OPENAPI_PATH}`);
    console.error('Run scripts/export-openapi.ps1 or scripts/export-openapi.sh first.');
    process.exit(1);
  }

  if (!fs.existsSync(SCREEN_MAP_PATH)) {
    console.error(`ERROR: Screen map file not found at ${SCREEN_MAP_PATH}`);
    process.exit(1);
  }

  // 1. Load OpenAPI endpoints
  const openapiRaw = fs.readFileSync(OPENAPI_PATH, 'utf8').replace(/^\uFEFF/, '');
  const openapi = JSON.parse(openapiRaw);
  const openapiEndpoints = new Set();

  if (openapi.paths) {
    for (const [pathKey, methods] of Object.entries(openapi.paths)) {
      for (const method of Object.keys(methods)) {
        openapiEndpoints.add(`${method.toUpperCase()} ${pathKey}`);
      }
    }
  }
  console.log(`Loaded ${openapiEndpoints.size} unique endpoints from OpenAPI document.`);

  // 2. Load Screen Map
  const mapContent = fs.readFileSync(SCREEN_MAP_PATH, 'utf8');
  const screenMap = parseSimpleYaml(mapContent);
  const screens = screenMap.screens || [];
  console.log(`Loaded ${screens.length} screens from screen-endpoint-map.yml.\n`);

  const ALLOWED_ROLES = new Set(['MEMBER', 'TRAINER', 'ADMIN']);
  const seenIds = new Set();
  const seenRoutes = new Set();
  const errors = [];
  let totalEndpointsChecked = 0;

  for (const screen of screens) {
    const screenId = screen.id;

    // Check 1: Duplicate Screen ID
    if (!screenId) {
      errors.push(`Screen missing 'id' property.`);
    } else if (seenIds.has(screenId)) {
      errors.push(`DUPLICATE SCREEN ID: '${screenId}' is defined more than once.`);
    } else {
      seenIds.add(screenId);
    }

    // Check 2: Duplicate Screen Route
    const route = screen.route;
    if (!route) {
      errors.push(`Screen '${screenId}' is missing 'route' property.`);
    } else if (seenRoutes.has(route)) {
      errors.push(`DUPLICATE ROUTE: '${route}' in screen '${screenId}' is already used by another screen.`);
    } else {
      seenRoutes.add(route);
    }

    // Check 3: Role Validation
    if (!screen.roles || !Array.isArray(screen.roles) || screen.roles.length === 0) {
      errors.push(`Screen '${screenId}' has no roles defined.`);
    } else {
      for (const role of screen.roles) {
        if (!ALLOWED_ROLES.has(role)) {
          errors.push(`INVALID ROLE: Role '${role}' in screen '${screenId}' is not allowed. Must be MEMBER, TRAINER, or ADMIN.`);
        }
      }
    }

    // Check 4: Endpoint existence in OpenAPI document
    if (screen.endpoints && Array.isArray(screen.endpoints)) {
      for (const ep of screen.endpoints) {
        totalEndpointsChecked++;
        if (!openapiEndpoints.has(ep)) {
          errors.push(`MISSING ENDPOINT: Screen '${screenId}' references '${ep}' which does not exist in openapi.json.`);
        }
      }
    }
  }

  // Print results
  console.log('----------------------------------------------------------------------');
  console.log(`Screens validated          : ${screens.length}`);
  console.log(`Unique routes verified     : ${seenRoutes.size}`);
  console.log(`Endpoint references checked: ${totalEndpointsChecked}`);
  console.log('----------------------------------------------------------------------\n');

  if (errors.length > 0) {
    console.error(`FAILED: Found ${errors.length} validation error(s):\n`);
    errors.forEach((err, idx) => {
      console.error(`  [${idx + 1}] ${err}`);
    });
    console.error('\nScreen map verification failed!');
    process.exit(1);
  }

  console.log('SUCCESS: All screens, routes, roles, and endpoints matched openapi.json with zero drift!');
  process.exit(0);
}

main();
