import React, { useState } from 'react';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { Badge } from '../../components/Badge';
import { PlanCard } from '../../components/PlanCard';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { Skeleton } from '../../components/Skeleton';
import { CheckoutLauncher } from './CheckoutLauncher';
import {
  useGetMyMembershipQuery,
  useGetPlansQuery,
  useGetChangePreviewMutation,
  useCancelMembershipMutation,
  ChangePreviewDto,
} from '../../api/membershipApi';
import { useIdempotencyKey } from '../../lib/idempotency';
import { formatDateOnly } from '../../lib/dates';
import { Zap } from 'lucide-react';

export const MembershipPage: React.FC = () => {
  const { data: membership, isLoading: memLoading, refetch: refetchMem } = useGetMyMembershipQuery();
  const { data: plans, isLoading: plansLoading } = useGetPlansQuery();

  const [getChangePreviewMutation, { isLoading: isPreviewing }] = useGetChangePreviewMutation();
  const [cancelMembershipMutation, { isLoading: isCancelling }] = useCancelMembershipMutation();
  const { idempotencyKey, resetKey } = useIdempotencyKey();

  const [activePreview, setActivePreview] = useState<{
    targetPlanId: string;
    targetPlanName: string;
    isNew: boolean;
    preview: ChangePreviewDto;
  } | null>(null);

  const [isCancelModalOpen, setIsCancelModalOpen] = useState(false);

  const handleSelectPlan = async (targetPlanId: string) => {
    const targetPlan = plans?.find((p) => p.id === targetPlanId);
    if (!targetPlan) return;

    try {
      const preview = await getChangePreviewMutation({
        targetPlanId,
      }).unwrap();

      setActivePreview({
        targetPlanId,
        targetPlanName: targetPlan.name,
        isNew: !membership,
        preview,
      });
    } catch (err) {
      console.error('Failed to preview plan change:', err);
    }
  };

  const handleRefreshPreview = async (): Promise<ChangePreviewDto | void> => {
    if (!activePreview) return;
    try {
      const fresh = await getChangePreviewMutation({
        targetPlanId: activePreview.targetPlanId,
      }).unwrap();
      return fresh;
    } catch {
      // Ignore
    }
  };

  const handleConfirmCancelMembership = async () => {
    try {
      await cancelMembershipMutation({
        idempotencyKey,
      }).unwrap();
      resetKey();
      setIsCancelModalOpen(false);
      refetchMem();
    } catch (err) {
      console.error('Failed to cancel membership:', err);
    }
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">Studio Membership</h1>
        <p className="text-sm text-text-secondary mt-1">
          Review your current monthly session allowance and upgrade or adjust your membership tier.
        </p>
      </div>

      {/* Current Subscription Card */}
      {memLoading ? (
        <Skeleton variant="card" className="h-44" />
      ) : membership ? (
        <Card className="p-6 md:p-8 border border-border shadow-sm">
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-border">
            <div>
              <div className="flex items-center gap-2 mb-1.5">
                <Badge status={membership.status === 'ACTIVE' ? 'ACTIVE' : 'CAUTION'}>
                  {membership.status}
                </Badge>
                <span className="text-xs text-text-muted">
                  Renews on {formatDateOnly(membership.renewsAt)}
                </span>
              </div>
              <h2 className="text-2xl font-bold">{membership.planName}</h2>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={() => setIsCancelModalOpen(true)}
              className="text-red-600 border-red-200 hover:bg-red-50 dark:border-red-900 dark:hover:bg-red-950"
            >
              Cancel Membership
            </Button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 pt-6">
            <div>
              <span className="text-xs text-text-muted block">Remaining Class Credits</span>
              <div className="text-3xl font-extrabold text-brand-primary mt-1">
                {membership.sessionsRemaining}
                <span className="text-sm font-normal text-text-muted ml-1">
                  / {membership.sessionsTotal} this cycle
                </span>
              </div>
            </div>

            <div>
              <span className="text-xs text-text-muted block">Free Trial Sessions</span>
              <div className="text-2xl font-bold text-text-primary mt-1">
                {membership.freeSessionsRemaining !== undefined
                  ? `${membership.freeSessionsRemaining} left`
                  : 'N/A'}
              </div>
            </div>

            <div>
              <span className="text-xs text-text-muted block">Billing Cycle</span>
              <div className="text-sm font-semibold text-text-primary mt-1">
                Started: {formatDateOnly(membership.startsAt)}
              </div>
            </div>
          </div>
        </Card>
      ) : (
        <Card className="p-6 border-dashed border-2 border-border text-center">
          <div className="w-12 h-12 rounded-full bg-brand-primary/10 text-brand-primary flex items-center justify-center mx-auto mb-3">
            <Zap className="w-6 h-6" aria-hidden="true" />
          </div>
          <h2 className="text-lg font-bold mb-1">No Active Membership</h2>
          <p className="text-xs text-text-secondary max-w-sm mx-auto mb-4">
            Select a monthly studio plan below to unlock spot reservations across all class styles.
          </p>
        </Card>
      )}

      {/* Available Plans Catalog */}
      <div>
        <div className="mb-6">
          <h2 className="text-xl font-bold">Available Membership Plans</h2>
          <p className="text-xs text-text-secondary">
            Switch plans anytime. Upgrades credit your unused sessions toward your new tier immediately.
          </p>
        </div>

        {plansLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Skeleton variant="card" className="h-80" />
            <Skeleton variant="card" className="h-80" />
            <Skeleton variant="card" className="h-80" />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {plans?.map((p) => {
              const isCurrent = membership?.planId === p.id;
              return (
                <PlanCard
                  key={p.id}
                  plan={p}
                  isCurrent={isCurrent}
                  actionLabel={membership ? 'Switch to this Plan' : 'Subscribe'}
                  onSelect={handleSelectPlan}
                  isLoading={isPreviewing}
                />
              );
            })}
          </div>
        )}
      </div>

      {/* Checkout / Change Preview Dialog */}
      {activePreview && (
        <div
          role="dialog"
          aria-modal="true"
          aria-label="Review Membership Change"
          className="fixed inset-0 z-modal flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in"
        >
          <div className="w-full max-w-lg">
            <CheckoutLauncher
              targetPlanId={activePreview.targetPlanId}
              targetPlanName={activePreview.targetPlanName}
              isNewSubscription={activePreview.isNew}
              preview={activePreview.preview}
              onClose={() => setActivePreview(null)}
              onRefreshPreview={handleRefreshPreview}
            />
          </div>
        </div>
      )}

      {/* Cancellation Dialog */}
      <ConfirmDialog
        isOpen={isCancelModalOpen}
        onClose={() => setIsCancelModalOpen(false)}
        title="Cancel FitFlow Membership?"
        description="Are you sure you want to cancel your monthly subscription? You will retain access to your remaining session credits until the end of the current billing cycle, but your plan will not automatically renew."
        confirmLabel="Confirm Cancellation"
        isDestructive={true}
        isLoading={isCancelling}
        onConfirm={handleConfirmCancelMembership}
      />
    </div>
  );
};
