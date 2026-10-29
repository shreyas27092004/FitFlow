# Stage 11 Verification & Evidence (docs/evidence/stage-11.md)

**Stage**: Stage 11 of 13 — Production-Quality React Application for Public & Member Screens (Core, Payments, Security)  
**Platform**: FitFlow Modular Monolith  
**Date**: 2026-10-30  
**Status**: COMPLETE (All verification gates passed)

---

## 1. Executive Summary

Stage 11 delivers a production-grade React 18 single-page application (`frontend/`) serving public visitors and studio members, interacting with the real Spring Boot modular monolith backend.

Key accomplishments:
1. **Backend Secure Cookie Auth (Part A)**: Implemented `ff_refresh` cookie (`HttpOnly; SameSite=Strict; Path=/api/v1/auth; Max-Age=TTL; Secure`). Browser requests never receive the refresh token in JSON bodies. Cookie refresh requires CSRF defense header `X-Requested-With: fitflow`. Dual mode preserved via `X-Client: api` for CLIs and automated scripts. CORS credentials allowlist restricted to origin `http://localhost:5173`.
2. **Frontend State & Token Architecture (Part B)**: Access token resides in-memory only (`authSlice.ts`); refresh token is completely inaccessible to browser JavaScript. Automatic token refresh is serialized via single-flight mutex (`RefreshMutex`) preventing parallel refresh races. Zero `localStorage` or `sessionStorage` token leakage.
3. **Design System & Components (Part C)**: Implemented all 29 reusable accessible design system components in `frontend/src/components/` using Radix UI primitives, Lucide React icons, and semantic Tailwind tokens.
4. **Member & Public Screens (Part D)**: Implemented all 19 Stage 11 screen IDs across Authentication, Onboarding, Member Dashboard, Class Browsing, Session Details, Spot Reservations, My Bookings, Waitlist, Studio Membership, Proration Previews, Checkout with Verbatim Numbers, MOCK Gateway Test Panel, Invoices, and Account Self-Service.
5. **Anti-Drift Verification**: `scripts/check-api-drift.js` verified zero OpenAPI drift. `scripts/check-screen-routes.js` verified all 19 Stage 11 screen IDs mapped.
6. **Unit & Integration Suite**: 158 tests passing across 18 test suites in Vitest with 100% green status.
7. **Four Sanity Break Checks**: Executed and verified deliberate breakage and restoration for:
   - Check 1: Mutex Serialization (10 parallel 401s -> exactly 1 refresh)
   - Check 2: Verbatim Numbers Rule (client-side arithmetic forbidden)
   - Check 3: MOCK Gateway Panel Isolation (never rendered in live/test RAZORPAY mode)
   - Check 4: Zero LocalStorage Token Storage (memory-only access token enforcement)
8. **Playwright End-to-End Suite**: All 6 Member Journeys passed across Desktop (1280x800) and Mobile (375x667) viewports (12/12 PASS) with 0 serious or critical axe-core accessibility violations. 8 full-page screenshots captured in `docs/evidence/stage-11-screenshots/`.
9. **Full Platform Verification**: Backend `mvn test` (471 tests) and `scripts/verify.ps1` (240 integration tests + live health `/actuator/health` UP) 100% green.

---

## 2. Pinned Technology Stack & Bundle Analysis

### 2.1 Dependencies (`frontend/package.json`)
- React `18.3.1` & React DOM `18.3.1`
- React Router DOM `6.27.0`
- Redux Toolkit `2.3.0` & React Redux `9.1.2`
- Tailwind CSS `3.4.14`
- Radix UI Primitives (Dialog `1.1.2`, Dropdown `2.1.2`, Select `2.1.2`, Tabs `1.1.1`, Toast `1.2.2`, Checkbox `1.1.2`, RadioGroup `1.2.1`, Switch `1.1.1`, Tooltip `1.1.3`)
- Lucide React `0.454.0`
- React Hook Form `7.53.1` & Zod `3.23.8`
- Vitest `2.1.3` & Testing Library React `16.0.1`
- Playwright `1.48.2` & `@axe-core/playwright` `4.10.0`
- OpenAPI TypeScript `7.4.1`

### 2.2 Production Bundle Size (`npm run build`)
```
dist/index.html                   1.02 kB │ gzip:   0.54 kB
dist/assets/index-C4EM_ef3.css   39.33 kB │ gzip:   7.65 kB
dist/assets/index-B7-iZw6h.js   574.01 kB │ gzip: 172.05 kB
✓ built in 3.78s
```

---

## 3. Four Sanity Break Checks (Deliberate Breakage & Restoration)

### 3.1 Check 1: Single-Flight Refresh Mutex Serialization
- **Verification Rule**: 10 simultaneous 401 Unauthorized API responses must trigger exactly **one** call to `POST /api/v1/auth/refresh`. The remaining 9 calls must await the single lock and replay with the acquired access token.
- **Deliberate Breakage**: Bypassed the mutex lock in `baseApi.ts` so all 10 requests independently called `rawBaseQuery('/auth/refresh')`.
- **Observed Test Failure**:
  ```
  AssertionError: expected 10 to be 1 // Object.is equality
  - Expected: 1
  + Received: 10
    at src/api/__tests__/tokenRefresh.test.ts:72:32
  ```
- **Restoration**: Restored `refreshMutex.acquire()`, `waitForUnlock()`, and `release()`. Test passed cleanly with exactly 1 refresh call and 10 retried queries.

### 3.2 Check 2: Verbatim Numbers Rule (No Client Floating-Point Math)
- **Verification Rule**: CheckoutLauncher and preview modals must render backend-calculated amounts verbatim. Client-side arithmetic (e.g. `price - credit`) is strictly prohibited.
- **Deliberate Breakage**: Injected client-side math into `CheckoutLauncher.tsx`:
  `const fakeAmount = preview.targetPricePaise - 34468;` displaying `formatPaise(fakeAmount)` instead of `preview.amountDuePaise`.
- **Observed Test Failure**:
  ```
  AssertionError: expected 'Rs 654.32' to include 'Rs 687.13'
  - Expected: "Rs 687.13"
  + Received: "Rs 654.32"
    at src/features/membership/__tests__/checkoutAndPreview.test.tsx:42:31
  ```
- **Restoration**: Restored direct rendering of `preview.amountDuePaise` verbatim from backend DTO.

### 3.3 Check 3: MOCK Gateway Test Panel Isolation
- **Verification Rule**: The MOCK gateway test triggers (`tok_ok`, `tok_fail`, `tok_timeout`) must **never** be rendered when `clientPayload.gateway === 'RAZORPAY'`.
- **Deliberate Breakage**: Forced the mock gateway panel to render unconditionally regardless of gateway mode (`gateway === 'RAZORPAY' || gateway === 'MOCK'`).
- **Observed Test Failure**:
  ```
  AssertionError: expected <div data-testid="mock-gateway-panel"> to be null
  - Expected: null
  + Received: HTMLDivElement
    at src/test/security.test.tsx:48:38
  ```
- **Restoration**: Restored strict guard `clientPayload?.gateway === 'MOCK'` preventing mock panels from rendering during real/test Razorpay checkouts.

### 3.4 Check 4: Zero LocalStorage Token Storage
- **Verification Rule**: Access token must reside in memory only. Neither `localStorage` nor `sessionStorage` may contain `accessToken`, `refreshToken`, or raw JWT credentials.
- **Deliberate Breakage**: Injected `localStorage.setItem('accessToken', 'mock-jwt-token')` into the login reducer action.
- **Observed Test Failure**:
  ```
  AssertionError: expected 'accessToken' not to match /token/i
  - Expected: not /token/i
  + Received: "accessToken"
    at src/test/security.test.tsx:28:35
  ```
- **Restoration**: Removed `localStorage` write. Token state resides exclusively in Redux in-memory state.

---

## 4. Frontend Unit & Integration Tests (158 Tests across 18 Suites)

Command: `npm run test`
```
 Test Files  18 passed (18)
      Tests  158 passed (158)
   Duration  8.48s
```

| Test Suite File | Tests | Focus Area | Status |
| :--- | :---: | :--- | :---: |
| `src/lib/__tests__/money.test.ts` | 8 | Integer paise currency math & `formatPaise` | PASS |
| `src/lib/__tests__/dates.test.ts` | 6 | UTC date-time formatting & time intervals | PASS |
| `src/lib/__tests__/errors.test.ts` | 7 | RFC 7807 problem+json error mapping & copy deck messages | PASS |
| `src/lib/__tests__/idempotency.test.ts` | 3 | UUIDv4 idempotency key hook & reset triggers | PASS |
| `src/lib/__tests__/themeAndSse.test.ts` | 6 | Theme toggle preference & SSE buffer parser | PASS |
| `src/components/__tests__/components.test.tsx` | 29 | Design system components (1 test per component) | PASS |
| `src/components/__tests__/formControls.test.tsx` | 16 | Form input states, validation feedback, accessibility | PASS |
| `src/components/__tests__/navigationComponents.test.tsx` | 14 | Responsive navigation (NavBar, SideNav, BottomTabs) | PASS |
| `src/components/__tests__/cardAndDisplayComponents.test.tsx` | 15 | Cards, badges, tables, banners, skeletons, empty states | PASS |
| `src/api/__tests__/tokenRefresh.test.ts` | 2 | RefreshMutex single-flight serialization (10 parallel 401s) | PASS |
| `src/test/security.test.tsx` | 4 | No token leakage in web storage, MOCK panel isolation | PASS |
| `src/features/auth/__tests__/authScreens.test.tsx` | 10 | Login, Register, Forgot Password, Reset Password, Forced Change | PASS |
| `src/features/dashboard/__tests__/dashboardAndClasses.test.tsx` | 4 | Dashboard widgets, active membership card, catalog filters | PASS |
| `src/features/classes/__tests__/classAndSessionDetails.test.tsx` | 8 | Class schedule, room info, trainer bios, session safety | PASS |
| `src/features/bookings/__tests__/bookingFlows.test.tsx` | 8 | Booking reservation, 2h cutoff rule, cancel modal, waitlist | PASS |
| `src/features/membership/__tests__/checkoutAndPreview.test.tsx` | 5 | Prorated preview verbatim values, upgrade flow, mock checkout | PASS |
| `src/features/payments/__tests__/paymentsPage.test.tsx` | 8 | Payment history, invoice downloads, receipt preview | PASS |
| `src/features/account/__tests__/accountPage.test.tsx` | 5 | DPDP data export (JSON), account deletion with typed confirm | PASS |
| **Total** | **158** | **Comprehensive Frontend Coverage** | **100% PASS** |

---

## 5. Playwright End-to-End Member Journeys & Accessibility

Command: `npm run test:e2e`  
Configuration: Chromium Desktop (1280x800) and Chromium Mobile (375x667). 1 worker, 0 retries.  
Results: **12 passed (33.0s)**

### 5.1 Journey Matrix

| Journey ID | Description | Desktop Chrome (1280x800) | Mobile Chrome (375x667) | Axe-Core Violations (Serious/Critical) |
| :---: | :--- | :---: | :---: | :---: |
| **Journey 1** | Register account -> Sign in -> Browse class catalog -> View class details -> Book studio session -> View My Bookings -> Cancel reservation with 2-hour cutoff acknowledgment. | **PASS** (4.0s) | **PASS** (4.1s) | **0 violations** |
| **Journey 2** | Exhaust session credits -> Attempt booking -> UPGRADE_REQUIRED prompt -> Click upgrade -> Pro-rated preview with verbatim amounts -> Pay via MOCK -> Payment Succeeded screen with invoice ID. | **PASS** (5.0s) | **PASS** (5.1s) | **0 violations** |
| **Journey 3** | Decline path in MOCK checkout (`tok_fail`) -> Simulated issuer decline banner -> Retry action. | **PASS** (732ms) | **PASS** (834ms) | **0 violations** |
| **Journey 4** | Stale preview conflict path (409) -> Banner warning "Membership Preview Updated" -> Refreshed preview -> Complete payment successfully. | **PASS** (3.7s) | **PASS** (3.7s) | **0 violations** |
| **Journey 5** | Forced password change (`mustChangePassword: true`) -> Forced change screen -> Set new password -> Land on dashboard. | **PASS** (932ms) | **PASS** (894ms) | **0 violations** |
| **Journey 6** | Automatic silent refresh via `ff_refresh` cookie -> Navigation -> Sign out with cookie revocation. | **PASS** (456ms) | **PASS** (463ms) | **0 violations** |

### 5.2 Evidence Screenshots Captured (`docs/evidence/stage-11-screenshots/`)
1. `desktop-dashboard.png` (99 KB): Member Dashboard at 1280x800
2. `desktop-classes.png` (66 KB): Class Catalog at 1280x800
3. `desktop-checkout.png` (105 KB): Pro-Rated Checkout Modal at 1280x800
4. `desktop-bookings.png` (66 KB): My Bookings at 1280x800
5. `mobile-dashboard.png` (353 KB): Member Dashboard at 375x667
6. `mobile-classes.png` (235 KB): Class Catalog at 375x667
7. `mobile-checkout.png` (205 KB): Pro-Rated Checkout Modal at 375x667
8. `mobile-bookings.png` (198 KB): My Bookings at 375x667

---

## 6. Full Platform Verification Summary

| Gate | Command | Scope | Result |
| :--- | :--- | :--- | :---: |
| **Backend Unit Tests** | `mvn test` (backend) | 471 unit tests across all domain packages | **PASS** (471/471) |
| **Backend Verify Suite** | `powershell scripts/verify.ps1` | MySQL 3306, PG 5432, RabbitMQ 5672, Jacoco, ArchUnit, 240 ITs, Live Dev Health | **PASS** |
| **Frontend Lint** | `npm run lint` | ESLint strict `--max-warnings 0` | **PASS** (0 err, 0 warn) |
| **Frontend Typecheck** | `npm run typecheck` | TypeScript 5.6.3 compiler (`tsc --noEmit`) | **PASS** (0 errors) |
| **API Drift Check** | `npm run check:api-drift` | OpenAPI schema vs generated TypeScript types | **PASS** (zero drift) |
| **Screen Route Map** | `npm run check:screen-routes` | All 19 Stage 11 screen IDs mapped | **PASS** (19/19 mapped) |
| **Frontend Unit/IT** | `npm run test` | 158 tests across 18 Vitest suites | **PASS** (158/158) |
| **Frontend E2E Tests** | `npm run test:e2e` | 12 Playwright journeys + Axe-Core audits | **PASS** (12/12) |
| **Frontend Build** | `npm run build` | Vite production bundle compilation | **PASS** |

All Stage 11 verification gates are 100% GREEN.
