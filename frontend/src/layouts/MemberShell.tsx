import React, { useState } from 'react';
import { Outlet, useNavigate } from 'react-router-dom';
import { NavBar } from '../components/NavBar';
import { SideNav } from '../components/SideNav';
import { BottomTabs } from '../components/BottomTabs';
import { DisclaimerNote } from '../components/DisclaimerNote';
import { useAppDispatch, useAppSelector } from '../app/store';
import { clearCredentials } from '../features/auth/authSlice';
import { useLogoutMutation } from '../api/authApi';
import {
  getStoredThemePreference,
  setStoredThemePreference,
  ThemePreference,
} from '../lib/theme';

export const MemberShell: React.FC = () => {
  const { user } = useAppSelector((state) => state.auth);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [logoutMutation] = useLogoutMutation();

  const [theme, setTheme] = useState<ThemePreference>(() => getStoredThemePreference());

  const handleThemeToggle = () => {
    const nextTheme: ThemePreference = theme === 'dark' ? 'light' : 'dark';
    setTheme(nextTheme);
    setStoredThemePreference(nextTheme);
  };

  const handleLogout = async () => {
    try {
      await logoutMutation().unwrap();
    } catch {
      // Ignore network errors on logout
    } finally {
      dispatch(clearCredentials());
      navigate('/login');
    }
  };

  return (
    <div className="min-h-screen flex flex-col bg-bg-page text-text-primary">
      <NavBar
        currentRole="Member"
        userName={user?.fullName || user?.email}
        themePreference={theme}
        onThemeToggle={handleThemeToggle}
        onLogout={handleLogout}
      />

      <div className="flex flex-1">
        <SideNav />

        <main className="flex-1 p-4 md:p-8 pb-24 md:pb-8 max-w-7xl mx-auto w-full">
          <Outlet />

          <div className="mt-12">
            <DisclaimerNote variant="compact" />
          </div>
        </main>
      </div>

      <BottomTabs />
    </div>
  );
};
