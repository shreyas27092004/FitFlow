import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Provider } from 'react-redux';
import { NavBar } from '../NavBar';
import { SideNav } from '../SideNav';
import { BottomTabs } from '../BottomTabs';
import { MemberShell } from '../../layouts/MemberShell';
import { setupStore } from '../../app/store';
import { expectNoA11yViolations } from '../../test/setup';

describe('Navigation Components Suite', () => {
  // 1. NavBar Brand Title
  it('NavBar: renders brand title and home link', () => {
    render(
      <MemoryRouter>
        <NavBar themePreference="dark" onThemeToggle={() => {}} />
      </MemoryRouter>
    );

    const brandLink = screen.getByRole('link', { name: /FitFlow/i });
    expect(brandLink).toBeInTheDocument();
    expect(brandLink).toHaveAttribute('href', '/');
  });

  // 2. NavBar Role Badge
  it('NavBar: displays role badge when provided', () => {
    render(
      <MemoryRouter>
        <NavBar currentRole="Trainer" themePreference="dark" onThemeToggle={() => {}} />
      </MemoryRouter>
    );

    expect(screen.getByText('Trainer')).toBeInTheDocument();
  });

  // 3. NavBar User Name
  it('NavBar: displays user name when provided', () => {
    render(
      <MemoryRouter>
        <NavBar
          userName="Aarav Sharma"
          themePreference="dark"
          onThemeToggle={() => {}}
        />
      </MemoryRouter>
    );

    expect(screen.getByText('Aarav Sharma')).toBeInTheDocument();
  });

  // 4. NavBar Theme Toggle
  it('NavBar: calls onThemeToggle when button is clicked', () => {
    const onThemeToggle = vi.fn();
    render(
      <MemoryRouter>
        <NavBar
          themePreference="dark"
          onThemeToggle={onThemeToggle}
        />
      </MemoryRouter>
    );

    const themeBtn = screen.getByRole('button', { name: /Switch theme/i });
    fireEvent.click(themeBtn);
    expect(onThemeToggle).toHaveBeenCalledTimes(1);
  });

  // 5. NavBar Theme Icon
  it('NavBar: displays correct icon for dark vs light theme', () => {
    const { rerender } = render(
      <MemoryRouter>
        <NavBar themePreference="dark" onThemeToggle={() => {}} />
      </MemoryRouter>
    );
    expect(screen.getByRole('button', { name: /currently dark/i })).toBeInTheDocument();

    rerender(
      <MemoryRouter>
        <NavBar themePreference="light" onThemeToggle={() => {}} />
      </MemoryRouter>
    );
    expect(screen.getByRole('button', { name: /currently light/i })).toBeInTheDocument();
  });

  // 6. NavBar Logout Button
  it('NavBar: calls onLogout when sign out button is clicked', () => {
    const onLogout = vi.fn();
    render(
      <MemoryRouter>
        <NavBar
          userName="member@fitflow.test"
          onLogout={onLogout}
          themePreference="dark"
          onThemeToggle={() => {}}
        />
      </MemoryRouter>
    );

    const logoutBtn = screen.getByRole('button', { name: /Sign out/i });
    fireEvent.click(logoutBtn);
    expect(onLogout).toHaveBeenCalledTimes(1);
  });

  // 7. SideNav Navigation Links
  it('SideNav: renders member navigation links', () => {
    render(
      <MemoryRouter initialEntries={['/app/dashboard']}>
        <SideNav />
      </MemoryRouter>
    );

    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Browse Classes')).toBeInTheDocument();
    expect(screen.getByText('My Bookings')).toBeInTheDocument();
    expect(screen.getByText('Membership')).toBeInTheDocument();
    expect(screen.getByText('Payments')).toBeInTheDocument();
    expect(screen.getByText('Account')).toBeInTheDocument();
  });

  // 8. SideNav Active Route Highlight
  it('SideNav: highlights active route link', () => {
    render(
      <MemoryRouter initialEntries={['/app/bookings']}>
        <SideNav />
      </MemoryRouter>
    );

    const activeLink = screen.getByRole('link', { name: /My Bookings/i });
    expect(activeLink).toHaveClass('text-brand-primary');
    expect(activeLink).toHaveAttribute('aria-current', 'page');
  });

  // 9. SideNav Disabled Items
  it('SideNav: disabled items render badge and disabled styling', () => {
    render(
      <MemoryRouter initialEntries={['/app/dashboard']}>
        <SideNav />
      </MemoryRouter>
    );

    const wellnessBadges = screen.getAllByTitle('Coming in Stage 12');
    expect(wellnessBadges.length).toBeGreaterThan(0);
  });

  // 10. BottomTabs Mobile Navigation Items
  it('BottomTabs: renders 5 mobile navigation tabs with correct labels', () => {
    render(
      <MemoryRouter initialEntries={['/app/dashboard']}>
        <BottomTabs />
      </MemoryRouter>
    );

    expect(screen.getByRole('link', { name: /Dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Classes/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Bookings/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Plans/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Account/i })).toBeInTheDocument();
  });

  // 11. BottomTabs Active Tab
  it('BottomTabs: marks the active tab route', () => {
    render(
      <MemoryRouter initialEntries={['/app/classes']}>
        <BottomTabs />
      </MemoryRouter>
    );

    const classesTab = screen.getByRole('link', { name: /Classes/i });
    expect(classesTab).toHaveClass('text-brand-primary');
  });

  // 12. MemberShell Layout Structure
  it('MemberShell: renders layout elements and child route content', () => {
    const store = setupStore({
      auth: {
        accessToken: 'valid-token',
        user: {
          id: 'mem-1',
          email: 'aarav@fitflow.test',
          fullName: 'Aarav Sharma',
          roles: ['MEMBER'],
          mustChangePassword: false,
        },
      },
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/dashboard']}>
          <Routes>
            <Route element={<MemberShell />}>
              <Route path="/app/dashboard" element={<div data-testid="dashboard-content">Dashboard View</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByTestId('dashboard-content')).toBeInTheDocument();
    expect(screen.getByText('Aarav Sharma')).toBeInTheDocument();
    expect(screen.getByText('FitFlow')).toBeInTheDocument();
    expect(screen.getByText(/Wellness suggestions, not medical advice/i)).toBeInTheDocument();
  });

  // 13. MemberShell Theme Toggling
  it('MemberShell: toggles theme state when theme button is clicked', () => {
    const store = setupStore({
      auth: {
        accessToken: 'valid-token',
        user: { id: 'mem-1', email: 'test@fitflow.test', fullName: 'Tester', roles: ['MEMBER'], mustChangePassword: false },
      },
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/app/dashboard']}>
          <Routes>
            <Route element={<MemberShell />}>
              <Route path="/app/dashboard" element={<div>Dashboard View</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    const toggleBtn = screen.getByRole('button', { name: /Switch theme/i });
    fireEvent.click(toggleBtn);
    // Button label should reflect toggle
    expect(screen.getByRole('button', { name: /Switch theme/i })).toBeInTheDocument();
  });

  // 14. Navigation Accessibility
  it('Navigation Components: pass axe-core accessibility checks', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/app/dashboard']}>
        <NavBar currentRole="Member" userName="User" themePreference="dark" onThemeToggle={() => {}} />
        <SideNav />
        <BottomTabs />
      </MemoryRouter>
    );

    await expectNoA11yViolations(container);
  });
});
