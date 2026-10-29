# FitFlow Requirements Traceability Matrix (docs/design/requirements-traceability.md)

This document establishes the machine-verifiable mapping between the case study brief requirements, backend domain modules, database stores, frontend screens, and user interaction flows.

---

## 1. Traceability Matrix

| Requirement from Brief | Architectural Implementation | Primary Screens | Endpoints Used | Flow | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **Spring/FastAPI Backend** | Spring Boot 3.5.3 (Java 17 LTS) modular monolith with RFC 7807 problem+json error handling | All screens | All endpoints in `docs/api/openapi.json` | 1–7 | **SATISFIED** |
| **MySQL & PostgreSQL Databases** | Dual DataSources: MySQL 8 (transactional core, users, classes, bookings, payments, encrypted health) & PostgreSQL 17 (agent memory, audit, job locks) | All authenticated screens | MySQL: `/api/v1/classes`, `/api/v1/bookings`, `/api/v1/wellness/*`; Postgres: `/api/v1/agent/*`, `/api/v1/admin/messaging/*` | 1–7 | **SATISFIED** |
| **React Frontend** | React 18 + TypeScript (strict) + Vite; React Router v6; Redux Toolkit + RTK Query; Tailwind CSS with tokens | 46 screens in `docs/design/screens.md` | Mapped in `screen-endpoint-map.yml` | 1–7 | **SATISFIED** |
| **UI/UX Design & System** | WCAG 2.2 AA design tokens (`tokens.json`), 38 accessible components (`components.md`), static style guide (`styleguide/index.html`) | All screens | Static tokens & assets | 1–7 | **SATISFIED** |
| **User (Member) Role** | Member workspace with home dashboard, class discovery, bookings, membership checkout, and DPDP data rights | `member-home-dashboard`, `member-browse-classes`, `member-session-details`, `member-my-bookings`, `member-membership`, `member-checkout`, `member-account` | `GET /api/v1/me`, `GET /api/v1/sessions`, `POST /api/v1/bookings`, `POST /api/v1/memberships/*` | 1, 2, 3 | **SATISFIED** |
| **Trainer Role** | Trainer workspace with weekly schedule, attendee check-in roster, session completion, and payout earnings ledger | `trainer-dashboard`, `trainer-my-sessions`, `trainer-session-detail`, `trainer-class-prep-summary`, `trainer-earnings`, `trainer-profile-editor` | `GET /api/v1/trainer/*`, `POST /api/v1/trainer/sessions/{id}/attendance`, `POST /api/v1/trainer/sessions/{id}/complete` | 6 | **SATISFIED** |
| **Admin Role** | Admin workspace with operational metrics, user provisioning, scheduling with conflict detection, refund approvals, and payouts | `admin-dashboard`, `admin-users`, `admin-classes`, `admin-sessions`, `admin-plans`, `admin-refunds`, `admin-payouts`, `admin-settings-ai`, `admin-settings-payments`, `admin-messaging` | `GET /api/v1/admin/*`, `POST /api/v1/admin/*`, `PUT /api/v1/admin/*` | 7 | **SATISFIED** |
| **Multi-Agent Framework** | 8 specialist agents (PlanBuilder, WellnessCoach, NutritionNumbers, TrainerPrep, TrainerSchedule, Retention, ClassMetadata, DemandForecast) + SupervisorRouter | `member-assistant-panel`, `admin-assistant-panel` | `POST /api/v1/agent/chat`, `GET/POST /api/v1/agent/chat/stream`, `POST /api/v1/agent/actions/{id}/confirm` | 5 | **SATISFIED** |
| **JWT Authentication** | Stateless JJWT authentication; access token in memory, refresh token in httpOnly cookie; brute-force lockout on 5 fails | `public-login`, `public-register`, `public-forgot-password`, `public-reset-password`, `public-session-expired` | `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/forgot-password` | 1 | **SATISFIED** |
| **RabbitMQ Messaging** | Topic exchange topology, dead-letter queues, outbox relay with publisher confirms, and DLQ replay controls | `admin-dashboard`, `admin-messaging` | `GET /api/v1/admin/messaging/queues`, `POST /api/v1/admin/messaging/dlq/{queue}/replay`, `GET /api/v1/admin/messaging/outbox` | 7 | **SATISFIED** |
| **SMTP Notifications** | Dual HTML/plain-text transactional email rendering (registration, booking confirmation, waitlist promotion, cancel notices) | Background asynchronous dispatch triggered by booking & auth flows | Dispatched via Outbox relay; viewable in dev via `/dev/mail` | 1, 2 | **SATISFIED** |
| **Memberships & Quotas** | Free tier (3 credits), paid tiers (Basic 8 credits, Pro 16 credits), prorated upgrades/downgrades with refund calculations | `public-plans`, `member-membership`, `member-checkout`, `member-payment-result`, `admin-plans` | `GET /api/v1/plans`, `GET /api/v1/me/membership`, `POST /api/v1/me/membership/change-preview`, `POST /api/v1/memberships/subscribe` | 3 | **SATISFIED** |
| **Trainer Pay Per Session** | Base rate per session, attendance marking unlocks completion, monthly batch generation with dual-admin approval | `trainer-session-detail`, `trainer-earnings`, `admin-payouts` | `GET /api/v1/trainer/me/earnings`, `POST /api/v1/admin/payouts`, `POST /api/v1/admin/payouts/{id}/approve` | 6, 7 | **SATISFIED** |
| **Limited Admin Access** | Deny-by-default granular permissions (`USER_READ`, `USER_EDIT`, `CLASS_WRITE`, `REFUND_APPROVE`, `PAYOUT_EXECUTE`); ArchUnit Rule M health ban | `admin-users`, `admin-classes`, `admin-refunds`, `admin-payouts` | `PUT /api/v1/admin/users/{id}/permissions`, `POST /api/v1/admin/refunds/{id}/approve` | 7 | **SATISFIED** |
| **Gemini API Integration** | LangChain4j Gemini client with runtime encrypted key entry, masked status display, model switching, and connection test | `admin-settings-ai`, `member-assistant-panel`, `admin-assistant-panel` | `GET /api/v1/admin/settings/ai`, `PUT /api/v1/admin/settings/ai/key`, `POST /api/v1/admin/settings/ai/test` | 5, 7 | **SATISFIED** |
| **Member Intake & Health Profile** | Coded vocabularies, AES-256-GCM encrypted profile, PAR-Q Q1–Q7 questionnaire, DPDP consent, and data rights | `member-wellness-consent`, `member-wellness-profile`, `member-screening-result`, `member-my-health-data` | `GET /api/v1/wellness/consent`, `PUT /api/v1/wellness/profile`, `GET /api/v1/wellness/export`, `DELETE /api/v1/wellness/data` | 4 | **SATISFIED** |
| **Class Suggestions & Safety Filter** | Deterministic safety filter categorizing classes into ALLOWED, CAUTION (with modifications), and RESTRICTED | `member-safe-classes`, `member-class-details`, `member-screening-result` | `GET /api/v1/wellness/classes/safe`, `GET /api/v1/wellness/classes/{classId}/check` | 4 | **SATISFIED** |
| **Calorie Guidance** | Deterministic MET calculations, daily BMR/TDEE calculations, and class burn estimates with medical disclaimer note | `member-calorie-numbers` | `GET /api/v1/wellness/nutrition/numbers`, `GET /api/v1/wellness/classes/{classId}/burn` | 4 | **SATISFIED** |
| **Other AI Capabilities** | At-risk retention offers, automated class metadata drafting, demand forecasting, scheduled weekly coach jobs | `admin-assistant-panel`, `member-assistant-panel`, `member-restriction-proposals` | `POST /api/v1/agent/chat`, `GET /api/v1/agent/actions/pending`, `POST /api/v1/agent/actions/{id}/confirm` | 5, 7 | **SATISFIED** |

---

## 2. Requirement Gap Analysis

- **Total Requirements Assessed**: 19
- **Satisfied Requirements**: 19
- **Identified Gaps**: **0 (Zero)**
- **Conclusion**: Every architectural capability, user permission, database entity, and AI feature specified in the case study brief has a corresponding frontend screen, wireframe, user flow, and OpenAPI endpoint mapping.
