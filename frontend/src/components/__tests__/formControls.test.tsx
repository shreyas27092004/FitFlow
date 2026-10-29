import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  Button,
  TextField,
  PasswordField,
  Checkbox,
  RadioGroup,
  Switch,
  DatePicker,
} from '../index';
import { Search, Mail } from 'lucide-react';
import { expectNoA11yViolations } from '../../test/setup';

describe('Design System Form Controls Suite', () => {
  // 1. Button Variants
  it('Button: renders different style variants correctly', () => {
    const { rerender } = render(<Button variant="secondary">Secondary</Button>);
    expect(screen.getByRole('button', { name: 'Secondary' })).toHaveClass('bg-bg-subtle');

    rerender(<Button variant="outline">Outline</Button>);
    expect(screen.getByRole('button', { name: 'Outline' })).toHaveClass('border');

    rerender(<Button variant="ghost">Ghost</Button>);
    expect(screen.getByRole('button', { name: 'Ghost' })).toHaveClass('hover:bg-bg-subtle');

    rerender(<Button variant="danger">Danger</Button>);
    expect(screen.getByRole('button', { name: 'Danger' })).toHaveClass('bg-red-600');

    rerender(<Button variant="link">Link</Button>);
    expect(screen.getByRole('button', { name: 'Link' })).toHaveClass('underline-offset-4');
  });

  // 2. Button Sizes
  it('Button: renders different size classes', () => {
    const { rerender } = render(<Button size="sm">Small</Button>);
    expect(screen.getByRole('button', { name: 'Small' })).toHaveClass('h-8');

    rerender(<Button size="lg">Large</Button>);
    expect(screen.getByRole('button', { name: 'Large' })).toHaveClass('h-12');
  });

  // 3. Button Icons
  it('Button: renders left and right icons with text', () => {
    render(
      <Button
        leftIcon={<Search data-testid="left-icon" />}
        rightIcon={<Mail data-testid="right-icon" />}
      >
        Search Mail
      </Button>
    );

    expect(screen.getByTestId('left-icon')).toBeInTheDocument();
    expect(screen.getByTestId('right-icon')).toBeInTheDocument();
    expect(screen.getByText('Search Mail')).toBeInTheDocument();
  });

  // 4. Button Disabled State
  it('Button: does not fire onClick when disabled', () => {
    const onClick = vi.fn();
    render(
      <Button disabled onClick={onClick}>
        Disabled
      </Button>
    );

    const btn = screen.getByRole('button', { name: 'Disabled' });
    expect(btn).toBeDisabled();
    fireEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });

  // 5. TextField Required Indicator
  it('TextField: displays required indicator when required=true', () => {
    render(<TextField label="Full Name" required />);
    expect(screen.getByLabelText(/Full Name/)).toBeRequired();
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  // 6. TextField Disabled State
  it('TextField: is disabled when disabled=true', () => {
    render(<TextField label="Username" disabled value="fitflow_user" onChange={() => {}} />);
    const input = screen.getByLabelText('Username');
    expect(input).toBeDisabled();
  });

  // 7. TextField Value and Change
  it('TextField: handles user text input', () => {
    const onChange = vi.fn();
    render(<TextField label="Email" onChange={onChange} />);
    const input = screen.getByLabelText('Email');

    fireEvent.change(input, { target: { value: 'member@fitflow.test' } });
    expect(onChange).toHaveBeenCalled();
  });

  // 8. TextField Prefix and Suffix Icons
  it('TextField: renders left and right icons', () => {
    render(
      <TextField
        label="Search"
        leftIcon={<Search data-testid="search-icon" />}
        rightIcon={<Mail data-testid="mail-icon" />}
      />
    );
    expect(screen.getByTestId('search-icon')).toBeInTheDocument();
    expect(screen.getByTestId('mail-icon')).toBeInTheDocument();
  });

  // 9. PasswordField Disabled State
  it('PasswordField: disables input and toggle button when disabled', () => {
    render(<PasswordField label="Password" disabled />);
    const input = screen.getByLabelText('Password');
    const toggle = screen.getByRole('button', { name: /password/i });

    expect(input).toBeDisabled();
    expect(toggle).toBeDisabled();
  });

  // 10. Checkbox State Changes
  it('Checkbox: toggles checked state when clicked', () => {
    const onCheckedChange = vi.fn();
    render(<Checkbox label="I agree to terms" onCheckedChange={onCheckedChange} />);

    const checkbox = screen.getByRole('checkbox', { name: 'I agree to terms' });
    expect(checkbox).toHaveAttribute('data-state', 'unchecked');

    fireEvent.click(checkbox);
    expect(onCheckedChange).toHaveBeenCalledWith(true);
  });

  // 11. Checkbox Disabled State
  it('Checkbox: cannot be toggled when disabled', () => {
    const onCheckedChange = vi.fn();
    render(<Checkbox label="Disabled option" disabled onCheckedChange={onCheckedChange} />);

    const checkbox = screen.getByRole('checkbox', { name: 'Disabled option' });
    expect(checkbox).toBeDisabled();
  });

  // 12. Checkbox Description and Accessibility
  it('Checkbox: displays description correctly', () => {
    render(
      <Checkbox label="Newsletter" description="Weekly fitness tips" />
    );
    expect(screen.getByText('Weekly fitness tips')).toBeInTheDocument();
  });

  // 13. RadioGroup Selection
  it('RadioGroup: selects option on user click', () => {
    const onValueChange = vi.fn();
    render(
      <RadioGroup
        label="Select Intensity"
        options={[
          { label: 'Low', value: 'LOW' },
          { label: 'Medium', value: 'MEDIUM' },
          { label: 'High', value: 'HIGH' },
        ]}
        value="LOW"
        onValueChange={onValueChange}
      />
    );

    const mediumRadio = screen.getByRole('radio', { name: 'Medium' });
    fireEvent.click(mediumRadio);
    expect(onValueChange).toHaveBeenCalledWith('MEDIUM');
  });

  // 14. Switch Toggle and Disabled
  it('Switch: toggles on click and honors disabled state', () => {
    const onCheckedChange = vi.fn();
    const { rerender } = render(
      <Switch label="Dark Mode" onCheckedChange={onCheckedChange} />
    );

    const switchEl = screen.getByRole('switch', { name: 'Dark Mode' });
    fireEvent.click(switchEl);
    expect(onCheckedChange).toHaveBeenCalledWith(true);

    rerender(
      <Switch label="Dark Mode" disabled onCheckedChange={onCheckedChange} />
    );
    expect(switchEl).toBeDisabled();
  });

  // 15. DatePicker value change
  it('DatePicker: renders label and updates date input', () => {
    const onChange = vi.fn();
    render(<DatePicker label="Birth Date" value="1995-05-15" onChange={onChange} />);

    const input = screen.getByLabelText('Birth Date');
    expect(input).toHaveValue('1995-05-15');

    fireEvent.change(input, { target: { value: '1995-06-20' } });
    expect(onChange).toHaveBeenCalled();
  });

  // 16. Accessibility validation of form container
  it('Form container: passes axe-core accessibility checks', async () => {
    const { container } = render(
      <form>
        <TextField label="Full Name" required defaultValue="Aarav Sharma" />
        <PasswordField label="Password" defaultValue="ValidPass123" />
        <Checkbox label="Remember me" />
        <Button type="submit">Submit Form</Button>
      </form>
    );
    await expectNoA11yViolations(container);
  });
});
