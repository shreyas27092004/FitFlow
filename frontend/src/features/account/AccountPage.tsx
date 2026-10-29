import React, { useState } from 'react';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { PasswordField } from '../../components/PasswordField';
import { Banner } from '../../components/Banner';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Skeleton } from '../../components/Skeleton';
import {
  useGetProfileQuery,
  useUpdateProfileMutation,
  useLazyExportDataQuery,
  useDeleteAccountMutation,
} from '../../api/accountApi';
import { useChangePasswordMutation } from '../../api/authApi';
import { useAppDispatch } from '../../app/store';
import { clearCredentials } from '../auth/authSlice';
import { useNavigate } from 'react-router-dom';
import { User, Lock, Download, Trash2 } from 'lucide-react';

export const AccountPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const { data: profile, isLoading, refetch } = useGetProfileQuery();
  const [updateProfileMutation, { isLoading: isUpdatingProfile }] = useUpdateProfileMutation();
  const [changePasswordMutation, { isLoading: isChangingPassword }] = useChangePasswordMutation();
  const [triggerExport, { isFetching: isExporting }] = useLazyExportDataQuery();
  const [deleteAccountMutation, { isLoading: isDeletingAccount }] = useDeleteAccountMutation();

  // Profile Form state
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [profileSuccess, setProfileSuccess] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Sync profile fields on load
  React.useEffect(() => {
    if (profile) {
      setFullName(profile.fullName || '');
      setPhone(profile.phone || '');
    }
  }, [profile]);

  // Password Form state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  // Account Delete Modal state
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!profile) return;
    setProfileSuccess(false);
    setProfileError(null);

    try {
      await updateProfileMutation({
        fullName,
        phone,
        version: profile.version,
      }).unwrap();
      setProfileSuccess(true);
      refetch();
    } catch (err: any) {
      if (err.status === 409 || err.code === 'CONFLICT' || err.code === 'OPTIMISTIC_LOCK') {
        setProfileError('Profile was updated elsewhere. Reloading latest information...');
        refetch();
      } else {
        setProfileError(err.message || 'Failed to update profile information.');
      }
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordSuccess(false);
    setPasswordError(null);

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }

    if (newPassword.length < 10) {
      setPasswordError('New password must be at least 10 characters.');
      return;
    }

    try {
      await changePasswordMutation({
        currentPassword,
        newPassword,
      }).unwrap();
      setPasswordSuccess(true);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      setPasswordError(err.message || 'Failed to change password. Please check your current password.');
    }
  };

  const handleExportData = async () => {
    try {
      const data = await triggerExport().unwrap();
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: 'application/json',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `fitflow-data-export-${new Date().toISOString().slice(0, 10)}.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Export failed:', err);
    }
  };

  const handleConfirmDeleteAccount = async () => {
    setDeleteError(null);
    try {
      await deleteAccountMutation({
        confirmation: 'DELETE',
        password: deletePassword,
      }).unwrap();

      dispatch(clearCredentials());
      navigate('/login');
    } catch (err: any) {
      setDeleteError(err.message || 'Failed to delete account. Please verify your password.');
    }
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton variant="card" className="h-44" />
        <Skeleton variant="card" className="h-44" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl space-y-8">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">Account Settings</h1>
        <p className="text-sm text-text-secondary mt-1">
          Manage your personal profile, security credentials, and DPDP privacy rights.
        </p>
      </div>

      {/* Edit Profile */}
      <Card className="p-6 md:p-8 border border-border shadow-sm">
        <div className="flex items-center gap-2 mb-6 pb-4 border-b border-border">
          <User className="w-5 h-5 text-brand-primary" aria-hidden="true" />
          <h2 className="text-lg font-bold">Personal Profile</h2>
        </div>

        {profileSuccess && (
          <div className="mb-4">
            <Banner type="success" title="Profile updated successfully!" />
          </div>
        )}

        {profileError && (
          <div className="mb-4">
            <Banner type="danger" title={profileError} />
          </div>
        )}

        <form onSubmit={handleUpdateProfile} className="space-y-4">
          <TextField
            label="Email Address"
            type="email"
            value={profile?.email || ''}
            disabled
            helperText="Account email cannot be modified directly."
          />

          <TextField
            label="Full Name"
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            required
          />

          <TextField
            label="Phone Number"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="+91 98765 43210"
          />

          <div className="flex items-center justify-between pt-2">
            <span className="text-xs text-text-muted">
              Record Version: {profile?.version} (Optimistic lock protection)
            </span>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              disabled={isUpdatingProfile}
              isLoading={isUpdatingProfile}
            >
              Save Profile Changes
            </Button>
          </div>
        </form>
      </Card>

      {/* Change Password */}
      <Card className="p-6 md:p-8 border border-border shadow-sm">
        <div className="flex items-center gap-2 mb-6 pb-4 border-b border-border">
          <Lock className="w-5 h-5 text-brand-primary" aria-hidden="true" />
          <h2 className="text-lg font-bold">Change Password</h2>
        </div>

        {passwordSuccess && (
          <div className="mb-4">
            <Banner type="success" title="Password changed successfully!" />
          </div>
        )}

        {passwordError && (
          <div className="mb-4">
            <Banner type="danger" title={passwordError} />
          </div>
        )}

        <form onSubmit={handleChangePassword} className="space-y-4">
          <PasswordField
            label="Current Password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            required
          />

          <PasswordField
            label="New Password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            required
            helperText="Minimum 10 characters with at least one letter and one number."
          />

          <PasswordField
            label="Confirm New Password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            required
          />

          <div className="text-right pt-2">
            <Button
              type="submit"
              variant="primary"
              size="sm"
              disabled={isChangingPassword}
              isLoading={isChangingPassword}
            >
              Update Password
            </Button>
          </div>
        </form>
      </Card>

      {/* Data Privacy & Self-Service Data Rights */}
      <Card className="p-6 md:p-8 border border-border shadow-sm">
        <h2 className="text-lg font-bold mb-2">Data Privacy & Self-Service Rights</h2>
        <p className="text-xs text-text-secondary mb-6">
          Under India’s Digital Personal Data Protection (DPDP) Act, you have the absolute right to export a complete machine-readable copy of your profile and history, or permanently delete your account.
        </p>

        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-4 bg-bg-subtle rounded-lg mb-6">
          <div>
            <h4 className="font-semibold text-sm">Export My Studio Data</h4>
            <p className="text-xs text-text-muted mt-0.5">
              Download your profile, bookings, and payments records in structured JSON format.
            </p>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleExportData}
            isLoading={isExporting}
            leftIcon={<Download className="w-4 h-4" />}
          >
            Export Data (JSON)
          </Button>
        </div>

        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 p-4 border border-red-200 dark:border-red-950 bg-red-50/40 dark:bg-red-950/20 rounded-lg">
          <div>
            <h4 className="font-semibold text-sm text-red-900 dark:text-red-300">
              Permanently Delete Account
            </h4>
            <p className="text-xs text-red-700 dark:text-red-400 mt-0.5">
              Permanently erase your account, membership records, and personal health data. This cannot be undone.
            </p>
          </div>
          <Button
            type="button"
            variant="danger"
            size="sm"
            onClick={() => setIsDeleteModalOpen(true)}
            leftIcon={<Trash2 className="w-4 h-4" />}
          >
            Delete Account
          </Button>
        </div>
      </Card>

      {/* Delete Account Modal with typed confirmation */}
      {isDeleteModalOpen && (
        <ConfirmDialog
          isOpen={isDeleteModalOpen}
          onClose={() => {
            setIsDeleteModalOpen(false);
            setDeletePassword('');
            setDeleteError(null);
          }}
          title="Delete Account Permanently?"
          description="This action is irreversible. All of your upcoming bookings, membership entitlements, and wellness data will be permanently purged."
          confirmationWord="DELETE"
          confirmLabel="Permanently Delete My Account"
          isDestructive={true}
          isLoading={isDeletingAccount}
          onConfirm={handleConfirmDeleteAccount}
        >
          <div className="space-y-4 mt-2">
            {deleteError && <Banner type="danger" title={deleteError} />}
            <PasswordField
              label="Re-enter Account Password"
              value={deletePassword}
              onChange={(e) => setDeletePassword(e.target.value)}
              required
            />
          </div>
        </ConfirmDialog>
      )}
    </div>
  );
};
