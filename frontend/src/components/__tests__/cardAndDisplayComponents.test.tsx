import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  Badge,
  Tag,
  Card,
  StatCard,
  EmptyState,
  ErrorState,
  DisclaimerNote,
  Skeleton,
} from '../index';
import { Dumbbell, AlertCircle } from 'lucide-react';
import { expectNoA11yViolations } from '../../test/setup';

describe('Card and Display Components Suite', () => {
  // 1. Badge Variant Success
  it('Badge: renders success styling for ALLOWED or ACTIVE status', () => {
    const { rerender } = render(<Badge status="ALLOWED" />);
    expect(screen.getByText('ALLOWED')).toBeInTheDocument();

    rerender(<Badge status="ACTIVE" />);
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();
  });

  // 2. Badge Variant Warning and Danger
  it('Badge: renders warning and danger statuses with appropriate styles', () => {
    const { rerender } = render(<Badge status="CAUTION" />);
    expect(screen.getByText('CAUTION')).toBeInTheDocument();

    rerender(<Badge status="RED_FLAG" />);
    expect(screen.getByText('RED_FLAG')).toBeInTheDocument();

    rerender(<Badge status="FAILED" />);
    expect(screen.getByText('FAILED')).toBeInTheDocument();
  });

  // 3. Badge with Custom Children
  it('Badge: renders custom children and custom variant', () => {
    render(<Badge variant="info">Custom Info</Badge>);
    expect(screen.getByText('Custom Info')).toBeInTheDocument();
  });

  // 4. Tag Label and Click
  it('Tag: renders label and responds to keyboard/click interactions', () => {
    const onClick = vi.fn();
    render(<Tag label="Strength" onClick={onClick} />);

    const tag = screen.getByRole('button', { name: 'Strength' });
    fireEvent.click(tag);
    expect(onClick).toHaveBeenCalledTimes(1);

    fireEvent.keyDown(tag, { key: 'Enter' });
    expect(onClick).toHaveBeenCalledTimes(2);
  });

  // 5. Tag Removable Action
  it('Tag: renders remove button and triggers onRemove', () => {
    const onRemove = vi.fn();
    render(<Tag label="Cardio" onRemove={onRemove} />);

    const removeBtn = screen.getByRole('button', { name: 'Remove Cardio' });
    fireEvent.click(removeBtn);
    expect(onRemove).toHaveBeenCalledTimes(1);
  });

  // 6. Tag Selected State
  it('Tag: applies selected styling when selected=true', () => {
    render(<Tag label="Pilates" selected />);
    const tag = screen.getByText('Pilates').parentElement;
    expect(tag).toHaveClass('bg-brand-primary');
  });

  // 7. Card Variants and Interactivity
  it('Card: renders variants and responds to interactive click and keydown', () => {
    const onClick = vi.fn();
    const { rerender } = render(
      <Card variant="outlined" isInteractive onClick={onClick}>
        <span>Interactive Card</span>
      </Card>
    );

    const card = screen.getByRole('button', { name: 'Interactive Card' });
    expect(card).toBeInTheDocument();
    fireEvent.click(card);
    expect(onClick).toHaveBeenCalledTimes(1);

    fireEvent.keyDown(card, { key: 'Enter' });
    expect(onClick).toHaveBeenCalledTimes(2);

    rerender(
      <Card variant="subtle">
        <span>Subtle Card</span>
      </Card>
    );
    expect(screen.getByText('Subtle Card')).toBeInTheDocument();
  });

  // 8. StatCard Values and Icons
  it('StatCard: renders title, value, and custom icon', () => {
    render(
      <StatCard
        title="Completed Sessions"
        value={12}
        icon={<Dumbbell data-testid="dumbbell-icon" />}
      />
    );

    expect(screen.getByText('Completed Sessions')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByTestId('dumbbell-icon')).toBeInTheDocument();
  });

  // 9. StatCard Trend Indicators
  it('StatCard: displays positive and negative trend indicators', () => {
    const { rerender } = render(
      <StatCard
        title="Streak"
        value="5 days"
        trendLabel="+2 days from last week"
        trendDirection="up"
      />
    );
    expect(screen.getByText('+2 days from last week')).toHaveClass('text-green-600');

    rerender(
      <StatCard
        title="Attendance"
        value="60%"
        trendLabel="-10% from target"
        trendDirection="down"
      />
    );
    expect(screen.getByText('-10% from target')).toHaveClass('text-red-600');
  });

  // 10. EmptyState Structure and Action
  it('EmptyState: displays title, description, and triggers onAction callback', () => {
    const onAction = vi.fn();
    render(
      <EmptyState
        title="No Bookings Yet"
        description="Explore available fitness classes to get started."
        actionLabel="Browse Classes"
        onAction={onAction}
      />
    );

    expect(screen.getByText('No Bookings Yet')).toBeInTheDocument();
    expect(screen.getByText('Explore available fitness classes to get started.')).toBeInTheDocument();

    const actionBtn = screen.getByRole('button', { name: 'Browse Classes' });
    fireEvent.click(actionBtn);
    expect(onAction).toHaveBeenCalledTimes(1);
  });

  // 11. EmptyState Custom Icon
  it('EmptyState: renders custom icon when provided', () => {
    render(
      <EmptyState
        title="No History"
        description="Nothing to display"
        icon={<AlertCircle data-testid="alert-icon" />}
      />
    );
    expect(screen.getByTestId('alert-icon')).toBeInTheDocument();
  });

  // 12. ErrorState Retry Flow
  it('ErrorState: renders error message and triggers onRetry', () => {
    const onRetry = vi.fn();
    render(
      <ErrorState
        title="Failed to load payments"
        errorMessage="Network request timed out. Please try again."
        onRetry={onRetry}
      />
    );

    expect(screen.getByText('Failed to load payments')).toBeInTheDocument();
    expect(screen.getByText('Network request timed out. Please try again.')).toBeInTheDocument();

    const retryBtn = screen.getByRole('button', { name: /Try again/i });
    fireEvent.click(retryBtn);
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  // 13. DisclaimerNote Required Wording
  it('DisclaimerNote: renders the mandatory non-medical wellness disclaimer', () => {
    const { rerender } = render(<DisclaimerNote />);
    expect(
      screen.getByText(/Wellness suggestions, not medical advice/i)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Rules are illustrative and must be reviewed by a qualified professional before real use/i)
    ).toBeInTheDocument();

    rerender(<DisclaimerNote variant="compact" />);
    expect(
      screen.getByText(/Wellness suggestions, not medical advice/i)
    ).toBeInTheDocument();
  });

  // 14. Skeleton Loader Elements
  it('Skeleton: renders with aria-hidden and animation class', () => {
    const { container } = render(<Skeleton className="w-48 h-6" />);
    const skeleton = container.firstChild as HTMLElement;
    expect(skeleton).toHaveClass('animate-pulse');
    expect(skeleton).toHaveAttribute('aria-hidden', 'true');
  });

  // 15. Accessibility checks
  it('Display elements: pass axe-core accessibility checks', async () => {
    const { container } = render(
      <div>
        <Badge status="ACTIVE" />
        <Tag label="Yoga" />
        <Card>
          <StatCard title="Classes" value="4" />
        </Card>
        <DisclaimerNote />
      </div>
    );
    await expectNoA11yViolations(container);
  });
});
