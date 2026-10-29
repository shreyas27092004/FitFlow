# FitFlow Master Copy Deck (docs/design/copy-deck.md)

This copy deck provides the official, approved text for all user-facing interactions in FitFlow. Sentences are intentionally kept short, friendly, plain, and non-medical.

---

## 1. Problem+JSON Error Messages & Recovery Actions

### `UPGRADE_REQUIRED` (HTTP 402)
- **Title**: *"Session Limit Reached"*
- **Message**: *"You have used all session credits included in your current plan."*
- **Action Button**: *"Explore Membership Plans"*

### `SESSION_FULL` (HTTP 409)
- **Title**: *"Class Full"*
- **Message**: *"All 20 spots in this session are currently reserved."*
- **Action Button**: *"Join Waitlist"*

### `CONSENT_REQUIRED` (HTTP 428)
- **Title**: *"Health Consent Needed"*
- **Message**: *"To view personalized workout suggestions and calorie calculations, please review and accept our health data consent."*
- **Action Button**: *"Review Consent Settings"*

### `AGE_RESTRICTED` (HTTP 400)
- **Title**: *"Age Requirement Notice"*
- **Message**: *"In accordance with the DPDP Act and safety guidelines, wellness profiles and nutritional estimates are available for members aged 18 and older."*
- **Action Button**: *"Back to Dashboard"*

### `BOOKING_RULE_VIOLATION` (HTTP 422)
- **Title**: *"Booking Window Closed"*
- **Message**: *"Classes lock 2 hours before start time. Cancellations and bookings are no longer permitted for this session."*
- **Action Button**: *"Browse Other Sessions"*

### `LOCKED` (HTTP 429)
- **Title**: *"Account Temporarily Locked"*
- **Message**: *"For your security, sign in is paused for 15 minutes following repeated unsuccessful attempts. Please check your password and try again shortly."*
- **Action Button**: *"Reset Password"*

### `RATE_LIMITED` (HTTP 429)
- **Title**: *"Please Slow Down"*
- **Message**: *"We received too many requests in a short period. Please wait a moment before trying again."*
- **Action Button**: *"Dismiss"*

### `OPTIMISTIC_LOCK` (HTTP 409)
- **Title**: *"Schedule Updated"*
- **Message**: *"Class availability changed while you were viewing this page. We've refreshed the latest spots for you."*
- **Action Button**: *"Refresh Now"*

### `PAYMENT_FAILED` (HTTP 402)
- **Title**: *"Payment Not Completed"*
- **Message**: *"Your issuing bank could not process this payment. No money was deducted from your account."*
- **Action Button**: *"Try Another Payment Method"*

### `UNAUTHENTICATED` (HTTP 401)
- **Title**: *"Session Expired"*
- **Message**: *"Your sign-in session has expired. Please sign in again to continue."*
- **Action Button**: *"Sign In"*

### `FORBIDDEN` (HTTP 403)
- **Title**: *"Access Restricted"*
- **Message**: *"You don't have permission to view this section or perform this administrative action."*
- **Action Button**: *"Go to Home"*

### `VALIDATION_FAILED` (HTTP 400)
- **Title**: *"Please Check Your Information"*
- **Message**: *"One or more fields require correction. Please review the highlighted items below."*
- **Action Button**: *"Review Errors"*

### `INTERNAL_ERROR` (HTTP 500)
- **Title**: *"Something Went Wrong"*
- **Message**: *"We're experiencing temporary technical difficulties on our end. Please try again in a few moments."*
- **Action Button**: *"Try Again"*

---

## 2. Empty States Copy

- **No Classes Found**: *"No classes match your current filters. Try selecting 'All Categories' or clearing your time range."*
- **No Upcoming Bookings**: *"You have no upcoming classes scheduled. Ready to move? Browse our weekly studio calendar."*
- **No Attendance History**: *"You haven't attended any classes yet. Your completed workouts and calorie summaries will appear here."*
- **No Invoices Found**: *"No past invoices found for this account."*
- **No Pending AI Actions**: *"No pending action requests. Everything is confirmed and up to date."*
- **No Members Found**: *"No users match your search criteria."*

---

## 3. Destructive Action Confirmations

### Cancel Class Booking
- **Header**: *"Cancel this class booking?"*
- **Body**: *"Are you sure you want to cancel Power Vinyasa Yoga on Tuesday, 03 Nov at 07:00 AM? Your session credit will be returned to your active plan."*
- **Confirm**: *"Yes, Cancel Booking"*
- **Cancel**: *"Keep My Spot"*

### Cancel Membership Subscription
- **Header**: *"Cancel Membership?"*
- **Body**: *"Your subscription will remain active until the end of your billing cycle on 15 Nov 2026. You will retain access to remaining session credits until that date."*
- **Confirm**: *"Yes, Cancel Subscription"*
- **Cancel**: *"Never Mind"*

### Permanent Health Data Deletion (Typed Confirmation)
- **Header**: *"Permanently Delete All Health Records?"*
- **Body**: *"This action is permanent and cannot be undone. All your encrypted health metrics, injury profiles, and safety preferences will be immediately deleted from our databases. To confirm, please type DELETE below:"*
- **Confirm**: *"Delete Everything"*
- **Cancel**: *"Cancel"*

---

## 4. Consent Screen Framing & Notices

- **Introductory Framing**: *"Under India's Digital Personal Data Protection (DPDP) Act, your health and wellness information requires explicit, informed consent. We use this information solely to personalize class recommendations, verify physical safety, and estimate energy expenditure."*
- **Data Isolation Guarantee**: *"Your wellness data is stored in dedicated encrypted tables. Only you can view your personal health profile. Instructors receive only anonymous group summaries, and administrators have zero access."*
- **Right to Withdraw**: *"You have the right to withdraw consent, export your full health records, or permanently delete your wellness data at any time from your account settings."*

---

## 5. Wellness & Medical Disclaimers

### Universal Wellness Disclaimer
> **Wellness suggestions, not medical advice.** Rules are illustrative and must be reviewed by a qualified professional before real use.

### Doctor Clearance Recommendation
> **Doctor Consultation Recommended:** Based on your screening questionnaire responses, we recommend consulting a qualified medical doctor or healthcare professional before participating in vigorous studio exercises.

### Small-Group Privacy Notice (Trainer Prep)
> **Small-Group Privacy Protection:** When fewer than 3 attendees are enrolled in a session, individual injury and modification tags are withheld to maintain personal health confidentiality.

---

## 6. AI Assistant States & Action Framing

- **AI Identity Label**: *"[FitFlow Assistant]"*
- **Mock Mode Status Banner**: *"Assistant running in demonstration / rule-based mode. Model responses are generated using local rules."*
- **Pending Action Card Notice**: *"Please review the details below. Our AI assistant cannot book or alter your account without your explicit confirmation."*
- **Circuit Breaker Fallback**: *"The AI assistant is temporarily unavailable. You can still use the app screens to browse and book classes directly."*

---

## 7. Payment Results & Receipts

- **Payment Successful**: *"Payment confirmed! Rs. 899.00 received. Your 16 FitFlow Pro monthly session credits are now active."*
- **Payment Processing**: *"We are confirming your payment with Razorpay. This page will update automatically in a few seconds."*
- **Payment Cancelled**: *"Payment was cancelled. Your previous membership status remains active."*

---

## 8. Transactional Email Notices

- **Booking Confirmation Notice**: *"We sent your booking confirmation and calendar invite to alice@fitflow.local."*
- **Password Reset Sent**: *"If an account is registered with that email, we've sent password reset instructions."*
- **Waitlist Promotion Notice**: *"A spot opened up! We've reserved your place in Athletic HIIT and emailed your confirmation."*
- **Refund Approved Notice**: *"Your refund of Rs. 499.00 was approved and will reflect on your card in 5–7 business days."*
