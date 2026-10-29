# FitFlow Core User Flows (docs/design/flows.md)

This document diagrams the primary end-to-end user journeys in FitFlow across Member, Trainer, and Administrator roles. Each flow outlines the complete happy path alongside at least three documented failure/exception branches.

---

## Flow 1: Registration, First Login & Forced Password Change

### 1.1 Diagram
```mermaid
flowchart TD
    Start([User Registration]) --> RegisterForm["Fill Registration Form (DOB, Email, Password)"]
    RegisterForm --> CheckAge{Age >= 14?}
    CheckAge -- No --> AgeErr["Display Error: Must be 14+ to register"]
    CheckAge -- Yes --> SubmitReg["POST /api/v1/auth/register"]
    SubmitReg --> CheckEmailConflict{Email Taken?}
    CheckEmailConflict -- Yes --> ConflictErr["409 Conflict: Email already registered"]
    CheckEmailConflict -- No --> RegSuccess["Account Created & Default FREE Plan Assigned"]
    RegSuccess --> LoginForm["POST /api/v1/auth/login"]
    LoginForm --> AuthCheck{Credentials Valid?}
    AuthCheck -- No --> AuthErr["401 Unauthorized / Locked on 5 fails"]
    AuthCheck -- Yes --> ForcedCheck{mustChangePassword?}
    ForcedCheck -- Yes --> ForceScreen["Forced Password Change Screen (POST /api/v1/auth/change-password)"]
    ForceScreen --> Home["Member Dashboard (/app/dashboard)"]
    ForcedCheck -- No --> Home
```

### 1.2 Path Specifications
- **Happy Path**: User enters valid details with DOB $\ge 14$ years. Registration completes; default FREE plan with 3 session credits is assigned. On first sign-in, if `mustChangePassword` is false, user is routed directly to `/app/dashboard`. If created by an admin with a temporary password (`mustChangePassword: true`), user is gated to set a new password first, then lands on Home.
- **Failure Path 1 (Underage)**: User enters DOB indicating age $<14$. Form fails with 400 Bad Request: *"You must be at least 14 years old to join FitFlow."*
- **Failure Path 2 (Duplicate Email)**: User registers an email that already exists. Backend returns 409 Conflict with code `CONFLICT`.
- **Failure Path 3 (Account Lockout)**: User attempts sign-in with wrong password 5 consecutive times. Account is locked for 15 minutes returning 429 Too Many Requests with code `LOCKED` and a `Retry-After` header.

---

## Flow 2: Browse, Book, Confirmation, Cancellation & Waitlist Promotion

### 2.1 Diagram
```mermaid
flowchart TD
    Browse["Browse Classes (/app/classes)"] --> SelectSession["Select Session Details (/app/sessions/:id)"]
    SelectSession --> CheckCap{Spots Available?}
    CheckCap -- Yes --> ClickBook["Click 'Book This Session' (Idempotency-Key)"]
    ClickBook --> EntitlementCheck{Quota Remaining?}
    EntitlementCheck -- No --> UpgradePrompt["402 UPGRADE_REQUIRED: Show Plans"]
    EntitlementCheck -- Yes --> BookSuccess["Booking CONFIRMED (Outbox: booking.created)"]
    BookSuccess --> EmailSent["Email Confirmation & Calendar Invite Dispatched"]
    
    CheckCap -- No --> JoinWaitlist["Click 'Join Waitlist' (POST /api/v1/bookings)"]
    JoinWaitlist --> WaitlistActive["Waitlist Position Assigned"]
    
    BookSuccess --> CancelFlow["Member Cancels Booking (DELETE /api/v1/bookings/:id)"]
    CancelFlow --> CutoffCheck{Within 2hr Cutoff?}
    CutoffCheck -- Inside Cutoff --> LateCancel["422 BOOKING_RULE_VIOLATION: Late Cancellation"]
    CutoffCheck -- Outside Cutoff --> CancelOK["Booking CANCELLED & Credit Restored"]
    CancelOK --> AutoPromote["Waitlist Worker Promotes 1st in Queue"]
    AutoPromote --> PromoteEmail["Outbox: waitlist.promoted Email Sent to Next Member"]
```

### 2.2 Path Specifications
- **Happy Path**: Member finds open yoga session, clicks Book. 1 session credit is consumed from active plan. Outbox triggers confirmation email. User attends or cancels $>2$ hours prior to start; seat is released and credit is returned.
- **Failure Path 1 (Quota Exceeded)**: Member on FREE or Basic tier with 0 sessions remaining clicks Book. System rejects with 402 `UPGRADE_REQUIRED` and displays plan upgrade cards.
- **Failure Path 2 (Late Cancellation)**: Member attempts cancellation 45 minutes prior to class start ($< 2$ hours). System blocks cancellation with 422 `BOOKING_RULE_VIOLATION` explaining the 2-hour cutoff.
- **Failure Path 3 (Concurrent Seat Race)**: Two members click the last remaining seat simultaneously. One wins; the second receives 409 `SESSION_FULL` and is immediately offered waitlist enrollment.

---

## Flow 3: Membership Upgrade with Razorpay Checkout

### 3.1 Diagram
```mermaid
flowchart TD
    MemScreen["View Membership (/app/membership)"] --> ClickUpgrade["Select Pro Plan & Preview"]
    ClickUpgrade --> PreviewCalc["POST /api/v1/me/membership/change-preview"]
    PreviewCalc --> ShowPreview["Display Prorated Net Payable (Full - Unused Credit)"]
    ShowPreview --> ConfirmCheckout["Initiate Checkout (POST /api/v1/memberships/change)"]
    ConfirmCheckout --> LaunchRazorpay["Launch Razorpay Modal (client-payload)"]
    
    LaunchRazorpay --> RzpAction{Gateway Result}
    RzpAction -- User Closes --> CloseErr["Payment Abandoned: Retain current tier"]
    RzpAction -- Bank Decline --> DeclineErr["402 PAYMENT_FAILED: Try another method"]
    RzpAction -- Payment Pending --> PendingWait["Status PENDING: Wait for webhook"]
    RzpAction -- Success Signature --> ConfirmCall["POST /api/v1/payments/:id/confirm"]
    ConfirmCall --> CheckConflict{Stale Preview?}
    CheckConflict -- Yes --> ConflictErr["409 OPTIMISTIC_LOCK: Re-calculate preview"]
    CheckConflict -- No --> ActivePlan["Plan Activated: 16 Sessions Credited"]
```

### 3.2 Path Specifications
- **Happy Path**: Member upgrades from Basic (Rs 499) to Pro (Rs 899). System calculates prorated credit of unused Basic days/sessions, netting Rs 750. User completes Razorpay checkout; signature is confirmed; Pro plan quota (16 sessions) is granted instantly.
- **Failure Path 1 (Card Declined)**: Bank declines charge. Razorpay reports error. Frontend catches event, keeps checkout open, and displays: *"Payment failed: Card declined by issuing bank. Please try another card."*
- **Failure Path 2 (User Abandons Modal)**: User closes payment modal without completing transaction. Checkout state resets; existing plan remains unchanged.
- **Failure Path 3 (Stale Preview Conflict)**: Member waited 25 minutes before completing payment, during which another transaction or billing cycle updated the membership. Confirmation detects version mismatch (`OPTIMISTIC_LOCK`) and prompts the user to review the updated total.

---

## Flow 4: DPDP Consent, Wellness Wizard, Safety Screening & Red-Flag Handling

### 4.1 Diagram
```mermaid
flowchart TD
    Consent["Consent Screen (/app/wellness/consent)"] --> AcceptPurposes["Grant PERSONALIZED_CLASSES & CALORIE_ESTIMATES"]
    AcceptPurposes --> StartWizard["Wellness Wizard (/app/wellness/profile)"]
    StartWizard --> FillBasics["1. Basics (Height, Weight, Age)"]
    FillBasics --> FillInjuries["2. Injuries & Severities"]
    FillInjuries --> FillConditions["3. Coded Health Conditions"]
    FillConditions --> FillParq["4. PAR-Q Questions (Q1 - Q7)"]
    FillParq --> SubmitProfile["PUT /api/v1/wellness/profile"]
    
    SubmitProfile --> ScreenLogic{Screening Engine}
    ScreenLogic -- Any PAR-Q Yes / Severe Injury --> RedFlagVerdict["Verdict: RED_FLAG / CAUTION"]
    ScreenLogic -- Mild or Zero Issues --> AllowedVerdict["Verdict: ALLOWED"]
    
    RedFlagVerdict --> DocBanner["Prominent Doctor Clearance Banner Displayed"]
    DocBanner --> SafeClassesView["Filtered Schedule: High-impact classes RESTRICTED"]
    AllowedVerdict --> SafeClassesView
```

### 4.2 Path Specifications
- **Happy Path**: Member reviews plain-language DPDP consent text, accepts purposes. Member completes wizard steps with zero injuries or heart conditions. System evaluates verdict as `ALLOWED`. All classes are unlocked with metabolic burn guidance.
- **Failure Path 1 (Consent Revoked / Missing)**: Member attempts to access `/app/wellness/profile` without active consent. API returns 428 `CONSENT_REQUIRED` and redirects to the consent screen.
- **Failure Path 2 (PAR-Q Positive / RED_FLAG)**: Member answers "Yes" to Q1 (heart condition advised by doctor). Evaluation yields `RED_FLAG` with `doctorCheckRecommended: true`. High-intensity and HIIT classes are locked (`RESTRICTED`), and a doctor check banner is permanently pinned.
- **Failure Path 3 (Underage Access Attempt)**: Member aged 16 attempts to access nutrition/calorie numbers. Endpoint returns 400 `AGE_RESTRICTED`, explaining that metabolic wellness calculations require age $\ge 18$.

---

## Flow 5: Conversational AI Assistant Booking Proposal & Confirmation

### 5.1 Diagram
```mermaid
flowchart TD
    OpenChat["Open Assistant Panel (/app/assistant)"] --> SendMsg["Send: 'Book me into Wednesday morning HIIT'"]
    SendMsg --> Supervisor["Supervisor Router & Intent Classifier"]
    Supervisor --> AgentRun["PlanBuilderAgent Tool Calling Loop"]
    AgentRun --> SearchTool["Tool: search_sessions"]
    SearchTool --> ProposeTool["Tool: propose_weekly_plan / propose_booking"]
    ProposeTool --> DBAction["Create pending_actions (status: PENDING, 15m expiry)"]
    DBAction --> NumberGuard["NumberGuard Text Validation Check"]
    NumberGuard --> StreamSSE["Stream Reply + Render PendingActionCard"]
    
    StreamSSE --> UserAction{User Decision}
    UserAction -- User Clicks Reject --> RejectCall["POST /api/v1/agent/actions/:id/reject -> REJECTED"]
    UserAction -- 15m Passes --> ExpireJob["PendingActionExpiryJob marks EXPIRED"]
    UserAction -- Says 'Yes' in Chat --> ChatRefuse["Assistant replies: 'Please click Confirm on screen'"]
    UserAction -- User Clicks Confirm --> ConfirmCall["POST /api/v1/agent/actions/:id/confirm"]
    ConfirmCall --> CheckEntitlement{Credits Still Valid?}
    CheckEntitlement -- No --> DenyConfirm["422 Error: Quota exceeded since proposal"]
    CheckEntitlement -- Yes --> ExecuteBooking["Booking Confirmed (Status: CONFIRMED)"]
```

### 5.2 Path Specifications
- **Happy Path**: Member asks assistant for class recommendation. Agent finds open slot, calls `propose_booking`. System renders `PendingActionCard` with 15-minute countdown. Member clicks "Confirm". Backend verifies quota and executes booking; chat displays confirmation receipt.
- **Failure Path 1 (Chat Text Confirmation Refusal)**: User replies in chat: *"Yes, please book it."* Assistant prompt and guardrails forbid text-based writes; assistant replies: *"I have staged this for you, but for your security, please click the on-screen Confirm button."*
- **Failure Path 2 (Proposal Expiry)**: Member receives proposal card but gets distracted for 20 minutes. Background expiry job marks record `EXPIRED`. Clicking Confirm returns 422 `Unprocessable`: *"This action proposal has expired. Please ask again."*
- **Failure Path 3 (AI Unconfigured Fallback)**: Administrator has not configured Gemini key. Assistant panel displays yellow status notice: *"Demonstration mode"*. Tool suggestions fall back strictly to deterministic rule-based results.

---

## Flow 6: Trainer Session Attendance, Completion & Earnings Ledger

### 6.1 Diagram
```mermaid
flowchart TD
    TrainerDash["Trainer Dashboard (/trainer/dashboard)"] --> OpenPrep["Open Class Prep Summary (/trainer/sessions/:id/prep)"]
    OpenPrep --> AggCheck{Enrolled Attendees >= 3?}
    AggCheck -- No --> WithholdNotice["Note: Small group - modifications withheld for privacy"]
    AggCheck -- Yes --> ShowModifications["Show Aggregated Injury Counts & Modification Labels"]
    
    ShowModifications --> OpenSession["Session Detail Roster (/trainer/sessions/:id)"]
    OpenSession --> MarkRoster["Mark ATTENDED or NO_SHOW per Attendee"]
    MarkRoster --> SubmitRoster["POST /api/v1/trainer/sessions/:id/attendance"]
    SubmitRoster --> CompleteSession["Click 'Complete Class' (POST .../complete)"]
    CompleteSession --> StateCheck{Already Completed?}
    StateCheck -- Yes --> DuplicateErr["409 Conflict: Session already marked COMPLETED"]
    StateCheck -- No --> MarkDone["Session Status: COMPLETED"]
    MarkDone --> CalculatePay["Pay Engine credits Trainer Session Rate"]
    CalculatePay --> ViewEarnings["Earnings Ledger (/trainer/earnings) Updated"]
```

### 6.2 Path Specifications
- **Happy Path**: Trainer opens prep summary, views aggregated knee/back modifications (identities withheld). After teaching, marks attendee roster and clicks "Complete Class". Backend transitions session to `COMPLETED` and queues session pay (e.g. Rs 500) into pending payout balance.
- **Failure Path 1 (Small Group Privacy)**: Class has only 2 enrolled attendees. Prep summary suppresses individual injury tags with the notice: *"Attendee count is below the privacy threshold. Anonymous modification counts are withheld to protect member confidentiality."*
- **Failure Path 2 (Premature Completion)**: Trainer attempts to mark session complete 2 hours before the class is scheduled to begin. System blocks action with 422: *"Sessions cannot be marked complete prior to their scheduled start time."*
- **Failure Path 3 (Duplicate Completion Call)**: Network glitch causes duplicate complete requests. Backend handles idempotently via status check, returning existing completion status without double-crediting payout.

---

## Flow 7: Administrator Class Creation, Dual-Admin Payout & Key Management

### 7.1 Diagram
```mermaid
flowchart TD
    AdminDash["Admin Dashboard (/admin/dashboard)"] --> CreateClass["Create Class (/admin/classes)"]
    CreateClass --> ValidateMET["MET Range Validation (met-ranges.yml)"]
    ValidateMET --> ClassCreated["Class Created in DRAFT or ACTIVE State"]
    
    ClassCreated --> ScheduleSessions["Schedule Recurring Sessions (/admin/sessions)"]
    ScheduleSessions --> ConflictCheck{Room or Trainer Overlap?}
    ConflictCheck -- Yes --> ConflictErr["409 Conflict: Studio A occupied at 07:00 AM"]
    ConflictCheck -- No --> SessionsLive["Sessions Published to Schedule"]
    
    AdminDash --> PayoutBatch["Generate Monthly Payout Batch (/admin/payouts)"]
    PayoutBatch --> Admin1Create["Admin 1 Generates Batch (Status: DRAFT)"]
    Admin1Create --> AdminApprove["Admin Approves Batch"]
    AdminApprove --> SameAdminCheck{Same Admin as Creator?}
    SameAdminCheck -- Yes --> DenySelf["403 Forbidden: Dual-admin separation of duty required"]
    SameAdminCheck -- No --> Approved["Batch APPROVED -> Mark Paid & Export CSV"]
    
    AdminDash --> AISettings["AI Settings (/admin/settings/ai)"]
    AISettings --> EnterKey["Enter Gemini API Key (Masked: AIzaSy...xxxx)"]
    EnterKey --> TestConn["POST /api/v1/admin/settings/ai/test"]
    TestConn --> ConnResult{Valid Key?}
    ConnResult -- No --> KeyErr["Connection Failed: Invalid Google API Key"]
    ConnResult -- Yes --> KeySaved["Encrypted at rest with AES-256-GCM"]
```

### 7.2 Path Specifications
- **Happy Path**: Admin creates "Cardio Kickboxing", sets MET value 7.5 (valid per category rules). Schedules recurring Tuesday sessions; conflict engine confirms Studio B is clear. At month end, Admin 1 generates payout batch; Admin 2 reviews and approves batch. Banking CSV is exported.
- **Failure Path 1 (Room Conflict)**: Admin attempts to schedule session in Studio A during an existing Yoga class. Backend detects time overlap and returns 409 `CONFLICT`: *"Studio A is already reserved by Power Vinyasa from 07:00 to 08:00 AM."*
- **Failure Path 2 (Self-Approval of Payouts)**: Admin 1 creates a payout batch and attempts to click "Approve". Backend enforces separation of duties: 403 `FORBIDDEN` (*"A different administrator must approve payout batches."*).
- **Failure Path 3 (Invalid AI Key Test)**: Admin enters an invalid or revoked Gemini key. Connection test returns 400 Bad Request with details masked from client logs; key is not saved.
