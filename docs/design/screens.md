# FitFlow Screen Inventory & Wireframes (docs/design/screens.md)

This document details all 46 screens comprising the FitFlow application across Public, Member Core, Member Wellness, Member AI, Trainer, and Admin workspaces.

Every screen definition includes:
- Purpose & User Role
- Route path
- Layout description
- Wireframe (ASCII diagram; dual Desktop & Mobile variants for designated screens)
- Component states (loading, empty, error, success, forbidden)
- Form validation rules & User-facing copy
- Accessibility considerations
- Backend API endpoints used
- Events & Analytics (Strictly none, per project privacy contract)

---

# 1. PUBLIC SCREENS

### 1.1 Landing Screen (`public-landing`)
- **Purpose**: Showcase FitFlow studio classes, trainer expertise, and membership pricing to prospective members.
- **Route**: `/`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN` (Public access)
- **Layout Description**: Hero banner with call-to-action buttons ("Browse Classes", "View Plans"), featured classes carousel, instructor spotlights, and plan pricing cards.
- **Wireframe**:
```
+-----------------------------------------------------------------------+
| [Logo] FitFlow           Classes  Plans  About      [Sign In] [Join] |
+-----------------------------------------------------------------------+
|                                                                       |
|      MOVE WITH PURPOSE. TRAIN WITH INTELLIGENCE.                      |
|      Studio fitness tailored to your strength and goals.              |
|                                                                       |
|         [ Explore Classes ]       [ View Membership Plans ]          |
|                                                                       |
+-----------------------------------------------------------------------+
| Featured Classes                                                      |
| +-------------------+  +-------------------+  +---------------------+ |
| | Power Vinyasa     |  | HIIT Athletic     |  | Reformer Pilates    | |
| | Trainer: Maya R.  |  | Trainer: Arjun K. |  | Trainer: Sarah T.   | |
| | 60 mins | High    |  | 45 mins | High    |  | 50 mins | Moderate  | |
| +-------------------+  +-------------------+  +---------------------+ |
+-----------------------------------------------------------------------+
```
- **States**: Loading (skeleton cards), Empty (no classes scheduled), Error (alert banner with retry), Success (rendered catalog).
- **Validation Rules**: None (read-only).
- **Copy**: *"Move with purpose. Train with intelligence. Studio fitness tailored to your strength and goals."*
- **Accessibility**: Skip-to-content link, semantic landmark tags (`<header>`, `<main>`, `<footer>`), aria-labels on carousels.
- **Endpoints**: `GET /api/v1/classes`, `GET /api/v1/plans`
- **Events/Analytics**: None.

---

### 1.2 Sign In Screen (`public-login`)
- **Purpose**: Authenticate existing members, trainers, and administrators.
- **Route**: `/login`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN` (Public access)
- **Layout Description**: Centered card with email input, password input with visibility toggle, "Forgot Password?" link, Sign In button, and registration callout.
- **Wireframe**:
```
+-----------------------------------------------------------------------+
|                             [Logo] FitFlow                            |
|                       Sign in to your account                         |
|                                                                       |
|   Email Address                                                       |
|   [ alice@fitflow.local                                             ] |
|                                                                       |
|   Password                                            [Forgot?]       |
|   [ ****************                                            (o) ] |
|                                                                       |
|   [                        Sign In                                  ] |
|                                                                       |
|   Don't have an account? [Create one now]                             |
+-----------------------------------------------------------------------+
```
- **States**: Loading (button spinner), Error (401 invalid credentials, 429 account locked), Success (redirect to role home).
- **Validation Rules**: Email must be valid email format; password cannot be blank.
- **Copy**: *"Sign in to your account"*, *"Account locked for 15 minutes due to multiple failed attempts."*
- **Accessibility**: Auto-focus on email input, labels linked via `htmlFor`, error summary announced via `role="alert"`.
- **Endpoints**: `POST /api/v1/auth/login`
- **Events/Analytics**: None.

---

### 1.3 Register Screen (`public-register`)
- **Purpose**: Create a new member account with date of birth validation for age compliance.
- **Route**: `/register`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN` (Public access)
- **Layout Description**: Centered form with full name, email, password, password confirmation, and date of birth picker (DD/MM/YYYY).
- **Wireframe**:
```
+-----------------------------------------------------------------------+
|                             [Logo] FitFlow                            |
|                         Create your account                           |
|                                                                       |
|   Full Name                                                           |
|   [ Alice Smith                                                     ] |
|   Email Address                                                       |
|   [ alice@fitflow.local                                             ] |
|   Date of Birth (Must be 14+)                                         |
|   [ 15 / 08 / 1995                                               [c]] |
|   Password                                                            |
|   [ ****************                                            (o) ] |
|   Confirm Password                                                    |
|   [ ****************                                            (o) ] |
|                                                                       |
|   [                     Create Account                              ] |
+-----------------------------------------------------------------------+
```
- **States**: Loading (button spinner), Error (400 validation error, 400 age restricted, 409 email conflict), Success (redirect to login).
- **Validation Rules**: Name 2–100 chars; password $\ge 8$ chars with letters and numbers; age $\ge 14$ years at current date.
- **Copy**: *"You must be at least 14 years old to join FitFlow."*
- **Accessibility**: Error message explicitly linked via `aria-describedby` to the date of birth field.
- **Endpoints**: `POST /api/v1/auth/register`
- **Events/Analytics**: None.

---

### 1.4 Forgot Password Screen (`public-forgot-password`)
- **Purpose**: Request a password reset link via registered email.
- **Route**: `/forgot-password`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN` (Public access)
- **Layout Description**: Centered card with email input and Send Reset Link button.
- **Wireframe**:
```
+-----------------------------------------------------------------------+
|                        Reset your password                            |
|   Enter your email and we'll send you instructions to reset it.       |
|                                                                       |
|   Email Address                                                       |
|   [ member@fitflow.local                                            ] |
|                                                                       |
|   [                   Send Reset Instructions                       ] |
|   [Back to Sign In]                                                   |
+-----------------------------------------------------------------------+
```
- **States**: Loading, Error (400 invalid email), Success (neutral notice: *"If an account exists, instructions have been sent"*).
- **Validation Rules**: Valid email format.
- **Endpoints**: `POST /api/v1/auth/forgot-password`
- **Events/Analytics**: None.

---

### 1.5 Reset Password Screen (`public-reset-password`)
- **Purpose**: Enter new password using token from reset email.
- **Route**: `/reset-password`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN` (Public access)
- **Layout Description**: Token verification badge, new password input, confirm password input, and Submit button.
- **Wireframe**:
```
+-----------------------------------------------------------------------+
|                        Set a new password                             |
|   Password must be at least 8 characters long.                        |
|                                                                       |
|   New Password                                                        |
|   [ ****************                                            (o) ] |
|   Confirm New Password                                                |
|   [ ****************                                            (o) ] |
|                                                                       |
|   [                    Update Password                              ] |
+-----------------------------------------------------------------------+
```
- **States**: Loading, Error (400 invalid token, passwords do not match), Success (redirect to login with toast).
- **Validation Rules**: Passwords match, $\ge 8$ characters.
- **Endpoints**: `POST /api/v1/auth/reset-password`
- **Events/Analytics**: None.

---

### 1.6 Public Plans Screen (`public-plans`)
- **Purpose**: Compare membership tiers and pricing prior to registration.
- **Route**: `/plans`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN` (Public access)
- **Layout Description**: Tier cards side-by-side (Free, Standard, Pro) with monthly price in Rupees, session allocations, and feature lists.
- **Wireframe**:
```
+-----------------------------------------------------------------------+
|                       FitFlow Membership Plans                        |
|                                                                       |
| +-----------------+   +--------------------+   +--------------------+ |
| | FREE TASTER     |   | FITFLOW BASIC      |   | FITFLOW PRO        | |
| | Rs. 0.00 / mo   |   | Rs. 499.00 / mo    |   | Rs. 899.00 / mo    | |
| | 3 Sessions      |   | 8 Sessions / mo    |   | 16 Sessions / mo   | |
| | All Classes     |   | Free Cancellations |   | AI Assistant Access| |
| | [Get Started]   |   | [Get Started]      |   | [Get Started]      | |
| +-----------------+   +--------------------+   +--------------------+ |
+-----------------------------------------------------------------------+
```
- **States**: Loading (skeleton plan cards), Error (retry banner), Success.
- **Endpoints**: `GET /api/v1/plans`
- **Events/Analytics**: None.

---

### 1.7 Not Found Screen (`public-not-found`)
- **Purpose**: Friendly 404 page for broken or non-existent URLs.
- **Route**: `/404`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN`
- **Layout Description**: Clean illustration, 404 message, and button returning to home dashboard.
- **Endpoints**: None.

---

### 1.8 Forbidden Screen (`public-forbidden`)
- **Purpose**: Accessible 403 error page when user lacks role or permissions.
- **Route**: `/403`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN`
- **Layout Description**: Shield icon, explanation that elevated permissions are required, and navigation back to user home.
- **Endpoints**: None.

---

### 1.9 Session Expired Screen (`public-session-expired`)
- **Purpose**: Informs user when authentication tokens have lapsed and silent refresh fails.
- **Route**: `/session-expired`
- **Role**: `MEMBER`, `TRAINER`, `ADMIN`
- **Layout Description**: Clock icon, notice that session has ended, and button to sign in again.
- **Endpoints**: `POST /api/v1/auth/refresh`

---

# 2. MEMBER CORE SCREENS

### 2.1 Home Dashboard (`member-home-dashboard`) — [DUAL WIREFRAME: DESKTOP & MOBILE]
- **Purpose**: Member central hub showing greeting, active membership quota, next booked class, and fast actions.
- **Route**: `/app/dashboard`
- **Role**: `MEMBER`
- **Desktop Wireframe (1280px)**:
```
+-----------------------------------------------------------------------------------------------------------+
| [Logo] FitFlow               Classes   Bookings   Membership   Wellness   Assistant    [Alice S. (v)]     |
+-----------------------------------------------------------------------------------------------------------+
|  Good morning, Alice! You have 2 sessions completed this week.                                            |
|                                                                                                           |
|  +--------------------------------------------+  +------------------------------------------------------+ |
|  | NEXT UPCOMING SESSION                      |  | MEMBERSHIP STATUS                                    | |
|  | Power Vinyasa Yoga                         |  | FitFlow Pro Plan (Active)                            | |
|  | Tomorrow, 07:00 AM IST | Studio A          |  | 11 of 16 Sessions Remaining                          | |
|  | Trainer: Maya Rao                          |  | Renews: 15 Nov 2026 (Rs. 899.00)                     | |
|  | [View Details]        [Cancel Booking]     |  | [Change Plan]               [View Invoices]          | |
|  +--------------------------------------------+  +------------------------------------------------------+ |
|                                                                                                           |
|  WEEKLY PROGRESS OVERVIEW                                                                                 |
|  +-------------------------+  +-------------------------+  +--------------------------------------------+ |
|  | Attended Sessions       |  | Estimated Energy Burn   |  | Safety & Wellness Status                   | |
|  | 2 / 3 Goal Planned      |  | 840 kcal                |  | [✓ ALL CLEAR] Screening Up to Date         | |
|  +-------------------------+  +-------------------------+  +--------------------------------------------+ |
+-----------------------------------------------------------------------------------------------------------+
```
- **Mobile Wireframe (375px)**:
```
+-----------------------------------------+
| [Logo] FitFlow                 [Alice]  |
+-----------------------------------------+
| Good morning, Alice!                    |
|                                         |
| +-------------------------------------+ |
| | NEXT UPCOMING SESSION               | |
| | Power Vinyasa Yoga                  | |
| | Tomorrow, 07:00 AM IST              | |
| | Studio A | Trainer: Maya Rao        | |
| | [ View Details ]                    | |
| +-------------------------------------+ |
|                                         |
| +-------------------------------------+ |
| | PLAN: FitFlow Pro                   | |
| | 11 / 16 sessions remaining          | |
| | [ Manage Plan ]                     | |
| +-------------------------------------+ |
|                                         |
| QUICK STATS                             |
| 2 classes attended | 840 kcal burn      |
|                                         |
+-----------------------------------------+
| [Home]  [Classes]  [Bookings]  [AI]  [=] |
+-----------------------------------------+
```
- **States**: Loading (skeletons), Empty (zero bookings; prompts "Browse Classes"), Error, Success.
- **Endpoints**: `GET /api/v1/me`, `GET /api/v1/me/membership`, `GET /api/v1/me/bookings`, `GET /api/v1/sessions`
- **Events/Analytics**: None.

---

### 2.2 Browse Classes Screen (`member-browse-classes`)
- **Purpose**: Search, filter, and discover studio classes by category, intensity, trainer, and date.
- **Route**: `/app/classes`
- **Role**: `MEMBER`
- **Layout Description**: Filter bar across top (Category: YOGA, HIIT, etc.; Intensity: LOW, MODERATE, HIGH; Trainer dropdown; Available only toggle), followed by grid of fitness class cards.
- **Wireframe**:
```
+-----------------------------------------------------------------------+
|  Browse Fitness Classes                                               |
|  [Search class name...] [Category: All (v)] [Intensity: All (v)]      |
|  [Trainer: All (v)]     [x] Available Spots Only                      |
+-----------------------------------------------------------------------+
|  +------------------------+  +------------------------+               |
|  | Power Vinyasa Yoga     |  | Athletic HIIT Interval |               |
|  | Category: YOGA         |  | Category: HIIT         |               |
|  | Intensity: MODERATE    |  | Intensity: HIGH        |               |
|  | MET: 4.5 | 60 mins     |  | MET: 8.0 | 45 mins     |               |
|  | [View Class Schedule]  |  | [View Class Schedule]  |               |
|  +------------------------+  +------------------------+               |
+-----------------------------------------------------------------------+
```
- **States**: Loading (card skeletons), Empty ("No classes match filters"), Error, Success.
- **Endpoints**: `GET /api/v1/classes`, `GET /api/v1/classes/metadata`, `GET /api/v1/trainers`

---

### 2.3 Class Details Screen (`member-class-details`)
- **Purpose**: View in-depth class description, intensity, contraindication tags, trainer bio, and upcoming scheduled sessions.
- **Route**: `/app/classes/:id`
- **Role**: `MEMBER`
- **Layout Description**: Class header with category badge, description, contraindications callout, and scheduled session list with Book buttons.
- **Endpoints**: `GET /api/v1/classes/{id}`, `GET /api/v1/sessions`

---

### 2.4 Session Details Screen (`member-session-details`) — [DUAL WIREFRAME: DESKTOP & MOBILE]
- **Purpose**: Inspect a single class instance, see remaining seat capacity, and execute 1-click booking or join waitlist.
- **Route**: `/app/sessions/:id`
- **Role**: `MEMBER`
- **Desktop Wireframe (1280px)**:
```
+-----------------------------------------------------------------------------------------------------------+
| Classes > Power Vinyasa Yoga > Tuesday Session                                                           |
+-----------------------------------------------------------------------------------------------------------+
|  Power Vinyasa Yoga - Tuesday, 03 Nov 2026                                                                |
|  Time: 07:00 AM - 08:00 AM IST | Studio A | Capacity: 18 / 20 Booked                                      |
|  Instructor: Maya Rao (Specialty: Vinyasa & Alignment)                                                    |
|                                                                                                           |
|  +-----------------------------------------------------------------------------------------------------+  |
|  | BOOKING ACTION                                                                                      |  |
|  | You have 11 sessions remaining in your FitFlow Pro plan.                                            |  |
|  | Free cancellation permitted until 05:00 AM IST (2 hours prior to start).                             |  |
|  |                                                                                                     |  |
|  |      [ Book This Session (1 Credit) ]                 [ Back to Schedule ]                          |  |
|  +-----------------------------------------------------------------------------------------------------+  |
|                                                                                                           |
|  Class Guidelines & Equipment                                                                             |
|  - Mats and blocks provided in Studio A. Please arrive 10 minutes early.                                  |
+-----------------------------------------------------------------------------------------------------------+
```
- **Mobile Wireframe (375px)**:
```
+-----------------------------------------+
| [< Back] Session Details                |
+-----------------------------------------+
| Power Vinyasa Yoga                      |
| Tue, 03 Nov | 07:00 - 08:00 AM IST      |
| Studio A | Instructor: Maya Rao         |
|                                         |
| Capacity: 18/20 spots filled            |
| [====================..] 90% full       |
|                                         |
| Cutoff Notice:                          |
| Free cancellation until 05:00 AM IST.   |
|                                         |
| +-------------------------------------+ |
| | [ Book Session (1 Credit) ]         | |
| +-------------------------------------+ |
|                                         |
| If session fills:                       |
| [ Join Waitlist ]                       |
+-----------------------------------------+
| [Home]  [Classes]  [Bookings]  [AI]  [=] |
+-----------------------------------------+
```
- **States**: Loading, Session Full (Book disabled; "Join Waitlist" enabled), Already Booked ("You are booked"), Cutoff Passed ("Booking Closed"), Success.
- **Validation Rules**: Idempotency key generated on submit; disabled while pending.
- **Endpoints**: `GET /api/v1/sessions/{id}`, `POST /api/v1/bookings`, `DELETE /api/v1/waitlist/{sessionId}`

---

### 2.5 My Bookings Screen (`member-my-bookings`)
- **Purpose**: Review upcoming reserved sessions, cancel with cutoff validation, and view historical attended classes.
- **Route**: `/app/bookings`
- **Role**: `MEMBER`
- **Layout Description**: Tabbed layout: "Upcoming (3)" and "Past & Completed (14)". Cancel button triggers ConfirmDialog with cutoff warning.
- **Endpoints**: `GET /api/v1/me/bookings`, `DELETE /api/v1/bookings/{id}`

---

### 2.6 Membership Screen (`member-membership`)
- **Purpose**: Manage subscription tier, inspect remaining session quota, calculate prorated upgrades, or cancel plan.
- **Route**: `/app/membership`
- **Role**: `MEMBER`
- **Layout Description**: Active plan banner, quota usage radial gauge, renewal date, plan comparison grid, and "Change Plan" modal trigger.
- **Endpoints**: `GET /api/v1/me/membership`, `GET /api/v1/plans`, `POST /api/v1/me/membership/change-preview`, `POST /api/v1/memberships/cancel`

---

### 2.7 Checkout Screen (`member-checkout`) — [DUAL WIREFRAME: DESKTOP & MOBILE]
- **Purpose**: Complete membership purchase or upgrade via Razorpay gateway modal.
- **Route**: `/app/membership/checkout`
- **Role**: `MEMBER`
- **Desktop Wireframe (1280px)**:
```
+-----------------------------------------------------------------------------------------------------------+
| Membership > Checkout                                                                                     |
+-----------------------------------------------------------------------------------------------------------+
|  ORDER SUMMARY                                          PAYMENT METHOD                                    |
|  +---------------------------------------------------+  +-----------------------------------------------+ |
|  | Upgrading to: FitFlow Pro                         |  | Razorpay Secure Checkout                      | |
|  | Billing: Monthly (Renews automatically)           |  | Cards, UPI, NetBanking, Wallets               | |
|  | Quota: 16 sessions per month                      |  |                                               | |
|  |                                                   |  | Subtotal:       Rs. 899.00                    | |
|  | Prorated Credit (Basic):         - Rs. 149.00     |  | Taxes (GST):        Rs. 0.00                    | |
|  | Total Due Today:                   Rs. 750.00     |  | Total Payable:  Rs. 750.00                    | |
|  +---------------------------------------------------+  |                                               | |
|                                                         |  [ Pay Rs. 750.00 via Razorpay ]              | |
|  Encrypted 256-bit SSL transaction.                     +-----------------------------------------------+ |
+-----------------------------------------------------------------------------------------------------------+
```
- **Mobile Wireframe (375px)**:
```
+-----------------------------------------+
| [< Back] Checkout                       |
+-----------------------------------------+
| ORDER SUMMARY                           |
| FitFlow Pro Plan                        |
| 16 Sessions / Month                     |
|                                         |
| Full Price:                 Rs. 899.00  |
| Prorated Credit:          - Rs. 149.00  |
| --------------------------------------- |
| Total Due Today:            Rs. 750.00  |
|                                         |
| +-------------------------------------+ |
| | [ Pay Rs. 750.00 via Razorpay ]     | |
| +-------------------------------------+ |
|                                         |
| [x] Secure 256-bit encrypted checkout   |
+-----------------------------------------+
```
- **States**: Loading, Gateway Open, Payment Pending, Payment Error, Success.
- **Endpoints**: `POST /api/v1/memberships/subscribe`, `POST /api/v1/memberships/change`, `GET /api/v1/payments/{paymentId}/client-payload`

---

### 2.8 Payment Result Screen (`member-payment-result`)
- **Purpose**: Verify payment signature following Razorpay redirect and grant membership credits.
- **Route**: `/app/membership/payment-result`
- **Role**: `MEMBER`
- **Layout Description**: Success checkmark animation, transaction ID, payment receipt summary, and "Go to Dashboard" button.
- **Endpoints**: `POST /api/v1/payments/{paymentId}/confirm`, `GET /api/v1/payments/{paymentId}`

---

### 2.9 Payments & Invoices Screen (`member-payments-invoices`)
- **Purpose**: Review historical billing statements, transactions, and download tax invoices.
- **Route**: `/app/payments`
- **Role**: `MEMBER`
- **Layout Description**: Table of past payments (Date, Plan, Amount in Rs., Gateway Reference, Status) with Download Invoice buttons.
- **Endpoints**: `GET /api/v1/me/payments`, `GET /api/v1/me/invoices/{id}`

---

### 2.10 Account Screen (`member-account`)
- **Purpose**: Member profile self-service, password change, notification toggles, DPDP export, and account deletion.
- **Route**: `/app/account`
- **Role**: `MEMBER`
- **Layout Description**: Personal details form, password reset card, notification checkboxes, and "Data Privacy & Account Actions" section.
- **Endpoints**: `GET /api/v1/me`, `PATCH /api/v1/me`, `POST /api/v1/auth/change-password`, `GET /api/v1/me/export`, `POST /api/v1/me/delete-request`

---

# 3. MEMBER WELLNESS SCREENS

### 3.1 Consent Screen (`member-wellness-consent`)
- **Purpose**: Plain-language DPDP consent management with version v1 text and granular purpose toggles.
- **Route**: `/app/wellness/consent`
- **Role**: `MEMBER`
- **Layout Description**: Markdown text box rendering `resources/consent/v1.md`, purpose toggle checkboxes, and Save Consent button.
- **Endpoints**: `GET /api/v1/wellness/consent`, `POST /api/v1/wellness/consent`, `DELETE /api/v1/wellness/consent/{purpose}`

---

### 3.2 Wellness Profile Wizard (`member-wellness-profile`) — [DUAL WIREFRAME: DESKTOP & MOBILE]
- **Purpose**: Multi-step fitness intake capturing experience level, goals, injuries, conditions, and PAR-Q screening.
- **Route**: `/app/wellness/profile`
- **Role**: `MEMBER`
- **Desktop Wireframe (1280px)**:
```
+-----------------------------------------------------------------------------------------------------------+
| Wellness Profile Intake (Step 4 of 6: Injuries & Physical Limitations)                                    |
| [1. Basics]-----[2. Experience]-----[3. Goals & Days]=====[4. Injuries]-----[5. PAR-Q]-----[6. Review]    |
+-----------------------------------------------------------------------------------------------------------+
|  Do you have any current or recurring joint or muscle injuries?                                           |
|  This information helps our scheduling filters recommend safe workouts and modifications.                 |
|                                                                                                           |
|  +-----------------------------------------------------------------------------------------------------+  |
|  | Injury Area: [ Knee               (v) ]    Severity: [ ( ) Mild   (*) Moderate   ( ) Severe ]        |  |
|  | [ + Add Another Injury ]                                                                            |  |
|  +-----------------------------------------------------------------------------------------------------+  |
|                                                                                                           |
|  Active Reported Injuries:                                                                                |
|  [ Knee (Moderate)  (x) ]                                                                                 |
|                                                                                                           |
|  +-----------------------------------------------------------------------------------------------------+  |
|  | Disclaimer: Wellness suggestions, not medical advice. Always consult a physician.                  |  |
|  +-----------------------------------------------------------------------------------------------------+  |
|                                                                                                           |
|      [ < Back: Goals & Availability ]                         [ Continue to PAR-Q Questions > ]           |
+-----------------------------------------------------------------------------------------------------------+
```
- **Mobile Wireframe (375px)**:
```
+-----------------------------------------+
| Step 4 of 6: Injuries                   |
| [====================..........] 65%    |
+-----------------------------------------+
| Do you have any injuries?               |
|                                         |
| Area:                                   |
| [ Knee                              (v) ]|
|                                         |
| Severity:                               |
| ( ) Mild  (*) Moderate  ( ) Severe      |
|                                         |
| [ + Add Injury ]                        |
|                                         |
| Current:                                |
| • Knee (Moderate)  [Delete]             |
|                                         |
| +-------------------------------------+ |
| | Note: Wellness suggestions, not     | |
| | medical advice.                     | |
| +-------------------------------------+ |
|                                         |
| [ < Back ]               [ Next > ]     |
+-----------------------------------------+
```
- **States**: Loading, Saving, Error, Success.
- **Validation Rules**: Past experience note $\le 300$ chars; height 120–230 cm; weight 30–300 kg; 1–3 goals required.
- **Endpoints**: `GET /api/v1/wellness/profile`, `PUT /api/v1/wellness/profile`, `GET /api/v1/wellness/metadata`

---

### 3.3 Screening Result Screen (`member-screening-result`)
- **Purpose**: Show deterministic evaluation verdict (`ALLOWED`, `CAUTION`, `RESTRICTED`, `RED_FLAG`) and doctor consultation alert.
- **Route**: `/app/wellness/screening`
- **Role**: `MEMBER`
- **Layout Description**: Prominent status badge, doctor check banner (if flagged), list of caution areas with exercise modifications, and "Browse Safe Classes" button.
- **Endpoints**: `GET /api/v1/wellness/profile`

---

### 3.4 Safe Classes Screen (`member-safe-classes`)
- **Purpose**: View curated class schedule segregated into Allowed, Caution (with suggested modifications), and Restricted classes.
- **Route**: `/app/wellness/safe-classes`
- **Role**: `MEMBER`
- **Layout Description**: Filter tabs ("Safe to Attend", "Caution / Modified", "Restricted"); cards display specific modification tags.
- **Endpoints**: `GET /api/v1/wellness/classes/safe`, `GET /api/v1/wellness/classes/{classId}/check`

---

### 3.5 Calorie Numbers Screen (`member-calorie-numbers`)
- **Purpose**: Deterministic BMR, TDEE, and class calorie burn estimates based strictly on verified MET values.
- **Route**: `/app/wellness/calories`
- **Role**: `MEMBER`
- **Layout Description**: Daily metabolic summary card, interactive class calorie calculator dropdown, and prominent medical disclaimer banner.
- **Endpoints**: `GET /api/v1/wellness/nutrition/numbers`, `GET /api/v1/wellness/classes/{classId}/burn`

---

### 3.6 Weekly Progress Screen (`member-weekly-progress`)
- **Purpose**: Track weekly workouts completed, active streak count, estimated calorie burn, and weight logs.
- **Route**: `/app/wellness/progress`
- **Role**: `MEMBER`
- **Layout Description**: Progress stat cards, weekly completion chart, and "Log Body Weight" form.
- **Endpoints**: `GET /api/v1/wellness/progress/weekly`, `GET /api/v1/wellness/metrics`, `POST /api/v1/wellness/metrics`

---

### 3.7 Post-Class Feedback Screen (`member-post-class-feedback`)
- **Purpose**: Rate attended class and report any felt joint or muscle pain for safety monitoring.
- **Route**: `/app/wellness/feedback`
- **Role**: `MEMBER`
- **Layout Description**: Star rating, optional encrypted comment, pain toggle (`Felt pain? Yes/No`), pain area selector, and Submit button.
- **Endpoints**: `POST /api/v1/bookings/{bookingId}/feedback`

---

### 3.8 Restriction Proposals Screen (`member-restriction-proposals`)
- **Purpose**: Review automated safety restriction proposals generated following reported pain or injury updates.
- **Route**: `/app/wellness/proposals`
- **Role**: `MEMBER`
- **Layout Description**: List of proposed modifications (e.g. avoid high-impact jumps for knee pain) with 1-click Accept or Dismiss buttons.
- **Endpoints**: `GET /api/v1/wellness/restriction-proposals`, `POST /api/v1/wellness/restriction-proposals/{id}/accept`, `POST /api/v1/wellness/restriction-proposals/{id}/dismiss`

---

### 3.9 My Health Data Rights Screen (`member-my-health-data`)
- **Purpose**: Exercise DPDP Act data rights: verify dedicated encrypted storage, export full health JSON, and execute typed permanent deletion.
- **Route**: `/app/wellness/data-rights`
- **Role**: `MEMBER`
- **Layout Description**: Privacy explanation banner ("Only you can see this data"), "Export All Health Data (JSON)" button, and "Delete Health Records" section with typed confirmation modal.
- **Endpoints**: `GET /api/v1/wellness/profile`, `GET /api/v1/wellness/export`, `DELETE /api/v1/wellness/data`

---

# 4. MEMBER AI ASSISTANT

### 4.1 Assistant Panel (`member-assistant-panel`) — [DUAL WIREFRAME: DESKTOP & MOBILE]
- **Purpose**: Multi-turn conversational AI for plan recommendations, scheduling inquiries, and pending action execution.
- **Route**: `/app/assistant`
- **Role**: `MEMBER`
- **Desktop Wireframe (1280px)**:
```
+-----------------------------------------------------------------------------------------------------------+
| FitFlow AI Assistant                                                      Status: [✓ Model Configured]    |
+-----------------------------------+-----------------------------------------------------------------------+
| CONVERSATIONS                     | [FitFlow Assistant]                                                   |
| +-------------------------------+ | Hi Alice! Based on your goal to build endurance, I can suggest two    |
| | * Weekly Plan Recommendation  | | great cardio classes for this week. Would you like to view them?      |
| |   Today, 10:14 AM             | |                                                                     |
| |                               | | [Alice]                                                             |
| |   Recovery & Stretches        | | Yes, please schedule me for Wednesday morning HIIT.                 |
| |   Yesterday                   | |                                                                     |
| +-------------------------------+ | [FitFlow Assistant]                                                   |
| [ + New Conversation ]          | | [Tool: search_sessions] [Tool: propose_booking]                     |
|                                   | | I found Athletic HIIT on Wednesday at 07:00 AM in Studio B.         |
|                                   | | Please review and confirm below:                                    |
|                                   | |                                                                     |
|                                   | | +-----------------------------------------------------------------+ |
|                                   | | PENDING ACTION: Book Athletic HIIT (Wed, 07:00 AM)                | |
|                                   | | Session ID: 1042 | Studio B | Instructor: Arjun K.                | |
|                                   | | Expires in: 14 mins 20 secs                                       | |
|                                   | |                                                                   | |
|                                   | |     [ ✓ Confirm & Book (1 Credit) ]        [ X Reject ]           | |
|                                   | +-----------------------------------------------------------------+ |
|                                   |                                                                       |
|                                   | +-------------------------------------------------------------------+ |
|                                   | | [Type your message to assistant...]                      [ Send ] | |
|                                   +---------------------------------------------------------------------+ |
+-----------------------------------------------------------------------------------------------------------+
```
- **Mobile Wireframe (375px)**:
```
+-----------------------------------------+
| [=] AI Assistant         [New Chat (+)] |
+-----------------------------------------+
| Disclaimer: Wellness suggestions, not   |
| medical advice.                         |
|                                         |
| [Assistant]: I found Athletic HIIT for  |
| Wed 07:00 AM. Please confirm below:     |
|                                         |
| +-------------------------------------+ |
| | PENDING ACTION                      | |
| | Book Athletic HIIT                  | |
| | Wed, 07:00 AM | Studio B            | |
| | Expires: 14m 20s                    | |
| |                                     | |
| | [ Confirm (1 Credit) ]  [ Reject ]  | |
| +-------------------------------------+ |
|                                         |
| +-------------------------------------+ |
| | [Ask assistant...]           [Send] | |
| +-------------------------------------+ |
+-----------------------------------------+
| [Home]  [Classes]  [Bookings]  [AI]  [=] |
+-----------------------------------------+
```
- **States**: Loading, Streaming SSE token emission, Pending Action active, Action Confirmed, Error.
- **Endpoints**: `POST /api/v1/agent/chat`, `GET /api/v1/agent/chat/stream`, `POST /api/v1/agent/chat/stream`, `GET /api/v1/agent/conversations`, `GET /api/v1/agent/conversations/{id}/messages`, `DELETE /api/v1/agent/conversations/{id}`, `GET /api/v1/agent/actions/pending`, `POST /api/v1/agent/actions/{id}/confirm`, `POST /api/v1/agent/actions/{id}/reject`, `GET /api/v1/agent/status`

---

# 5. TRAINER SCREENS

### 5.1 Trainer Dashboard (`trainer-dashboard`)
- **Purpose**: High-level instructor summary of upcoming teaching assignments, today's schedule, and monthly earnings.
- **Route**: `/trainer/dashboard`
- **Role**: `TRAINER`
- **Endpoints**: `GET /api/v1/trainer/me`, `GET /api/v1/trainer/me/summary`, `GET /api/v1/trainer/sessions`

---

### 5.2 My Sessions Screen (`trainer-my-sessions`)
- **Purpose**: 7-day schedule grid showing assigned class sessions, room assignments, and enrolled attendee counts.
- **Route**: `/trainer/sessions`
- **Role**: `TRAINER`
- **Endpoints**: `GET /api/v1/trainer/sessions`

---

### 5.3 Session Detail & Attendance Screen (`trainer-session-detail`)
- **Purpose**: Live roster of booked attendees, attendance marking (`ATTENDED` vs `NO_SHOW`), and class completion action.
- **Route**: `/trainer/sessions/:id`
- **Role**: `TRAINER`
- **Endpoints**: `GET /api/v1/sessions/{id}`, `POST /api/v1/trainer/sessions/{id}/attendance`, `POST /api/v1/trainer/sessions/{id}/complete`

---

### 5.4 Class Prep Summary Screen (`trainer-class-prep-summary`)
- **Purpose**: Privacy-preserving aggregated count of member injuries and exercise modifications with small-group privacy threshold notice.
- **Route**: `/trainer/sessions/:id/prep`
- **Role**: `TRAINER`
- **Notice**: *"Aggregated counts only. Member identities are strictly withheld to protect personal health privacy."*
- **Endpoints**: `GET /api/v1/trainer/sessions/{sessionId}/prep`

---

### 5.5 Trainer Earnings Screen (`trainer-earnings`)
- **Purpose**: Review completed session pay ledger, current session rate, paid vs pending earnings, and approved payouts.
- **Route**: `/trainer/earnings`
- **Role**: `TRAINER`
- **Endpoints**: `GET /api/v1/trainer/me/earnings`

---

### 5.6 Profile & Availability Editor (`trainer-profile-editor`)
- **Purpose**: Update public instructor biography, fitness specialties, and recurring weekly teaching availability.
- **Route**: `/trainer/profile`
- **Role**: `TRAINER`
- **Endpoints**: `GET /api/v1/trainer/me`, `PATCH /api/v1/trainer/me`

---

# 6. ADMIN SCREENS (Zero Health Data Exposure)

### 6.1 Admin Dashboard (`admin-dashboard`)
- **Purpose**: Platform operations center tracking studio utilization %, pending refund queue count, and transactional outbox queue depth.
- **Route**: `/admin/dashboard`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/admin/reports/utilization`, `GET /api/v1/admin/refunds`, `GET /api/v1/admin/messaging/queues`, `GET /api/v1/admin/messaging/outbox`

---

### 6.2 Admin Users List & Management (`admin-users`) — [DUAL WIREFRAME: DESKTOP & MOBILE]
- **Purpose**: User administration: filter members/trainers/admins, create staff accounts, manage permissions, and suspend/reactivate users.
- **Route**: `/admin/users`
- **Role**: `ADMIN`
- **Desktop Wireframe (1280px)**:
```
+-----------------------------------------------------------------------------------------------------------+
| Admin > User Management                                               [ + Provision Staff User ]          |
+-----------------------------------------------------------------------------------------------------------+
|  [Filter by Role: All (v)]   [Status: Active (v)]   [Search by name or email...]                          |
|                                                                                                           |
|  ID   Name             Email                    Role      Permissions          Status     Actions         |
|  -------------------------------------------------------------------------------------------------------  |
|  101  Maya Rao         maya@fitflow.local       TRAINER   [None]               ACTIVE     [Edit] [Rate]   |
|  102  Vikram Admin     vikram@fitflow.local     ADMIN     [USER_READ, ...]     ACTIVE     [Permissions]   |
|  103  Alice Smith      alice@fitflow.local      MEMBER    [None]               ACTIVE     [Suspend]       |
|                                                                                                           |
|  Showing 1 - 25 of 142 Users                                          [ < Prev ] [ 1 ] [ 2 ] [ Next > ]   |
+-----------------------------------------------------------------------------------------------------------+
```
- **Mobile Wireframe (375px)**:
```
+-----------------------------------------+
| Admin Users               [ + Add ]     |
+-----------------------------------------+
| [Search users...]                       |
|                                         |
| +-------------------------------------+ |
| | Maya Rao (TRAINER)                  | |
| | maya@fitflow.local                  | |
| | Status: ACTIVE                      | |
| | [ Manage ]             [ Set Rate ] | |
| +-------------------------------------+ |
|                                         |
| +-------------------------------------+ |
| | Vikram Admin (ADMIN)                | |
| | Status: ACTIVE                      | |
| | [ Edit Permissions ]                | |
| +-------------------------------------+ |
|                                         |
| Page 1 of 6          [ < ]  [ > ]       |
+-----------------------------------------+
```
- **States**: Loading, Empty, Error, Success.
- **Endpoints**: `GET /api/v1/admin/users`, `POST /api/v1/admin/users`, `GET /api/v1/admin/users/{id}`, `PATCH /api/v1/admin/users/{id}`, `PUT /api/v1/admin/users/{id}/permissions`, `POST /api/v1/admin/users/{id}/suspend`, `POST /api/v1/admin/users/{id}/reactivate`

---

### 6.3 Admin Classes Screen (`admin-classes`)
- **Purpose**: Manage fitness class catalog, configure MET values, assign contraindication tags, and confirm AI drafts.
- **Route**: `/admin/classes`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/classes`, `POST /api/v1/admin/classes`, `PUT /api/v1/admin/classes/{id}`, `POST /api/v1/admin/classes/{id}/activate`, `POST /api/v1/admin/classes/{id}/deactivate`, `POST /api/v1/admin/classes/{id}/confirm-metadata`

---

### 6.4 Admin Sessions Screen (`admin-sessions`)
- **Purpose**: Studio class schedule creator with conflict detection, recurring generation, and cancellation tools.
- **Route**: `/admin/sessions`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/sessions`, `POST /api/v1/admin/sessions`, `PATCH /api/v1/admin/sessions/{id}/reschedule`, `POST /api/v1/admin/sessions/{id}/cancel`, `POST /api/v1/admin/bookings/{id}/cancel`

---

### 6.5 Admin Plans Screen (`admin-plans`)
- **Purpose**: Create and edit subscription tiers, set monthly prices in paise, session quotas, and active status.
- **Route**: `/admin/plans`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/plans`, `POST /api/v1/admin/plans`, `PUT /api/v1/admin/plans/{id}`

---

### 6.6 Admin Refunds Screen (`admin-refunds`)
- **Purpose**: Refund governance queue allowing authorized admins to approve or reject member refund requests with audit notes.
- **Route**: `/admin/refunds`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/admin/refunds`, `POST /api/v1/admin/refunds/{id}/approve`, `POST /api/v1/admin/refunds/{id}/reject`

---

### 6.7 Admin Payouts Screen (`admin-payouts`)
- **Purpose**: Monthly trainer payroll generator enforcing two-admin separation of duty, banking CSV exports, and mark-paid workflows.
- **Route**: `/admin/payouts`
- **Role**: `ADMIN`
- **Endpoints**: `POST /api/v1/admin/payouts`, `GET /api/v1/admin/payouts/{id}`, `POST /api/v1/admin/payouts/{id}/approve`, `POST /api/v1/admin/payouts/{id}/mark-paid`, `GET /api/v1/admin/payouts/{id}/export.csv`, `PATCH /api/v1/admin/trainers/{id}/rate`

---

### 6.8 Admin Settings: AI Screen (`admin-settings-ai`)
- **Purpose**: Configure Gemini model version, enter runtime Google AI API key, view masked key status, and run live connection test.
- **Route**: `/admin/settings/ai`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/admin/settings/ai`, `PUT /api/v1/admin/settings/ai/key`, `DELETE /api/v1/admin/settings/ai/key`, `PUT /api/v1/admin/settings/ai/model`, `POST /api/v1/admin/settings/ai/test`

---

### 6.9 Admin Settings: Payments Screen (`admin-settings-payments`)
- **Purpose**: Switch payment gateway between MOCK and RAZORPAY, configure encrypted API key & secret, and run connection test.
- **Route**: `/admin/settings/payments`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/admin/settings/payments`, `PUT /api/v1/admin/settings/payments/gateway`, `PUT /api/v1/admin/settings/payments/razorpay`, `DELETE /api/v1/admin/settings/payments/razorpay`, `POST /api/v1/admin/settings/payments/razorpay/test`

---

### 6.10 Admin Messaging Console (`admin-messaging`)
- **Purpose**: Monitor RabbitMQ queue depths, dead-letter queue (DLQ) replays, and transactional outbox failed message retries.
- **Route**: `/admin/messaging`
- **Role**: `ADMIN`
- **Endpoints**: `GET /api/v1/admin/messaging/outbox`, `POST /api/v1/admin/messaging/outbox/{id}/retry`, `GET /api/v1/admin/messaging/queues`, `POST /api/v1/admin/messaging/dlq/{queue}/replay`

---

### 6.11 Admin Assistant Panel (`admin-assistant-panel`)
- **Purpose**: Administrative multi-agent copilot for retention analysis, demand forecasting, and class metadata drafting (strictly zero health data access).
- **Route**: `/admin/assistant`
- **Role**: `ADMIN`
- **Endpoints**: `POST /api/v1/agent/chat`, `GET /api/v1/agent/chat/stream`, `POST /api/v1/agent/chat/stream`, `GET /api/v1/agent/conversations`, `GET /api/v1/agent/conversations/{id}/messages`, `GET /api/v1/agent/actions/pending`, `POST /api/v1/agent/actions/{id}/confirm`, `POST /api/v1/agent/actions/{id}/reject`, `GET /api/v1/agent/status`
