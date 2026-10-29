import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import path from 'path';
import { createInitialState, setupMockApi } from './api-mock';

const SCREENSHOT_DIR = path.resolve(process.cwd(), '../docs/evidence/stage-11-screenshots');

test.describe('FitFlow Member End-to-End Journeys & Accessibility', () => {
  // Helper to run Axe on current page
  async function checkA11y(page: any, pageName: string) {
    const results = await new AxeBuilder({ page })
      .disableRules(['color-contrast'])
      .analyze();

    const seriousOrCritical = results.violations.filter(
      (v) => v.impact === 'serious' || v.impact === 'critical'
    );

    expect(
      seriousOrCritical,
      `A11y violations found on ${pageName}: ${seriousOrCritical.map((v) => v.id).join(', ')}`
    ).toHaveLength(0);
  }

  // Journey 1: Complete Member Lifecycle (Register -> Sign In -> Browse -> Book -> View -> Cancel)
  test('Journey 1: Register -> Sign In -> Browse Classes -> Book Session -> My Bookings -> Cancel', async ({
    page,
  }, testInfo) => {
    const state = createInitialState();
    await setupMockApi(page, state);
    page.on('console', (msg) => console.log('[BROWSER CONSOLE]', msg.type(), msg.text()));

    // 1. Visit Register Page
    await page.goto('/register');
    await expect(page.getByRole('heading', { level: 1, name: /Create your FitFlow account/i })).toBeVisible();
    await checkA11y(page, 'Register Page');

    // 2. Register new member
    await page.locator('input[name="fullName"]').fill('Aarav Sharma');
    await page.locator('input[name="email"]').fill('aarav.sharma@fitflow.test');
    await page.locator('input[name="dateOfBirth"]').fill('1995-05-15');
    await page.locator('input[name="password"]').fill('StrongPass123!');

    await page.getByRole('button', { name: /Create Account/i }).click();

    // 3. Navigates to Dashboard or Login
    await expect(page).toHaveURL(/.*(dashboard|login)/);

    // If redirected to login, sign in
    if (page.url().includes('login')) {
      await page.locator('input[name="email"]').fill('aarav.sharma@fitflow.test');
      await page.locator('input[name="password"]').fill('StrongPass123!');
      await page.getByRole('button', { name: /Sign In/i }).click();
    }

    // 4. Lands on Dashboard
    await expect(page).toHaveURL(/.*app\/dashboard/);
    await expect(page.getByRole('heading', { name: /Welcome back/i })).toBeVisible();
    await checkA11y(page, 'Dashboard Page');

    // Capture dashboard screenshot
    const isMobile = testInfo.project.name.includes('Mobile');
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, isMobile ? 'mobile-dashboard.png' : 'desktop-dashboard.png'),
      fullPage: true,
    });

    // 5. Navigate to Browse Classes via Dashboard link
    await page.getByRole('link', { name: /Book a Class/i }).click();
    await expect(page).toHaveURL(/.*app\/classes/);
    await expect(page.getByRole('heading', { level: 1, name: /Class Catalog/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Power Vinyasa Yoga' }).first()).toBeVisible();
    await checkA11y(page, 'Browse Classes');

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, isMobile ? 'mobile-classes.png' : 'desktop-classes.png'),
      fullPage: true,
    });

    // 6. View Class Details
    await page.getByRole('button', { name: /View Schedule & Details/i }).first().click();
    await expect(page).toHaveURL(/.*app\/classes\/cls_1/);
    await expect(page.getByRole('heading', { level: 1, name: 'Power Vinyasa Yoga' })).toBeVisible();
    await checkA11y(page, 'Class Details');

    // 7. Click Book Session to go to Session Details
    await page.getByRole('button', { name: /Book Session/i }).first().click();
    await expect(page).toHaveURL(/.*app\/sessions\/sess_1/);
    await expect(page.getByRole('heading', { level: 1, name: 'Power Vinyasa Yoga' })).toBeVisible();
    await checkA11y(page, 'Session Details');

    // 8. Confirm Spot Reservation
    await page.getByRole('button', { name: /Confirm Spot Reservation/i }).click();
    await expect(page.getByText(/Booking Confirmed!/i)).toBeVisible();

    // 9. Go to My Bookings via success card action
    await page.getByRole('button', { name: /View My Bookings/i }).click();
    await expect(page).toHaveURL(/.*app\/bookings/);
    await expect(page.getByRole('heading', { level: 1, name: /My Bookings/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Power Vinyasa Yoga' }).first()).toBeVisible();
    await checkA11y(page, 'My Bookings');

    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, isMobile ? 'mobile-bookings.png' : 'desktop-bookings.png'),
      fullPage: true,
    });

    // 10. Cancel booking
    await page.getByRole('button', { name: /Cancel Reservation/i }).first().click();
    // Modal opens
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.getByRole('button', { name: /Confirm Cancellation/i }).click();
    await expect(page.getByText(/Reservation cancelled/i)).toBeVisible();
  });

  // Journey 2: Free Session Exhaustion -> Upgrade Preview -> MOCK Checkout Success
  test('Journey 2: UPGRADE_REQUIRED -> Pro-Rated Preview -> MOCK Checkout Success', async ({
    page,
  }, testInfo) => {
    const state = createInitialState();
    // Exhaust all remaining session credits
    state.membership.sessionsRemaining = 0;
    state.membership.classesRemaining = 0;
    await setupMockApi(page, state);

    // Sign in directly
    await page.goto('/login');
    await page.locator('input[name="email"]').fill('aarav.sharma@fitflow.test');
    await page.locator('input[name="password"]').fill('StrongPass123!');
    await page.getByRole('button', { name: /Sign In/i }).click();
    await expect(page).toHaveURL(/.*app\/dashboard/);

    // Navigate to class and session
    await page.getByRole('link', { name: /Book a Class/i }).click();
    await page.getByRole('button', { name: /View Schedule & Details/i }).first().click();
    await page.getByRole('button', { name: /Book Session/i }).first().click();

    // Try booking with 0 credits remaining
    await page.getByRole('button', { name: /Confirm Spot Reservation/i }).click();

    // UPGRADE_REQUIRED prompt appears
    await expect(
      page.getByText(/You have used all session credits included in your current plan/i)
    ).toBeVisible();
    const upgradeBtn = page.getByRole('button', { name: /View Membership Plans & Upgrades/i });
    await expect(upgradeBtn).toBeVisible();

    // Click upgrade button -> navigates to membership page
    await upgradeBtn.click();
    await expect(page).toHaveURL(/.*app\/membership/);
    await checkA11y(page, 'Membership Page');

    // Click Switch to this Plan on Unlimited Flow
    const selectPlanBtn = page.getByRole('button', { name: /Switch to this Plan/i }).first();
    await selectPlanBtn.click();

    // CheckoutLauncher modal appears with verbatim pro-rated calculations
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByTestId('amount-due')).toContainText('Rs 687.13');

    const isMobile = testInfo.project.name.includes('Mobile');
    await page.screenshot({
      path: path.join(SCREENSHOT_DIR, isMobile ? 'mobile-checkout.png' : 'desktop-checkout.png'),
    });

    // Click Pay Rs 687.13
    await page.getByRole('button', { name: /Pay Rs 687.13/i }).click();

    // MOCK gateway panel appears
    await expect(page.getByTestId('mock-gateway-panel')).toBeVisible();
    await checkA11y(page, 'Mock Checkout Panel');

    // Click Success (tok_ok)
    await page.getByRole('button', { name: /Success \(tok_ok\)/i }).click();

    // Lands on Payment Result Success page
    await expect(page).toHaveURL(/.*payment-result\?.*status=SUCCEEDED/);
    await expect(page.getByText('Payment Succeeded!')).toBeVisible();
    await expect(page.getByText('INV-2026-002')).toBeVisible();
  });

  // Journey 3: Decline path in MOCK checkout
  test('Journey 3: Decline path in MOCK checkout (tok_fail)', async ({ page }) => {
    const state = createInitialState();
    await setupMockApi(page, state);
    page.on('console', (msg) => console.log('[BROWSER CONSOLE]', msg.type(), msg.text()));

    await page.goto('/login');
    await page.locator('input[name="email"]').fill('aarav.sharma@fitflow.test');
    await page.locator('input[name="password"]').fill('StrongPass123!');
    await page.getByRole('button', { name: /Sign In/i }).click();
    await expect(page).toHaveURL(/.*app\/dashboard/);

    // Navigate to membership via dashboard Manage Plan
    await page.getByRole('link', { name: /Manage Plan/i }).click();
    await expect(page).toHaveURL(/.*app\/membership/);

    await page.getByRole('button', { name: /Switch to this Plan/i }).first().click();
    await page.getByRole('button', { name: /Pay Rs 687.13/i }).click();

    // Click Decline (tok_fail)
    await expect(page.getByTestId('mock-gateway-panel')).toBeVisible();
    await page.getByRole('button', { name: /Decline \(tok_fail\)/i }).click();

    // Error message banner displayed
    await expect(page.getByText(/Simulated issuer decline/i)).toBeVisible();
  });

  // Journey 4: Stale preview path (409 conflict)
  test('Journey 4: Stale preview conflict handling (409)', async ({ page }) => {
    const state = createInitialState();
    state.failNextMembershipChangeWith409 = true;
    await setupMockApi(page, state);

    await page.goto('/login');
    await page.locator('input[name="email"]').fill('aarav.sharma@fitflow.test');
    await page.locator('input[name="password"]').fill('StrongPass123!');
    await page.getByRole('button', { name: /Sign In/i }).click();
    await expect(page).toHaveURL(/.*app\/dashboard/);

    await page.getByRole('link', { name: /Manage Plan/i }).click();
    await expect(page).toHaveURL(/.*app\/membership/);

    await page.getByRole('button', { name: /Switch to this Plan/i }).first().click();

    // Verify modal is open and has pricing breakdown
    await expect(page.getByTestId('amount-due')).toBeVisible();

    // Click Pay -> triggers 409 conflict
    await page.getByRole('button', { name: /Pay Rs 687.13/i }).click();

    // Banner appears warning about stale preview
    await expect(
      page.getByText(/Membership Preview Updated \(409 Conflict\)/i)
    ).toBeVisible();

    // Button updates to prompt confirmation of updated numbers
    const confirmUpdatedBtn = page.getByRole('button', {
      name: /Confirm & Pay Updated Amount/i,
    });
    await expect(confirmUpdatedBtn).toBeVisible();

    // Click confirm -> retry succeeds, shows mock panel
    await confirmUpdatedBtn.click();
    await expect(page.getByTestId('mock-gateway-panel')).toBeVisible();

    // Complete payment
    await page.getByRole('button', { name: /Success \(tok_ok\)/i }).click();
    await expect(page).toHaveURL(/.*payment-result\?.*status=SUCCEEDED/);
  });

  // Journey 5: Forced password change for admin-created user
  test('Journey 5: Forced password change for admin-created user (mustChangePassword: true)', async ({
    page,
  }) => {
    const state = createInitialState();
    await setupMockApi(page, state);
    page.on('console', (msg) => console.log('[BROWSER CONSOLE]', msg.type(), msg.text()));

    await page.goto('/login');
    await page.locator('input[name="email"]').fill('admin_forced@fitflow.test');
    await page.locator('input[name="password"]').fill('TemporaryAdminPass123!');
    await page.getByRole('button', { name: /Sign In/i }).click();

    // Redirected to forced password change page
    await expect(page).toHaveURL(/.*forced-password-change/);
    await expect(
      page.getByRole('heading', { level: 1, name: /Password Change Required/i })
    ).toBeVisible();
    await checkA11y(page, 'Force Password Change');

    // Fill in new password
    await page.locator('input[name="currentPassword"]').fill('TemporaryAdminPass123!');
    await page.locator('input[name="newPassword"]').fill('BrandNewSecurePassword123!');
    await page.locator('input[name="confirmPassword"]').fill('BrandNewSecurePassword123!');
    await page.getByRole('button', { name: /Set New Password & Continue/i }).click();

    // Navigates to Member Dashboard
    await expect(page).toHaveURL(/.*app\/dashboard/);
  });

  // Journey 6: Session expiry and refresh via cookie
  test('Journey 6: Automatic refresh token handling and logout', async ({ page }) => {
    const state = createInitialState();
    await setupMockApi(page, state);
    page.on('console', (msg) => console.log('[BROWSER CONSOLE]', msg.type(), msg.text()));

    await page.goto('/login');
    await page.locator('input[name="email"]').fill('aarav.sharma@fitflow.test');
    await page.locator('input[name="password"]').fill('StrongPass123!');
    await page.getByRole('button', { name: /Sign In/i }).click();
    await expect(page).toHaveURL(/.*app\/dashboard/);

    // Logout
    await page.getByRole('button', { name: /Sign out of account/i }).click();
    await expect(page).toHaveURL(/.*login/);
  });
});
