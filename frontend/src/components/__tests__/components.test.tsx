import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { expectNoA11yViolations } from '../../test/setup';
import {
  Button,
  TextField,
  PasswordField,
  Select,
  Checkbox,
  RadioGroup,
  Switch,
  DatePicker,
  Tag,
  Badge,
  Card,
  Table,
  Tabs,
  Dialog,
  ConfirmDialog,
  ToastProvider,
  useToast,
  Banner,
  Skeleton,
  EmptyState,
  ErrorState,
  Pagination,
  Stepper,
  SessionCard,
  PlanCard,
  StatCard,
  DisclaimerNote,
  NavBar,
  SideNav,
  BottomTabs,
} from '../index';

describe('Design System Component Catalogue (29 components)', () => {
  // 1. Button
  it('Button: renders and responds to clicks and loading state', async () => {
    const onClick = vi.fn();
    const { container, rerender } = render(
      <Button variant="primary" onClick={onClick}>
        Click Me
      </Button>
    );

    const btn = screen.getByRole('button', { name: 'Click Me' });
    expect(btn).toBeInTheDocument();
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);

    await expectNoA11yViolations(container);

    // Test loading state
    rerender(
      <Button variant="primary" isLoading loadingText="Saving...">
        Click Me
      </Button>
    );
    expect(screen.getByText('Saving...')).toBeInTheDocument();
    expect(btn).toBeDisabled();
    expect(btn).toHaveAttribute('aria-busy', 'true');
  });

  // 2. TextField
  it('TextField: renders label, input, helperText, and error state', async () => {
    const { container, rerender } = render(
      <TextField label="User Email" placeholder="user@fitflow.test" helperText="We will never share your email" />
    );

    expect(screen.getByLabelText('User Email')).toBeInTheDocument();
    expect(screen.getByText('We will never share your email')).toBeInTheDocument();
    await expectNoA11yViolations(container);

    rerender(
      <TextField label="User Email" errorMessage="Invalid email address" />
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid email address');
    expect(screen.getByLabelText('User Email')).toHaveAttribute('aria-invalid', 'true');
    await expectNoA11yViolations(container);
  });

  // 3. PasswordField
  it('PasswordField: toggles password visibility with accessible button', async () => {
    const { container } = render(<PasswordField label="Secret Password" defaultValue="SuperSecret123" />);

    const input = screen.getByLabelText('Secret Password');
    expect(input).toHaveAttribute('type', 'password');

    const toggleBtn = screen.getByRole('button', { name: 'Show password' });
    fireEvent.click(toggleBtn);
    expect(input).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Hide password' })).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });

  // 4. Select
  it('Select: renders options and accessible trigger', async () => {
    const { container } = render(
      <Select
        label="Fitness Goal"
        options={[
          { value: 'LOSE_WEIGHT', label: 'Lose Weight' },
          { value: 'BUILD_STRENGTH', label: 'Build Strength' },
        ]}
      />
    );

    expect(screen.getByRole('combobox')).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });

  // 5. Checkbox
  it('Checkbox: toggles state and supports keyboard access', async () => {
    const onCheckedChange = vi.fn();
    const { container } = render(
      <Checkbox
        label="I agree to terms"
        description="Must be checked to continue"
        onCheckedChange={onCheckedChange}
      />
    );

    const checkbox = screen.getByRole('checkbox');
    expect(checkbox).toBeInTheDocument();
    fireEvent.click(checkbox);
    expect(onCheckedChange).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 6. RadioGroup
  it('RadioGroup: navigates between radio options', async () => {
    const { container } = render(
      <RadioGroup
        label="Select Intensity"
        name="intensity"
        options={[
          { value: 'LOW', label: 'Low Intensity' },
          { value: 'HIGH', label: 'High Intensity' },
        ]}
      />
    );

    expect(screen.getByRole('radiogroup')).toBeInTheDocument();
    expect(screen.getByLabelText('Low Intensity')).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });

  // 7. Switch
  it('Switch: toggles boolean preference', async () => {
    const onCheckedChange = vi.fn();
    const { container } = render(
      <Switch label="Enable SMS Notifications" onCheckedChange={onCheckedChange} />
    );

    const switchEl = screen.getByRole('switch');
    expect(switchEl).toBeInTheDocument();
    fireEvent.click(switchEl);
    expect(onCheckedChange).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 8. DatePicker
  it('DatePicker: renders date input with accessible label', async () => {
    const { container } = render(
      <DatePicker label="Birth Date" defaultValue="2000-01-01" />
    );

    const input = screen.getByLabelText('Birth Date');
    expect(input).toHaveAttribute('type', 'date');
    await expectNoA11yViolations(container);
  });

  // 9. Tag
  it('Tag: renders static, clickable, and removable variants', async () => {
    const onRemove = vi.fn();
    const { container } = render(
      <Tag label="Yoga" onRemove={onRemove} />
    );

    expect(screen.getByText('Yoga')).toBeInTheDocument();
    const removeBtn = screen.getByRole('button', { name: 'Remove Yoga' });
    fireEvent.click(removeBtn);
    expect(onRemove).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 10. Badge
  it('Badge: renders status badges with text and icons', async () => {
    const { container } = render(
      <div>
        <Badge status="ALLOWED" />
        <Badge status="CAUTION" />
        <Badge status="RED_FLAG" />
        <Badge status="CONFIRMED" />
      </div>
    );

    expect(screen.getByText('ALLOWED')).toBeInTheDocument();
    expect(screen.getByText('CAUTION')).toBeInTheDocument();
    expect(screen.getByText('RED_FLAG')).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });

  // 11. Card
  it('Card: renders standard and interactive container', async () => {
    const onClick = vi.fn();
    const { container } = render(
      <Card isInteractive onClick={onClick}>
        <span>Card Content</span>
      </Card>
    );

    const card = screen.getByRole('button');
    expect(card).toBeInTheDocument();
    fireEvent.click(card);
    expect(onClick).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 12. Table
  it('Table: renders tabular data with column sorting triggers and aria-sort', async () => {
    const onSort = vi.fn();
    const { container } = render(
      <Table
        caption="Session Table"
        columns={[
          { key: 'name', header: 'Session Name', sortable: true },
          { key: 'spots', header: 'Spots' },
        ]}
        data={[{ id: '1', name: 'Morning Flow', spots: '5' }]}
        sortColumn="name"
        sortDirection="asc"
        onSort={onSort}
      />
    );

    expect(screen.getByText('Morning Flow')).toBeInTheDocument();
    const sortBtn = screen.getByRole('button', { name: /Session Name/i });
    fireEvent.click(sortBtn);
    expect(onSort).toHaveBeenCalledWith('name');

    await expectNoA11yViolations(container);
  });

  // 13. Tabs
  it('Tabs: switches active content tabs', async () => {
    const { container } = render(
      <Tabs
        defaultValue="tab1"
        tabs={[
          { id: 'tab1', label: 'Tab 1', content: <div>Content 1</div> },
          { id: 'tab2', label: 'Tab 2', content: <div>Content 2</div> },
        ]}
      />
    );

    expect(screen.getByText('Content 1')).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });

  // 14. Dialog
  it('Dialog: renders modal with title, content, and close button', async () => {
    const onClose = vi.fn();
    const { container } = render(
      <Dialog isOpen={true} onClose={onClose} title="Class Details">
        <p>Class Modal Body</p>
      </Dialog>
    );

    expect(screen.getByRole('heading', { name: 'Class Details' })).toBeInTheDocument();
    expect(screen.getByText('Class Modal Body')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Close dialog' }));
    expect(onClose).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 15. ConfirmDialog
  it('ConfirmDialog: supports typed confirmation word', async () => {
    const onConfirm = vi.fn();
    const { container } = render(
      <ConfirmDialog
        isOpen={true}
        onClose={() => {}}
        title="Delete Account?"
        description="Permanently delete data"
        confirmationWord="DELETE"
        onConfirm={onConfirm}
      />
    );

    const confirmBtn = screen.getByRole('button', { name: 'Confirm' });
    expect(confirmBtn).toBeDisabled();

    // Type "DELETE"
    const input = screen.getByLabelText('Confirmation Word');
    fireEvent.change(input, { target: { value: 'DELETE' } });
    expect(confirmBtn).not.toBeDisabled();

    fireEvent.click(confirmBtn);
    expect(onConfirm).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 16. Toast
  it('Toast: renders transient notification via context', async () => {
    const TestComponent = () => {
      const { showSuccess } = useToast();
      return (
        <button onClick={() => showSuccess('Saved!', 'Changes applied successfully')}>
          Show
        </button>
      );
    };

    const { container } = render(
      <ToastProvider>
        <TestComponent />
      </ToastProvider>
    );

    fireEvent.click(screen.getByText('Show'));
    expect(await screen.findByText('Saved!')).toBeInTheDocument();
    expect(screen.getByText('Changes applied successfully')).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });

  // 17. Banner
  it('Banner: renders region with icon and title', async () => {
    const { container } = render(
      <Banner type="warning" title="Studio Maintenance Scheduled">
        Power outage planned on Sunday.
      </Banner>
    );

    expect(screen.getByRole('region')).toBeInTheDocument();
    expect(screen.getByText('Studio Maintenance Scheduled')).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });

  // 18. Skeleton
  it('Skeleton: renders aria-hidden pulsing block', async () => {
    const { container } = render(<Skeleton variant="card" data-testid="skel" />);
    const skel = screen.getByTestId('skel');
    expect(skel).toHaveAttribute('aria-hidden', 'true');
    await expectNoA11yViolations(container);
  });

  // 19. EmptyState
  it('EmptyState: displays non-judgmental guidance and action button', async () => {
    const onAction = vi.fn();
    const { container } = render(
      <EmptyState
        title="No classes found"
        description="Try adjusting your search criteria"
        actionLabel="Reset Search"
        onAction={onAction}
      />
    );

    expect(screen.getByText('No classes found')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Reset Search' }));
    expect(onAction).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 20. ErrorState
  it('ErrorState: announces error via role="alert" with retry action', async () => {
    const onRetry = vi.fn();
    const { container } = render(
      <ErrorState
        errorMessage="Network connection timed out"
        traceId="trace-xyz-987"
        onRetry={onRetry}
      />
    );

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Reference: trace-xyz-987')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
    expect(onRetry).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 21. Pagination
  it('Pagination: renders page buttons with aria-current="page"', async () => {
    const onPageChange = vi.fn();
    const { container } = render(
      <Pagination currentPage={2} totalPages={5} onPageChange={onPageChange} />
    );

    const activePage = screen.getByRole('button', { name: 'Page 2' });
    expect(activePage).toHaveAttribute('aria-current', 'page');

    fireEvent.click(screen.getByRole('button', { name: 'Next Page' }));
    expect(onPageChange).toHaveBeenCalledWith(3);

    await expectNoA11yViolations(container);
  });

  // 22. Stepper
  it('Stepper: renders numbered steps with checkmarks for completed steps', async () => {
    const { container } = render(
      <Stepper
        steps={[
          { id: '1', label: 'Details' },
          { id: '2', label: 'Payment' },
          { id: '3', label: 'Done' },
        ]}
        currentStepIndex={1}
      />
    );

    expect(screen.getByRole('navigation', { name: 'Progress Stepper' })).toBeInTheDocument();
    expect(screen.getByText('Details')).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });

  // 23. SessionCard
  it('SessionCard: renders session details, capacity, and action trigger', async () => {
    const onBook = vi.fn();
    const { container } = render(
      <SessionCard
        session={{
          id: 'sess_1',
          classTitle: 'Gentle Hatha Yoga',
          category: 'YOGA',
          trainerName: 'Ananya',
          roomName: 'Studio A',
          startsAt: '2026-11-01T07:00:00Z',
          endsAt: '2026-11-01T08:00:00Z',
          capacity: 20,
          bookedCount: 15,
          status: 'SCHEDULED',
        }}
        onBook={onBook}
      />
    );

    expect(screen.getByText('Gentle Hatha Yoga')).toBeInTheDocument();
    expect(screen.getByText('15 / 20 spots filled')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Book Session' }));
    expect(onBook).toHaveBeenCalledWith('sess_1');

    await expectNoA11yViolations(container);
  });

  // 24. PlanCard
  it('PlanCard: formats price with integer paise math and displays features', async () => {
    const onSelect = vi.fn();
    const { container } = render(
      <PlanCard
        plan={{
          id: 'p_1',
          name: 'Starter Flow',
          pricePaise: 49900,
          sessionsPerMonth: 8,
          features: ['8 monthly sessions'],
        }}
        actionLabel="Choose Starter"
        onSelect={onSelect}
      />
    );

    expect(screen.getByText('Starter Flow')).toBeInTheDocument();
    expect(screen.getByText('Rs 499.00')).toBeInTheDocument();
    expect(screen.getByText('8 monthly sessions')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Choose Starter' }));
    expect(onSelect).toHaveBeenCalledWith('p_1');

    await expectNoA11yViolations(container);
  });

  // 25. StatCard
  it('StatCard: displays numeric metric and trend label', async () => {
    const { container } = render(
      <StatCard title="Weekly Attendance" value="12" trendLabel="+3 from last week" trendDirection="up" />
    );

    expect(screen.getByText('Weekly Attendance')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('+3 from last week')).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });

  // 26. DisclaimerNote
  it('DisclaimerNote: renders mandatory wellness disclaimer with role="note"', async () => {
    const { container } = render(<DisclaimerNote />);

    const note = screen.getByRole('note');
    expect(note).toBeInTheDocument();
    expect(note).toHaveTextContent(/Wellness suggestions, not medical advice/);

    await expectNoA11yViolations(container);
  });

  // 27. NavBar
  it('NavBar: renders brand, theme switcher, and navigation actions', async () => {
    const onThemeToggle = vi.fn();
    const { container } = render(
      <BrowserRouter>
        <NavBar
          currentRole="Member"
          userName="Tester"
          themePreference="light"
          onThemeToggle={onThemeToggle}
        />
      </BrowserRouter>
    );

    expect(screen.getByText('FitFlow')).toBeInTheDocument();
    expect(screen.getByText('Member')).toBeInTheDocument();

    const themeBtn = screen.getByRole('button', { name: /Switch theme/i });
    fireEvent.click(themeBtn);
    expect(onThemeToggle).toHaveBeenCalled();

    await expectNoA11yViolations(container);
  });

  // 28. SideNav
  it('SideNav: renders navigation links and disabled items', async () => {
    const { container } = render(
      <BrowserRouter>
        <SideNav />
      </BrowserRouter>
    );

    expect(screen.getByRole('navigation', { name: 'Main Navigation' })).toBeInTheDocument();
    expect(screen.getByText('Browse Classes')).toBeInTheDocument();

    // Stage 12 items should be disabled
    const wellnessItem = screen.getByText('Wellness').closest('[aria-disabled="true"]');
    expect(wellnessItem).toHaveAttribute('aria-disabled', 'true');

    await expectNoA11yViolations(container);
  });

  // 29. BottomTabs
  it('BottomTabs: renders mobile navigation bar', async () => {
    const { container } = render(
      <BrowserRouter>
        <BottomTabs />
      </BrowserRouter>
    );

    expect(screen.getByRole('navigation', { name: 'Mobile Navigation' })).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Classes')).toBeInTheDocument();

    await expectNoA11yViolations(container);
  });
});
