import { execSync } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const frontendDir = path.resolve(__dirname, '..');

console.log('Running API drift check...');

try {
  // 1. Run openapi-typescript
  execSync('npm run gen:api', { cwd: frontendDir, stdio: 'inherit' });

  // 2. Check git status on src/api/schema.d.ts
  const diff = execSync('git diff --name-only src/api/schema.d.ts', {
    cwd: frontendDir,
    encoding: 'utf8',
  }).trim();

  if (diff) {
    console.error('ERROR: src/api/schema.d.ts has drifted from docs/api/openapi.json!');
    console.error('Please run "npm run gen:api" and commit the updated schema file.');
    process.exit(1);
  }

  console.log('SUCCESS: API schema is up to date with zero drift.');
} catch (err) {
  console.error('API drift check failed:', err.message);
  process.exit(1);
}
