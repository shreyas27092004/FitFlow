# FitFlow Design System & UX Specification (docs/design/design-system.md)

This document establishes the comprehensive UI/UX design architecture for the FitFlow platform, unifying Member, Trainer, and Admin experiences within a single React 18 application.

---

## 1. Brand Identity, Tone & Microcopy

### 1.1 Brand Essence
- **Motivating yet Grounded**: Encourages physical progress without toxic fitness hustle or guilt-inducing pressure.
- **Calm & Reassuring**: Clean whitespace, clear typography, and thoughtful pacing reduce anxiety, especially around health assessments and financial transactions.
- **Friendly & Honest**: Plain language over fitness or medical jargon. Transparent about automated decisions and AI capabilities.
- **Non-Medical Wellness Tone**: All wellness and nutritional content uses everyday fitness terminology, strictly avoiding clinical diagnostic phrases.

### 1.2 Ten Example Microcopy Lines
1. *"You completed 3 workouts this week — great rhythm! Ready for a gentle stretch tomorrow?"*
2. *"Session full? Hop on the waitlist. We’ll notify you right away if a spot opens up."*
3. *"Wellness suggestions, not medical advice. Always listen to your body and consult a professional before starting new routines."*
4. *"Your health details are encrypted and visible only to you. You can export or delete them anytime."*
5. *"We noticed you reported lower back tightness. Would you like to review gentle low-impact class suggestions?"*
6. *"Plan renewal in 5 days. You have 2 sessions remaining this billing cycle."*
7. *"Spot booked! We've sent your confirmation and calendar invite to your email."*
8. *"Please confirm this action on screen before our AI assistant books your class."*
9. *"Account locked for 15 minutes following repeated failed sign-in attempts. Please try again after 10:45 AM."*
10. *"Trainer tip: Please arrive 5 minutes early with your water bottle and yoga mat."*

---

## 2. Design Tokens Overview
FitFlow design tokens are formalized in [`docs/design/tokens.json`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/tokens.json) and compiled for frontend use in [`docs/design/tokens.css`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/tokens.css).

### 2.1 Color Palette Architecture
- **Primary (Sky / Cyan)**: `#0284c7` (Light theme main brand / primary button), `#38bdf8` (Dark theme main brand), `#7dd3fc` (Dark theme button).
- **Secondary (Violet / Indigo)**: `#7c3aed` (Light theme accent), `#a78bfa` (Dark theme accent).
- **Neutral (Slate scale 50–950)**: Standard slate scales ensuring predictable surfaces and borders.
- **Feedback & Semantics**:
  - `Success`: Green `#16a34a` (text `#14532d` on `#dcfce7`).
  - `Warning`: Amber `#d97706` (text `#78350f` on `#fef3c7`).
  - `Danger`: Red `#dc2626` (text `#7f1d1d` on `#fee2e2`).
  - `Info`: Sky `#0284c7` (text `#0c4a6e` on `#e0f2fe`).

### 2.2 Typography Scale
- Primary font: `Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif`
- Monospace font: `ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace`
- Sizes:
  - `xs`: 12px (line-height 16px) — Tags, secondary badges, timestamps.
  - `sm`: 14px (line-height 20px) — Form labels, table cells, secondary descriptions.
  - `base`: 16px (line-height 24px) — Default body text, form input content, buttons.
  - `lg`: 18px (line-height 28px) — Card titles, prominent stat labels.
  - `xl`: 20px (line-height 28px) — Section headings, modal titles.
  - `2xl`: 24px (line-height 32px) — Page subheadings, key stat metrics.
  - `3xl`: 30px (line-height 36px) — Page titles.
  - `4xl`: 36px (line-height 40px) — Hero headlines.

### 2.3 Spacing, Radii, Elevation & Breakpoints
- **Base Grid**: 4px standard multiplier (`space-1` = 4px, `space-2` = 8px, `space-3` = 12px, `space-4` = 16px, `space-6` = 24px, `space-8` = 32px, `space-12` = 48px).
- **Border Radii**: `sm` (4px), `md` (8px standard cards/inputs), `lg` (12px dialogs), `xl` (16px bottom sheets), `full` (9999px pills).
- **Shadows**: Elevation levels 1 (`sm`), 2 (`md` cards), 3 (`lg` dropdowns/popovers), 4 (`xl` modals).
- **Breakpoints**: `sm` (640px), `md` (768px tablet), `lg` (1024px desktop), `xl` (1280px wide desktop).
- **Z-Index System**: Base (0), Dropdown (1000), Sticky (1100), Modal Backdrop (1200), Modal (1300), Popover (1400), Toast (1500), Tooltip (1600).

---

## 3. WCAG 2.2 AA Contrast Verification
The automated verification script [`scripts/check-contrast.js`](file:///c:/Users/shrey/Downloads/FitFlow/scripts/check-contrast.js) evaluates all defined semantic color pairs against WCAG 2.2 AA guidelines:
- **Body Text**: Minimum contrast ratio of **4.5:1** against underlying backgrounds.
- **Large Text & UI Components**: Minimum contrast ratio of **3.0:1** for borders, focus indicators, and badge containers.
- **Verification Result**: 26/26 semantic pairs in Light and Dark modes strictly pass (e.g. Body text on page background: 17.85:1 Light, 18.57:1 Dark).

---

## 4. Layout Architecture & Role-Aware Navigation

### 4.1 Responsive Shell Structure
```
+-----------------------------------------------------------------------+
|  Top NavBar: Logo, Role Badge, Health/Safety Status, User Profile      |
+-------------------+---------------------------------------------------+
|  SideNav          |  Main Content Workspace Area                      |
|  (Desktop >=1024) |                                                   |
|                   |  - Breadcrumbs & Page Header                      |
|  - Dashboard      |  - Mandatory Wellness Disclaimer (if applicable)  |
|  - Classes        |  - Dynamic Route Content                          |
|  - Bookings       |  - Skeletons / States                             |
|  - Membership     |                                                   |
|  - Wellness       |                                                   |
|  - Assistant (AI) |                                                   |
+-------------------+---------------------------------------------------+
|  Mobile Bottom Navigation Tab Bar (Screens < 1024px, Members only)    |
+-----------------------------------------------------------------------+
```

### 4.2 Role-Aware Visibility
- Navigation items and action buttons are conditionally rendered based on the active session's roles (`MEMBER`, `TRAINER`, `ADMIN`) and specific administrative permissions (`USER_READ`, `USER_EDIT`, `CLASS_WRITE`, `REFUND_APPROVE`, `PAYOUT_EXECUTE`, etc.).
- Even when client-side UI hides prohibited controls, the backend enforces deny-by-default Spring Security `@PreAuthorize` authorization on every request.

---

## 5. Accessibility Mandate (WCAG 2.2 AA)
1. **Focus Management**: Visible 2px high-contrast focus ring (`--color-brand-focus-ring`) with 2px offset on all interactive elements. Focus trap active within open modals and drawers.
2. **Keyboard Navigation**: 100% of workflows executable via keyboard only (`Tab`, `Shift+Tab`, `Enter`, `Space`, `Esc`, `Arrow keys`).
3. **Form Controls**: Every input explicitly linked via `htmlFor` / `aria-labelledby` to its `<label>` and via `aria-describedby` to its validation error / helper text.
4. **Touch Target Size**: All interactive buttons, tabs, and form controls have a minimum touch target area of $44 \times 44$ px on mobile devices.
5. **Screen Reader Announcements**: Toasts and live status banners use `role="status"` or `aria-live="polite"` (`aria-live="assertive"` for critical payment/safety alerts).
6. **No Color-Only Meaning**: Status indicators always combine color with an icon and explicit text label (e.g. `[✓ Allowed]`, `[! Caution]`, `[X Restricted]`).
7. **Motion Sensitivity**: `@media (prefers-reduced-motion: reduce)` reduces all CSS transition and animation durations to `0.01ms`.

---

## 6. Internationalisation & Localization
- **Message Catalogue**: All visible UI text, button labels, placeholders, and error messages are routed through [`frontend/src/i18n/en.json`](file:///c:/Users/shrey/Downloads/FitFlow/frontend/src/i18n/en.json). Zero hardcoded user-facing strings in component code.
- **Currency Representation**: Prices are stored in integer paise and displayed in Indian Rupees:
  $$\text{Formatted Price} = \text{"Rs. "} + (\text{paise} / 100).toFixed(2)$$
  e.g., `89900` paise displays as `"Rs. 899.00"`.
- **Date & Time Standards**: All timestamps formatted in the `Asia/Kolkata` time zone (IST) with explicit zone notation (e.g., `"29 Oct 2026, 06:30 PM IST"`).

---

## 7. Standard UI & UX Interaction Patterns

### 7.1 Loading States
- **List & Table Skeletons**: Standard skeleton placeholder blocks matching the exact geometry of list items or table rows. Standalone spinning spinners are strictly banned for data lists.
- **Button Loading**: Buttons display an embedded mini-spinner alongside their label while disabling repeated clicks (`aria-busy="true"`).

### 7.2 Optimistic UI & Idempotency
- **Harmless Actions Only**: Optimistic UI is limited strictly to local UI toggles (e.g. expanding accordions, toggling light/dark theme, tab switches).
- **Financial & Booking Idempotency**: Booking and payment submit buttons generate an `Idempotency-Key` (UUIDv4) upon initial click. Retries of the same attempt reuse the identical key to prevent double-charging or duplicate seat reservations.

### 7.3 Problem+JSON Backend Error Handling
All backend API exceptions conform to RFC 7807 (`application/problem+json`). The frontend interceptor maps each error code to actionable UX recovery:

| Backend Error Code | User-Facing Display Message | Primary Recovery Action |
| :--- | :--- | :--- |
| `UPGRADE_REQUIRED` | *"You have used all session credits included in your plan."* | Displays plan comparison card with `"Upgrade Plan"` button. |
| `SESSION_FULL` | *"This class is currently full."* | Prompts `"Join Waitlist"` button with position indicator. |
| `CONSENT_REQUIRED` | *"Health data consent is needed before accessing personalized wellness features."* | Redirects with return URL to `/app/wellness/consent`. |
| `AGE_RESTRICTED` | *"You must be 18 or older to access nutrition calculations and workout safety screening."* | Explains DPDP Act age gate; disables wellness intake. |
| `RATE_LIMITED` | *"Too many requests. Please wait a moment before trying again."* | Disables trigger button with countdown timer from `Retry-After`. |
| `OPTIMISTIC_LOCK` | *"This class was just modified by another user. Reloading fresh availability."* | Prompts `"Refresh Details"` to fetch latest seats. |
| `PAYMENT_FAILED` | *"Your payment was not completed by the issuing bank."* | Keeps checkout open; prompts `"Try Another Payment Method"`. |
| `UNAUTHENTICATED` | *"Your session has expired. Please sign in to continue."* | Preserves current URL and redirects to `/login`. |
| `FORBIDDEN` | *"You do not have permission to view this page or perform this action."* | Navigates to accessible `/403` explanation page. |
| `BOOKING_RULE_VIOLATION` | *"Booking cutoff window has passed (classes lock 2 hours before start)."* | Highlights class status as locked; suggests upcoming sessions. |
| `LOCKED` | *"Account locked due to 5 consecutive login failures."* | Displays locked banner with remaining lockout minutes. |
| `VALIDATION_FAILED` | *"Please correct the highlighted errors."* | Summarizes field errors at top; focuses first invalid input. |
| `INTERNAL_ERROR` | *"We're experiencing temporary technical difficulties. Please try again shortly."* | Renders friendly ErrorState banner with retry button. |

---

## 8. Wellness & AI UX Guardrails

### 8.1 Mandatory Disclaimer Note
The following standardized disclaimer appears in a prominent, styled callout box on every wellness screen, nutrition report, and within the AI chat panel:
> **Wellness suggestions, not medical advice.** Rules are illustrative and must be reviewed by a qualified professional before real use.

### 8.2 Doctor Check Recommendation Banner
Whenever a member's screening status evaluates to `RED_FLAG` or `doctorCheckRecommended = true` (from PAR-Q responses or reported severe injuries), an amber/red banner is pinned to the top of the screen:
> **Consultation Recommended:** Your health profile indicates you should obtain clearance from a doctor or qualified health professional before participating in strenuous physical activity.

### 8.3 AI Transparency & Identity
- All conversational AI responses are rendered in distinctive chat bubbles badged with `[FitFlow Assistant]` and an AI badge.
- If the administrator has not configured a valid Gemini API key, the assistant panel displays a yellow status banner: *"Assistant running in demonstration / rule-based mode"*.

### 8.4 Deterministic Action Confirmation Cards
- The AI assistant **cannot** book sessions, cancel bookings, update memberships, or save health profiles via chat text alone.
- Every proposed write action renders a structured [`PendingActionCard`](file:///c:/Users/shrey/Downloads/FitFlow/docs/design/components.md) displaying:
  - Tool action name and plain-language summary.
  - Expiry countdown (15-minute validity).
  - Explicit green **Confirm** and neutral **Reject** buttons.
  - Chat text instructions like *"Yes, book it"* are greeted with a prompt to click the on-screen button.

### 8.5 Health Data Privacy & Rights
Every health data screen clearly states data isolation boundaries:
> *"Your wellness data is stored in dedicated encrypted tables and is accessible only to you. Administrators and instructors cannot view your individual profile."*

Each health screen provides 1-click access to **Export My Health Data (JSON)** and a typed confirmation dialog to **Permanently Delete My Health Records**.
