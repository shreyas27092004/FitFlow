# FitFlow Component Catalogue & Specification (docs/design/components.md)

This document specifies the complete set of 38 reusable UI components for the FitFlow web application. All interactive elements strictly adhere to WCAG 2.2 AA accessibility standards, visible keyboard focus rings, and Radix UI headless primitives.

---

## 1. Action & Button Components

### 1.1 Button
- **Purpose**: Primary trigger for actions, submissions, and workflow confirmations.
- **Variants**: `primary`, `secondary`, `outline`, `ghost`, `danger`, `link`.
- **Sizes**: `sm` (32px height), `md` (40px height), `lg` (48px height).
- **States**: `default`, `hover`, `focus-visible`, `active`, `disabled`, `loading` (`aria-busy="true"` with spinner).
- **Props**: `variant`, `size`, `isLoading`, `loadingText`, `leftIcon`, `rightIcon`, `disabled`, `idempotencyKey`, `onClick`, `children`.
- **Accessibility**: Native `<button type="button|submit">`, `aria-disabled="true"`, visible 2px focus ring, touch target $\ge 44$px on mobile.

### 1.2 IconButton
- **Purpose**: Compact button containing an icon for toolbars, pagination, modal close, and actions.
- **Variants**: `ghost`, `outline`, `filled`, `danger`.
- **States**: `default`, `hover`, `focus-visible`, `active`, `disabled`.
- **Props**: `icon`, `aria-label` (mandatory), `size` (`sm`, `md`, `lg`), `onClick`.
- **Accessibility**: Mandatory descriptive `aria-label`, tooltips on hover/focus, minimum touch target size 44px.

---

## 2. Form & Input Controls

### 2.3 TextField
- **Purpose**: Single-line text input for names, emails, search terms, and numeric values.
- **Variants**: `default`, `filled`.
- **States**: `default`, `hover`, `focus`, `disabled`, `readOnly`, `error` (red border & message).
- **Props**: `name`, `label`, `helperText`, `errorMessage`, `placeholder`, `type`, `required`, `autoComplete`, `value`, `onChange`.
- **Accessibility**: Linked `<label htmlFor="{id}">`, `aria-invalid="{hasError}"`, `aria-describedby="{errorId} {helperId}"`.

### 2.4 PasswordField
- **Purpose**: Secure credential entry with toggleable visibility icon.
- **Variants**: `default`.
- **States**: `default`, `hover`, `focus`, `disabled`, `error`.
- **Props**: `name`, `label`, `helperText`, `errorMessage`, `showPasswordToggle`, `value`, `onChange`.
- **Accessibility**: Accessible button toggle (`aria-label="Show password"` / `"Hide password"`), announcement of visibility state change.

### 2.5 Select
- **Purpose**: Accessible dropdown menu for selecting from a defined list of options.
- **Variants**: Built on `@radix-ui/react-select`.
- **States**: `closed`, `open`, `item-hover`, `item-selected`, `disabled`, `error`.
- **Props**: `options: { value, label, disabled? }[]`, `value`, `onValueChange`, `placeholder`, `label`, `errorMessage`.
- **Accessibility**: Full keyboard navigation (`ArrowUp`, `ArrowDown`, `Enter`, `Home`, `End`), `aria-expanded`, screen reader announcements.

### 2.6 Checkbox
- **Purpose**: Binary or indeterminate toggle for terms agreement, consent purposes, and multi-selection.
- **Variants**: Built on `@radix-ui/react-checkbox`.
- **States**: `unchecked`, `checked`, `indeterminate`, `hover`, `focus`, `disabled`.
- **Props**: `checked`, `onCheckedChange`, `label`, `description`, `disabled`, `id`.
- **Accessibility**: Focusable indicator, spacebar toggle, linked text label.

### 2.7 RadioGroup
- **Purpose**: Exclusive single selection from 2–6 related options (e.g. sex, intensity level).
- **Variants**: Built on `@radix-ui/react-radio-group` (standard vertical list or segmented card buttons).
- **States**: `unselected`, `selected`, `focus`, `hover`, `disabled`.
- **Props**: `name`, `value`, `onValueChange`, `options: { value, label, description? }[]`.
- **Accessibility**: Arrow key navigation within group, `role="radiogroup"`, `aria-checked="true|false"`.

### 2.8 Switch
- **Purpose**: Instant toggle for preferences and notification settings.
- **Variants**: Built on `@radix-ui/react-switch`.
- **States**: `off`, `on`, `hover`, `focus`, `disabled`.
- **Props**: `checked`, `onCheckedChange`, `label`, `id`.
- **Accessibility**: Role `switch`, `aria-checked`, keyboard spacebar activation.

### 2.9 DatePicker (Accessible & Keyboard-Navigable)
- **Purpose**: Date of birth, class schedule date, and calendar filter selection.
- **Variants**: Text input with formatted mask (`DD/MM/YYYY`) + accessible calendar popup.
- **States**: `default`, `open`, `day-hover`, `day-selected`, `disabled`, `error`.
- **Props**: `value: Date`, `onChange: (Date) => void`, `minDate`, `maxDate`, `label`.
- **Accessibility**: Full keyboard grid navigation (`Arrow keys` for day, `PageUp/PageDown` for month, `Enter` to select), manual typed entry always supported.

### 2.10 TimePicker
- **Purpose**: Class session start time and trainer recurring availability hours.
- **Variants**: Dual segmented select (Hours:Minutes AM/PM).
- **States**: `default`, `focus`, `error`, `disabled`.
- **Props**: `value: string`, `onChange: (string) => void`, `intervalMinutes: 15|30`.
- **Accessibility**: Keyboard navigable via number input or dropdown, `aria-label="Start time"`.

---

## 3. Data Display & Status Components

### 3.11 Tag / Chip
- **Purpose**: Compact interactive or static token for categories, contraindication tags, and filters.
- **Variants**: `default`, `removable` (with 'X' icon), `clickable` (filter toggle).
- **States**: `default`, `hover`, `active`, `selected`.
- **Props**: `label`, `colorScheme`, `onRemove`, `onClick`, `selected`.
- **Accessibility**: If removable, close button has accessible `aria-label="Remove {label}"`.

### 3.12 Badge
- **Purpose**: Status indicator displaying class difficulty, safety screening verdict, or payment state.
- **Variants**: `success` (green), `warning` (amber), `danger` (red), `info` (cyan), `neutral` (slate).
- **States**: `default`.
- **Props**: `status: "ALLOWED" | "CAUTION" | "RESTRICTED" | "RED_FLAG" | "ACTIVE" | "PENDING"`, `children`.
- **Accessibility**: Never uses color alone; always includes an icon and clear text label.

### 3.13 Card
- **Purpose**: Container for grouping related content and actions.
- **Variants**: `elevated`, `outlined`, `interactive` (hoverable & clickable).
- **States**: `default`, `hover`, `focus-within`.
- **Props**: `children`, `padding`, `onClick`, `className`.
- **Accessibility**: If interactive, wrapped in native semantic link or button element.

### 3.14 Table (Sortable, Paginated, Responsive)
- **Purpose**: Displays tabular records (users, payouts, attendance rosters, audit logs).
- **Variants**: Standard table on desktop ($\ge 768$px); responsive card stack on mobile ($<768$px).
- **States**: `default`, `sorting`, `loading` (table skeleton), `empty`.
- **Props**: `columns: { key, header, sortable? }[]`, `data: any[]`, `sortColumn`, `sortDirection`, `onSort`, `isLoading`.
- **Accessibility**: `<th>` with `aria-sort="ascending|descending|none"`, accessible table caption.

### 3.15 Tabs
- **Purpose**: Segmenting content into distinct view panels without leaving the current route.
- **Variants**: Built on `@radix-ui/react-tabs` (underline or pill variant).
- **States**: `inactive`, `active`, `hover`, `focus`.
- **Props**: `tabs: { id, label, icon?, badge? }[]`, `activeTab`, `onTabChange`.
- **Accessibility**: Keyboard arrow key navigation between tabs, `role="tablist"`, `aria-selected="true"`.

---

## 4. Overlay & Feedback Components

### 4.16 Dialog
- **Purpose**: Modal overlay requiring user attention without navigating away.
- **Variants**: Built on `@radix-ui/react-dialog`.
- **States**: `open`, `closing`, `closed`.
- **Props**: `isOpen`, `onClose`, `title`, `description`, `children`, `footerButtons`.
- **Accessibility**: Focus trap, background scroll lock, `Esc` key dismissal, focus restoration on close.

### 4.17 ConfirmDialog (with Typed Confirmation Variant)
- **Purpose**: Guardrail for destructive actions (class cancellation, membership cancellation, health data deletion).
- **Variants**: `standard` (Confirm / Cancel buttons), `typed` (requires typing specific text like `"DELETE"` to unlock confirm).
- **States**: `idle`, `typing`, `confirmed`, `submitting`.
- **Props**: `title`, `description`, `confirmationWord?`, `onConfirm`, `onCancel`, `isDestructive: boolean`.
- **Accessibility**: Destructive button badged with danger styling, autofocus on cancel button or input.

### 4.18 Drawer
- **Purpose**: Slide-in panel from the right edge for mobile navigation, filters, or AI chat sidebar.
- **Variants**: Built on `@radix-ui/react-dialog`.
- **States**: `closed`, `entering`, `open`, `exiting`.
- **Props**: `isOpen`, `onClose`, `position: "right" | "bottom"`, `children`.
- **Accessibility**: Focus trap, accessible close button, dismiss on outside click or `Esc`.

### 4.19 Toast
- **Purpose**: Transient notification for action feedback (booking confirmed, link copied).
- **Variants**: Built on `@radix-ui/react-toast` (`success`, `warning`, `error`, `info`).
- **States**: `entering`, `visible`, `exiting`.
- **Props**: `title`, `description`, `durationMs: 4000`, `action?`.
- **Accessibility**: Rendered in `aria-live="polite"` or `role="alert"` region, dismissible via keyboard.

### 4.20 Banner (Info / Warning / Danger)
- **Purpose**: High-visibility contextual callout pinned to the top of a page or card.
- **Variants**: `info`, `warning`, `danger`, `success`.
- **States**: `visible`, `dismissed`.
- **Props**: `type`, `title`, `children`, `onDismiss?`.
- **Accessibility**: Uses `role="region"`, high contrast icon, non-color dependent status text.

---

## 5. Structural & Feedback States

### 5.21 Skeleton
- **Purpose**: Placeholder block matching the dimensions of content being fetched.
- **Variants**: `text`, `avatar`, `card`, `button`, `table-row`.
- **States**: Pulsing opacity animation (disabled under `prefers-reduced-motion`).
- **Props**: `width`, `height`, `rounded`.
- **Accessibility**: `aria-hidden="true"` container badged with parent `aria-busy="true"`.

### 5.22 EmptyState
- **Purpose**: Helpful guidance when a search, booking list, or table contains zero records.
- **Variants**: `default`, `compact`.
- **Props**: `icon`, `title`, `description`, `actionLabel?`, `onAction?`.
- **Accessibility**: Semantic heading structure, clear non-judgmental guidance copy.

### 5.23 ErrorState
- **Purpose**: Displays when a network request, screen load, or unexpected crash occurs.
- **Variants**: `page`, `inline-card`.
- **Props**: `errorCode: RFC 7807 code`, `errorMessage`, `onRetry?`.
- **Accessibility**: Announces error via `role="alert"`, keyboard-focusable retry button.

### 5.24 Pagination
- **Purpose**: Controls navigation across multi-page collections (users, sessions, payments).
- **Variants**: Standard page numbers with Previous/Next buttons.
- **States**: `default`, `current-page`, `disabled`.
- **Props**: `currentPage`, `totalPages`, `pageSize`, `totalItems`, `onPageChange`.
- **Accessibility**: `aria-label="Pagination Navigation"`, `aria-current="page"` on active number.

### 5.25 Stepper / Wizard
- **Purpose**: Multi-step progress indicator for Member Wellness Intake and Class Creation.
- **Variants**: Horizontal linear stepper (desktop) and numeric step header (mobile).
- **States**: `completed` (checkmark), `active` (bold highlight), `upcoming` (subtle).
- **Props**: `steps: { id, label }[]`, `currentStepIndex`.
- **Accessibility**: `role="navigation"`, `aria-label="Progress Stepper"`, announce step change to screen reader.

---

## 6. Domain-Specific Business Components

### 6.26 Calendar Week View
- **Purpose**: 7-day interactive schedule for Member class discovery and Trainer teaching assignments.
- **Variants**: Week view with hourly time slots (`06:00` to `21:00`).
- **States**: `day-active`, `slot-occupied`, `slot-available`, `loading`.
- **Props**: `startDate: Date`, `sessions: ClassSessionDto[]`, `onSelectSession`.
- **Accessibility**: Keyboard arrow key navigation across calendar cells, accessible day headers.

### 6.27 SessionCard
- **Purpose**: Displays single scheduled class instance with live capacity indicator.
- **Variants**: `upcoming`, `in-progress`, `completed`, `cancelled`.
- **States**: `default`, `hover`, `selected`.
- **Props**: `session: ClassSessionDto`, `onBook`, `onCancel`, `isBooked: boolean`, `waitlistPosition?`.
- **Accessibility**: Semantic headings, accessible button labels (`"Book Yoga Flow on Tuesday at 7:00 AM"`).

### 6.28 PlanCard
- **Purpose**: Highlights a subscription plan tier with pricing, session count, and action buttons.
- **Variants**: `standard`, `current-plan`, `recommended-upgrade`.
- **States**: `default`, `selected`.
- **Props**: `plan: MembershipPlanDto`, `isCurrent: boolean`, `onSelect`, `actionLabel`.
- **Accessibility**: Clear price summary (`"Rs. 899.00 per month for 16 sessions"`), comparison table format.

### 6.29 StatCard
- **Purpose**: Metric container for dashboards (completed sessions, streak, revenue, outbox health).
- **Variants**: `default`, `with-trend` (percentage delta).
- **Props**: `title`, `value`, `trendLabel?`, `trendDirection?`, `icon`.
- **Accessibility**: Text description accompanying icon and percentage trends.

### 6.30 Chart (Simple Bar / Line with Text Alternative)
- **Purpose**: Visualizes studio utilization and member weekly calorie burn trends.
- **Variants**: `bar` (utilization), `line` (weight / calorie progress).
- **States**: `rendered`, `loading`.
- **Props**: `data: { label, value }[]`, `unit`, `accessibleTableSummary`.
- **Accessibility**: Dual visualization: SVG chart paired with an accessible collapsible `<table>` alternative for screen readers.

---

## 7. AI & Conversational Components

### 7.31 ChatMessage
- **Purpose**: Displays user prompts and assistant replies in the multi-agent chat interface.
- **Variants**: `user` (right-aligned, brand surface) and `assistant` (left-aligned, neutral surface, AI badged).
- **States**: `sent`, `streaming` (blinking cursor), `error`.
- **Props**: `message: AgentMessageDto`, `isStreaming: boolean`.
- **Accessibility**: Role `listitem`, distinct styling, announcement of incoming messages via `aria-live="polite"`.

### 7.32 PendingActionCard
- **Purpose**: Human-in-the-loop confirmation card rendered when an agent proposes a write action.
- **Variants**: `pending`, `confirmed`, `rejected`, `expired`.
- **States**: `active` (countdown ticking), `submitting`, `completed`.
- **Props**: `action: PendingActionProposal`, `onConfirm`, `onReject`, `expiresAt`.
- **Accessibility**: Standalone focusable container (`role="group"`), explicitly labelled Confirm and Reject buttons.

---

## 8. Navigation & Foundation Components

### 8.33 Tooltip
- **Purpose**: Brief supplementary info on icon buttons and abbreviation tags.
- **Variants**: Built on `@radix-ui/react-tooltip`.
- **States**: `hidden`, `visible`.
- **Props**: `content`, `children`, `delayDuration: 300`.
- **Accessibility**: Visible on hover and keyboard focus; never contains critical action triggers.

### 8.34 Breadcrumbs
- **Purpose**: Hierarchical trail showing current page depth (e.g. `Classes > Power Yoga > Book Session`).
- **Props**: `items: { label, href? }[]`.
- **Accessibility**: `<nav aria-label="Breadcrumb">`, `<ol>` semantic list with `aria-current="page"`.

### 8.35 NavBar
- **Purpose**: Persistent top application header.
- **Props**: `currentRole`, `userName`, `onLogout`, `themeToggle`.
- **Accessibility**: `<header>` element with accessible navigation links and menu triggers.

### 8.36 SideNav
- **Purpose**: Collapsible desktop navigation bar for Member, Trainer, and Admin workspaces.
- **Props**: `navigationItems: { label, href, icon, requiredPermission? }[]`.
- **Accessibility**: `<nav aria-label="Main Navigation">`, marks active link with `aria-current="page"`.

### 8.37 Footer
- **Purpose**: Standard bottom page footer displaying copyright, DPDP privacy rights, and support links.
- **Props**: `links: { label, href }[]`.
- **Accessibility**: Landmark `<footer>` element with high-contrast text.

### 8.38 DisclaimerNote
- **Purpose**: Mandatory wellness and safety disclaimer container.
- **Text**: *"Wellness suggestions, not medical advice. Rules are illustrative and must be reviewed by a qualified professional before real use."*
- **Variants**: `standard` (prominent bordered callout) and `compact` (footer footnote).
- **Accessibility**: `role="note"`, styled with high-contrast borders and icon.
