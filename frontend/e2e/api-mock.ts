import type { Page, Route } from '@playwright/test';

export interface MockBackendState {
  user: any;
  membership: any;
  bookings: any[];
  payments: any[];
  refreshCookieSet: boolean;
  mustChangePassword: boolean;
  failNextMembershipChangeWith409?: boolean;
}

export function createInitialState(): MockBackendState {
  return {
    user: {
      id: 'mem_1',
      email: 'aarav.sharma@fitflow.test',
      fullName: 'Aarav Sharma',
      roles: ['MEMBER'],
      phone: '+91 98765 43210',
      version: 1,
      createdAt: '2026-10-01T00:00:00Z',
    },
    membership: {
      id: 'mship_1',
      userId: 'mem_1',
      planId: 'plan_starter',
      planName: 'Starter Flow',
      status: 'ACTIVE',
      monthlyPricePaise: 49900,
      sessionsTotal: 8,
      sessionsRemaining: 5,
      classesPerMonth: 8,
      classesRemaining: 5,
      freeSessionsRemaining: 0,
      currentPeriodStart: '2026-10-01T00:00:00Z',
      currentPeriodEnd: '2026-10-31T23:59:59Z',
      tier: 'STARTER',
    },
    bookings: [
      {
        id: 'bk_1',
        sessionId: 'sess_1',
        classTitle: 'Power Vinyasa Yoga',
        category: 'YOGA',
        trainerName: 'Ananya Sharma',
        roomName: 'Studio A',
        startsAt: '2026-10-30T10:00:00Z',
        endsAt: '2026-10-30T11:00:00Z',
        status: 'CONFIRMED',
        bookedAt: '2026-10-28T09:00:00Z',
      },
    ],
    payments: [
      {
        id: 'pay_123',
        amountPaise: 49900,
        currency: 'INR',
        status: 'SUCCEEDED',
        gateway: 'RAZORPAY',
        invoiceId: 'inv_123',
        invoiceNumber: 'INV-2026-001',
        createdAt: '2026-10-01T10:00:00Z',
      },
    ],
    refreshCookieSet: true,
    mustChangePassword: false,
  };
}

export async function setupMockApi(page: Page, state: MockBackendState) {
  await page.route('**/api/v1/**', async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();

    // 1. Auth routes
    if (path.endsWith('/auth/login') && method === 'POST') {
      const body = request.postDataJSON() || {};
      if (body.email === 'admin_forced@fitflow.test') {
        state.mustChangePassword = true;
      }
      return route.fulfill({
        status: 200,
        headers: {
          'Set-Cookie': 'ff_refresh=mock-refresh-token-aarav; Path=/api/v1/auth; HttpOnly; SameSite=Strict',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          accessToken: 'fake-jwt-token-aarav',
          user: {
            id: state.user.id,
            email: body.email || state.user.email,
            fullName: state.user.fullName,
            roles: ['MEMBER'],
            mustChangePassword: state.mustChangePassword,
          },
        }),
      });
    }

    if (path.endsWith('/auth/register') && method === 'POST') {
      const body = request.postDataJSON() || {};
      return route.fulfill({
        status: 201,
        headers: {
          'Set-Cookie': 'ff_refresh=mock-refresh-token-aarav; Path=/api/v1/auth; HttpOnly; SameSite=Strict',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          accessToken: 'fake-jwt-token-aarav',
          user: {
            id: 'mem_new_1',
            email: body.email,
            fullName: body.fullName,
            roles: ['MEMBER'],
            mustChangePassword: false,
          },
        }),
      });
    }

    if (path.endsWith('/auth/refresh') && method === 'POST') {
      return route.fulfill({
        status: 200,
        headers: {
          'Set-Cookie': 'ff_refresh=mock-refresh-token-aarav-refreshed; Path=/api/v1/auth; HttpOnly; SameSite=Strict',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ accessToken: 'fake-jwt-refreshed-aarav' }),
      });
    }

    if (path.endsWith('/auth/logout') && method === 'POST') {
      return route.fulfill({
        status: 200,
        headers: {
          'Set-Cookie': 'ff_refresh=; Path=/api/v1/auth; Max-Age=0',
        },
        body: '',
      });
    }

    if (path.endsWith('/auth/change-password') && method === 'POST') {
      state.mustChangePassword = false;
      return route.fulfill({ status: 200, body: '' });
    }

    // 2. Member profile & membership
    if (path.endsWith('/me') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.user),
      });
    }

    if (path.endsWith('/me') && (method === 'PUT' || method === 'PATCH')) {
      const body = request.postDataJSON() || {};
      state.user = { ...state.user, ...body };
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.user),
      });
    }

    if (path.endsWith('/me/membership') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(state.membership),
      });
    }

    if (path.endsWith('/me/payments') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          items: state.payments,
          totalElements: state.payments.length,
          page: 0,
          size: 20,
          totalPages: 1,
        }),
      });
    }

    // 3. Classes and Sessions
    if (path.endsWith('/classes/metadata') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          categories: ['YOGA', 'HIIT', 'STRENGTH', 'CARDIO', 'PILATES'],
          intensities: ['LOW', 'MEDIUM', 'HIGH'],
          rooms: ['Studio A', 'Studio B', 'Main Studio'],
        }),
      });
    }

    if (path.endsWith('/classes') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'cls_1',
              title: 'Power Vinyasa Yoga',
              description: 'A dynamic flow sequence linking breath with vigorous physical postures.',
              category: 'YOGA',
              intensity: 'MEDIUM',
              durationMinutes: 60,
              capacityDefault: 20,
              active: true,
            },
            {
              id: 'cls_2',
              title: 'High-Intensity Interval Training (HIIT)',
              description: 'Full-body conditioning with timed high-intensity intervals.',
              category: 'HIIT',
              intensity: 'HIGH',
              durationMinutes: 45,
              capacityDefault: 15,
              active: true,
            },
          ],
          totalElements: 2,
        }),
      });
    }

    if (path.match(/\/classes\/cls_1$/) && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'cls_1',
          title: 'Power Vinyasa Yoga',
          description: 'A dynamic flow sequence linking breath with vigorous physical postures.',
          category: 'YOGA',
          intensity: 'MEDIUM',
          durationMinutes: 60,
          capacityDefault: 20,
          active: true,
        }),
      });
    }

    if (path.endsWith('/sessions') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [
            {
              id: 'sess_1',
              classId: 'cls_1',
              classTitle: 'Power Vinyasa Yoga',
              category: 'YOGA',
              intensity: 'MEDIUM',
              trainerName: 'Ananya Sharma',
              roomName: 'Studio A',
              startsAt: '2026-10-30T10:00:00Z',
              endsAt: '2026-10-30T11:00:00Z',
              capacity: 20,
              bookedCount: 12,
              status: 'SCHEDULED',
              safetyVerdict: 'ALLOWED',
            },
          ],
          totalElements: 1,
        }),
      });
    }

    if (path.match(/\/sessions\/sess_1$/) && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'sess_1',
          classId: 'cls_1',
          classTitle: 'Power Vinyasa Yoga',
          category: 'YOGA',
          intensity: 'MEDIUM',
          trainerName: 'Ananya Sharma',
          roomName: 'Studio A',
          startsAt: '2026-10-30T10:00:00Z',
          endsAt: '2026-10-30T11:00:00Z',
          capacity: 20,
          bookedCount: 12,
          status: 'SCHEDULED',
          safetyVerdict: 'ALLOWED',
        }),
      });
    }

    // 4. Bookings
    if (path.endsWith('/me/bookings') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: state.bookings,
          totalElements: state.bookings.length,
        }),
      });
    }

    if (path.endsWith('/bookings') && method === 'POST') {
      const body = request.postDataJSON() || {};
      if (
        (state.membership.sessionsRemaining !== undefined && state.membership.sessionsRemaining <= 0) ||
        (state.membership.classesRemaining !== undefined && state.membership.classesRemaining <= 0)
      ) {
        return route.fulfill({
          status: 402,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 'UPGRADE_REQUIRED',
            detail: 'You have used all session credits in your current membership.',
          }),
        });
      }

      if (state.membership.sessionsRemaining !== undefined) state.membership.sessionsRemaining -= 1;
      if (state.membership.classesRemaining !== undefined) state.membership.classesRemaining -= 1;
      const newBooking = {
        id: `bk_${Date.now()}`,
        sessionId: body.sessionId,
        classTitle: 'Power Vinyasa Yoga',
        category: 'YOGA',
        trainerName: 'Ananya Sharma',
        roomName: 'Studio A',
        startsAt: '2026-10-30T10:00:00Z',
        endsAt: '2026-10-30T11:00:00Z',
        status: 'CONFIRMED',
        bookedAt: new Date().toISOString(),
      };
      state.bookings.push(newBooking);

      return route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(newBooking),
      });
    }

    if (path.match(/\/bookings\/.*$/) && method === 'DELETE') {
      state.bookings = [];
      state.membership.classesRemaining += 1;
      return route.fulfill({ status: 200, body: '' });
    }

    // 5. Membership preview & upgrade
    if ((path.endsWith('/plans') || path.endsWith('/memberships/plans')) && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: 'plan_starter',
            name: 'Starter Flow',
            description: 'Perfect for regular wellness practice',
            pricePaise: 49900,
            sessionsPerMonth: 8,
            classesPerMonth: 8,
            features: ['8 studio sessions included', 'Online booking window', 'Standard locker access'],
            active: true,
          },
          {
            id: 'plan_unlimited',
            name: 'Unlimited Flow',
            description: 'For dedicated fitness enthusiasts',
            pricePaise: 99900,
            sessionsPerMonth: 999,
            classesPerMonth: 999,
            features: ['Unlimited group classes', 'Priority booking window', '1-on-1 monthly consultation'],
            active: true,
          },
        ]),
      });
    }

    if (path.includes('change-preview')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          targetPlanId: 'plan_unlimited',
          targetPlanName: 'Unlimited Flow',
          targetPricePaise: 99900,
          creditPaise: 31187,
          amountDuePaise: 68713,
          refundPaise: 0,
          usedRatio: 0.375,
          unusedRatio: 0.625,
          explanationLines: [
            'Pro-rated credit applied from remaining days in Starter Flow.',
            'Net amount payable: Rs 687.13 including applicable GST.',
          ],
        }),
      });
    }

    if (path.endsWith('/memberships/change') && method === 'POST') {
      const body = request.postDataJSON() || {};
      if (state.failNextMembershipChangeWith409 || body.expectedAmountDuePaise === 11111) {
        state.failNextMembershipChangeWith409 = false;
        // Stale preview conflict
        return route.fulfill({
          status: 409,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 'STALE_PREVIEW',
            detail: 'The pricing preview expired. Please review updated pro-rated calculations.',
          }),
        });
      }

      return route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'REQUIRES_PAYMENT',
          paymentId: 'pay_mock_upgrade',
        }),
      });
    }

    if (path.match(/\/payments\/pay_mock_upgrade\/client-payload$/) && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          paymentId: 'pay_mock_upgrade',
          gateway: 'MOCK',
          amountPaise: 68713,
          currency: 'INR',
          keyId: 'rzp_mock_key',
          orderId: 'order_mock_upgrade',
          email: state.user.email,
        }),
      });
    }

    if (path.match(/\/payments\/pay_mock_upgrade\/confirm$/) && method === 'POST') {
      const body = request.postDataJSON() || {};
      if (body.mockToken === 'tok_fail') {
        return route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 'PAYMENT_FAILED',
            detail: 'Simulated issuer decline (tok_fail).',
          }),
        });
      }

      // Success
      state.membership = {
        id: 'mship_unlimited',
        userId: 'mem_1',
        planId: 'plan_unlimited',
        planName: 'Unlimited Flow',
        status: 'ACTIVE',
        monthlyPricePaise: 99900,
        classesPerMonth: 999,
        classesRemaining: 999,
        currentPeriodStart: '2026-10-29T00:00:00Z',
        currentPeriodEnd: '2026-11-28T23:59:59Z',
        tier: 'UNLIMITED',
      };

      state.payments.push({
        id: 'pay_mock_upgrade',
        amountPaise: 68713,
        currency: 'INR',
        status: 'SUCCEEDED',
        gateway: 'MOCK',
        invoiceId: 'inv_mock_upgrade',
        invoiceNumber: 'INV-2026-002',
        createdAt: new Date().toISOString(),
      });

      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'pay_mock_upgrade',
          amountPaise: 68713,
          currency: 'INR',
          status: 'SUCCEEDED',
          gateway: 'MOCK',
          invoiceNumber: 'INV-2026-002',
          createdAt: new Date().toISOString(),
        }),
      });
    }

    if (path.match(/\/payments\/pay_mock_upgrade$/) && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'pay_mock_upgrade',
          amountPaise: 68713,
          currency: 'INR',
          status: 'SUCCEEDED',
          gateway: 'MOCK',
          invoiceNumber: 'INV-2026-002',
          createdAt: new Date().toISOString(),
        }),
      });
    }

    // Default fallback
    return route.continue();
  });
}
