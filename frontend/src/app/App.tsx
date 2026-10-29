import React, { useEffect } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store, useAppDispatch } from './store';
import { routes } from './routes';
import { ToastProvider } from '../components/Toast';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { initTheme } from '../lib/theme';
import { useRefreshMutation } from '../api/authApi';
import { updateAccessToken, setInitialized } from '../features/auth/authSlice';

const router = createBrowserRouter(routes);

const AppContent: React.FC = () => {
  const dispatch = useAppDispatch();
  const [refreshMutation] = useRefreshMutation();

  useEffect(() => {
    // 1. Initialize light/dark theme preference
    initTheme();

    // 2. Silent token check on boot via HttpOnly cookie
    const bootstrapAuth = async () => {
      try {
        const res = await refreshMutation().unwrap();
        if (res.accessToken) {
          dispatch(updateAccessToken(res.accessToken));
        }
      } catch {
        // No valid cookie or refresh failed; leave unauthenticated
      } finally {
        dispatch(setInitialized());
      }
    };

    bootstrapAuth();
  }, [dispatch, refreshMutation]);

  return (
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  );
};

export const App: React.FC = () => {
  return (
    <Provider store={store}>
      <ToastProvider>
        <AppContent />
      </ToastProvider>
    </Provider>
  );
};
