import { http, HttpResponse } from 'msw';

export const handlers = [
  // Auth handlers
  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as any;
    if (body.email === 'locked@fitflow.test') {
      return HttpResponse.json(
        {
          code: 'ACCOUNT_LOCKED',
          title: 'Account Locked',
          detail: 'Account temporarily locked for 15 minutes due to multiple failed sign-in attempts.',
          retryAfter: 900,
        },
        { status: 429 }
      );
    }
    if (body.password === 'WrongPass123') {
      return HttpResponse.json(
        {
          code: 'INVALID_CREDENTIALS',
          title: 'Invalid Credentials',
          detail: 'Invalid email or password.',
        },
        { status: 401 }
      );
    }
    return HttpResponse.json({
      accessToken: 'fake-jwt-token-12345',
      refreshToken: null, // browser request: token in HttpOnly cookie only
      user: {
        id: 'usr_member_1',
        email: body.email,
        fullName: 'Test Member',
        roles: ['MEMBER'],
        mustChangePassword: body.email === 'mustchange@fitflow.test',
      },
    });
  }),

  http.post('/api/v1/auth/register', async ({ request }) => {
    const body = (await request.json()) as any;
    return HttpResponse.json({
      accessToken: 'fake-jwt-registered-12345',
      user: {
        id: 'usr_new_1',
        email: body.email,
        fullName: body.fullName,
        roles: ['MEMBER'],
      },
    });
  }),

  http.post('/api/v1/auth/refresh', ({ request }) => {
    const csrfHeader = request.headers.get('X-Requested-With');
    if (csrfHeader !== 'fitflow') {
      return HttpResponse.json(
        { code: 'FORBIDDEN', detail: 'Missing CSRF header' },
        { status: 403 }
      );
    }
    return HttpResponse.json({
      accessToken: 'fake-jwt-refreshed-67890',
    });
  }),

  http.post('/api/v1/auth/logout', () => {
    return new HttpResponse(null, { status: 200 });
  }),

  http.post('/api/v1/auth/forgot-password', () => {
    return HttpResponse.json({ message: 'Reset email sent if account exists.' });
  }),

  http.post('/api/v1/auth/reset-password', () => {
    return HttpResponse.json({ message: 'Password updated successfully.' });
  }),

  http.post('/api/v1/auth/change-password', () => {
    return new HttpResponse(null, { status: 200 });
  }),

  // Classes & Sessions
  http.get('/api/v1/classes', () => {
    return HttpResponse.json({
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
    });
  }),

  http.get('/api/v1/classes/metadata', () => {
    return HttpResponse.json({
      categories: ['YOGA', 'HIIT', 'STRENGTH', 'CARDIO', 'PILATES'],
      intensities: ['LOW', 'MEDIUM', 'HIGH'],
      rooms: ['Studio A', 'Studio B', 'Spin Room'],
    });
  }),

  http.get('/api/v1/classes/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      title: 'Power Vinyasa Yoga',
      description: 'A dynamic flow sequence linking breath with vigorous physical postures.',
      category: 'YOGA',
      intensity: 'MEDIUM',
      durationMinutes: 60,
      capacityDefault: 20,
      active: true,
    });
  }),

  http.get('/api/v1/sessions', () => {
    return HttpResponse.json({
      content: [
        {
          id: 'sess_1',
          classId: 'cls_1',
          classTitle: 'Power Vinyasa Yoga',
          category: 'YOGA',
          intensity: 'MEDIUM',
          trainerId: 'trn_1',
          trainerName: 'Ananya Sharma',
          roomName: 'Studio A',
          startsAt: '2026-11-01T07:00:00Z',
          endsAt: '2026-11-01T08:00:00Z',
          capacity: 20,
          bookedCount: 12,
          status: 'SCHEDULED',
        },
      ],
      totalElements: 1,
    });
  }),

  http.get('/api/v1/sessions/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      classId: 'cls_1',
      classTitle: 'Power Vinyasa Yoga',
      category: 'YOGA',
      intensity: 'MEDIUM',
      trainerId: 'trn_1',
      trainerName: 'Ananya Sharma',
      roomName: 'Studio A',
      startsAt: '2026-11-01T07:00:00Z',
      endsAt: '2026-11-01T08:00:00Z',
      capacity: 20,
      bookedCount: 12,
      status: 'SCHEDULED',
    });
  }),

  // Bookings
  http.get('/api/v1/me/bookings', () => {
    return HttpResponse.json({
      content: [
        {
          id: 'bk_1',
          sessionId: 'sess_1',
          classTitle: 'Power Vinyasa Yoga',
          category: 'YOGA',
          trainerName: 'Ananya Sharma',
          roomName: 'Studio A',
          startsAt: '2026-11-01T07:00:00Z',
          endsAt: '2026-11-01T08:00:00Z',
          status: 'CONFIRMED',
          bookedAt: '2026-10-25T10:00:00Z',
        },
      ],
      totalElements: 1,
    });
  }),

  http.post('/api/v1/bookings', async ({ request }) => {
    const body = (await request.json()) as any;
    if (body.sessionId === 'sess_full') {
      return HttpResponse.json(
        { code: 'SESSION_FULL', detail: 'This session is full.' },
        { status: 409 }
      );
    }
    if (body.sessionId === 'sess_upgrade_needed') {
      return HttpResponse.json(
        { code: 'UPGRADE_REQUIRED', detail: 'Session allowance exhausted.' },
        { status: 402 }
      );
    }
    if (body.sessionId === 'sess_waitlist') {
      return HttpResponse.json(
        {
          id: 'bk_wait_1',
          sessionId: body.sessionId,
          classTitle: 'Waitlisted Session',
          category: 'HIIT',
          trainerName: 'Rahul Verma',
          roomName: 'Studio B',
          startsAt: '2026-11-02T08:00:00Z',
          endsAt: '2026-11-02T09:00:00Z',
          status: 'WAITLISTED',
          waitlistPosition: 2,
          bookedAt: '2026-10-29T10:00:00Z',
        },
        { status: 202 }
      );
    }

    return HttpResponse.json(
      {
        id: 'bk_new_1',
        sessionId: body.sessionId,
        classTitle: 'Power Vinyasa Yoga',
        category: 'YOGA',
        trainerName: 'Ananya Sharma',
        roomName: 'Studio A',
        startsAt: '2026-11-01T07:00:00Z',
        endsAt: '2026-11-01T08:00:00Z',
        status: 'CONFIRMED',
        bookedAt: '2026-10-29T10:00:00Z',
      },
      { status: 201 }
    );
  }),

  http.delete('/api/v1/bookings/:id', () => {
    return new HttpResponse(null, { status: 204 });
  }),

  // Memberships & Plans
  http.get('/api/v1/plans', () => {
    return HttpResponse.json([
      {
        id: 'plan_starter',
        name: 'Starter Flow',
        description: 'Ideal for beginners establishing a regular rhythm.',
        pricePaise: 49900,
        sessionsPerMonth: 8,
        features: ['8 Classes per month', 'Studio booking access', 'Free mat rental'],
        active: true,
      },
      {
        id: 'plan_unlimited',
        name: 'Unlimited Flow',
        description: 'Unlimited access across all styles and workshops.',
        pricePaise: 99900,
        sessionsPerMonth: 30,
        features: ['Unlimited Classes', 'Priority waitlist', 'Guest passes'],
        active: true,
      },
    ]);
  }),

  http.get('/api/v1/me/membership', () => {
    return HttpResponse.json({
      id: 'mem_1',
      planId: 'plan_starter',
      planName: 'Starter Flow',
      status: 'ACTIVE',
      startsAt: '2026-10-01T00:00:00Z',
      renewsAt: '2026-11-01T00:00:00Z',
      sessionsRemaining: 5,
      sessionsTotal: 8,
      freeSessionsRemaining: 1,
    });
  }),

  http.post('/api/v1/me/membership/change-preview', async ({ request }) => {
    const body = (await request.json()) as any;
    return HttpResponse.json({
      targetPlanId: body.targetPlanId,
      targetPlanName: 'Unlimited Flow',
      targetPricePaise: 99900,
      creditPaise: 31187,
      amountDuePaise: 68713,
      refundPaise: 0,
      usedRatio: 0.375,
      unusedRatio: 0.625,
      explanationLines: [
        'Remaining unused days in current cycle: 19 of 30 days (62.5%)',
        'Unused credit applied from Starter Flow: Rs 311.87',
        'Net amount due for Unlimited Flow upgrade: Rs 687.13',
      ],
      cappedByCashPaise: false,
    });
  }),

  http.post('/api/v1/memberships/subscribe', () => {
    return HttpResponse.json(
      {
        status: 'REQUIRES_PAYMENT',
        paymentId: 'pay_123',
      },
      { status: 202 }
    );
  }),

  http.post('/api/v1/memberships/change', () => {
    return HttpResponse.json(
      {
        status: 'REQUIRES_PAYMENT',
        paymentId: 'pay_123',
      },
      { status: 202 }
    );
  }),

  http.post('/api/v1/memberships/cancel', () => {
    return new HttpResponse(null, { status: 200 });
  }),

  // Payments
  http.get('/api/v1/me/payments', () => {
    return HttpResponse.json({
      content: [
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
      totalElements: 1,
    });
  }),

  http.get('/api/v1/payments/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      amountPaise: 68713,
      currency: 'INR',
      status: 'SUCCEEDED',
      gateway: 'RAZORPAY',
      invoiceId: 'inv_123',
      invoiceNumber: 'INV-2026-001',
      createdAt: '2026-10-29T12:00:00Z',
    });
  }),

  http.get('/api/v1/payments/:id/client-payload', ({ params }) => {
    return HttpResponse.json({
      paymentId: params.id,
      gateway: 'MOCK',
      amountPaise: 68713,
      currency: 'INR',
      keyId: 'rzp_test_mock_key',
      orderId: 'order_mock_123',
      email: 'member@fitflow.test',
    });
  }),

  http.post('/api/v1/payments/:id/confirm', () => {
    return HttpResponse.json({
      id: 'pay_123',
      amountPaise: 68713,
      currency: 'INR',
      status: 'SUCCEEDED',
      gateway: 'RAZORPAY',
      invoiceNumber: 'INV-2026-001',
      createdAt: '2026-10-29T12:00:00Z',
    });
  }),

  http.get('/api/v1/me/invoices/:id', ({ params }) => {
    return HttpResponse.json({
      id: params.id,
      invoiceNumber: 'INV-2026-001',
      amountPaise: 42288,
      taxPaise: 7612,
      totalPaise: 49900,
      currency: 'INR',
      status: 'PAID',
      createdAt: '2026-10-01T10:00:00Z',
      lines: [
        {
          description: 'Starter Flow Monthly Studio Membership',
          amountPaise: 42288,
        },
      ],
    });
  }),

  // Account
  http.get('/api/v1/me', () => {
    return HttpResponse.json({
      id: 'usr_member_1',
      email: 'member@fitflow.test',
      fullName: 'Test Member',
      phone: '+91 98765 43210',
      roles: ['MEMBER'],
      version: 1,
      createdAt: '2026-01-01T00:00:00Z',
    });
  }),

  http.patch('/api/v1/me', async ({ request }) => {
    const body = (await request.json()) as any;
    return HttpResponse.json({
      id: 'usr_member_1',
      email: 'member@fitflow.test',
      fullName: body.fullName || 'Test Member',
      phone: body.phone || '+91 98765 43210',
      roles: ['MEMBER'],
      version: (body.version || 1) + 1,
      createdAt: '2026-01-01T00:00:00Z',
    });
  }),

  http.get('/api/v1/me/export', () => {
    return HttpResponse.json({
      user: { id: 'usr_member_1', email: 'member@fitflow.test' },
      bookings: [],
      payments: [],
    });
  }),

  http.post('/api/v1/me/delete-request', () => {
    return new HttpResponse(null, { status: 204 });
  }),
];
