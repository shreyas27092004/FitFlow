import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { Badge } from '../../components/Badge';
import { Tabs } from '../../components/Tabs';
import { ConfirmDialog } from '../../components/ConfirmDialog';
import { EmptyState } from '../../components/EmptyState';
import { Skeleton } from '../../components/Skeleton';
import { useToast } from '../../components/Toast';
import { useGetMyBookingsQuery, useCancelBookingMutation } from '../../api/bookingsApi';
import { formatDateTime } from '../../lib/dates';
import { useIdempotencyKey } from '../../lib/idempotency';
import { Calendar, MapPin, User, AlertCircle } from 'lucide-react';
import en from '../../i18n/en.json';

export const MyBookingsPage: React.FC = () => {
  const navigate = useNavigate();
  const { showSuccess, showError } = useToast();
  const { data, isLoading, refetch } = useGetMyBookingsQuery();
  const [cancelBookingMutation, { isLoading: isCancelling }] = useCancelBookingMutation();
  const { idempotencyKey, resetKey } = useIdempotencyKey();

  const [cancelModalBooking, setCancelModalBooking] = useState<{
    id: string;
    classTitle: string;
    startsAt: string;
    isWaitlist: boolean;
  } | null>(null);

  const bookings = data?.content || [];
  const now = Date.now();

  const upcoming = bookings.filter(
    (b) => new Date(b.startsAt).getTime() > now && b.status !== 'CANCELLED'
  );
  const past = bookings.filter(
    (b) => new Date(b.startsAt).getTime() <= now || b.status === 'CANCELLED'
  );

  const handleConfirmCancel = async () => {
    if (!cancelModalBooking) return;
    try {
      await cancelBookingMutation({
        bookingId: cancelModalBooking.id,
        idempotencyKey,
      }).unwrap();
      resetKey();
      setCancelModalBooking(null);
      showSuccess('Reservation cancelled', 'Your spot has been successfully released.');
      refetch();
    } catch (err) {
      console.error('Cancellation failed:', err);
      showError('Cancellation failed', 'Could not cancel your booking. Please try again.');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">My Bookings</h1>
          <p className="text-sm text-text-secondary mt-1">
            Manage your reserved studio spots and view past training history.
          </p>
        </div>
        <Button
          variant="primary"
          size="sm"
          onClick={() => {
            navigate('/app/classes');
          }}
        >
          Book New Class
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-4">
          <Skeleton variant="card" className="h-32" />
          <Skeleton variant="card" className="h-32" />
        </div>
      ) : (
        <Tabs
          ariaLabel="Bookings filter"
          tabs={[
            {
              id: 'upcoming',
              label: 'Upcoming Classes',
              badge: upcoming.length > 0 ? <Badge variant="info">{upcoming.length}</Badge> : undefined,
              content: (
                <div className="space-y-4 pt-2">
                  {upcoming.length > 0 ? (
                    upcoming.map((b) => (
                      <Card
                        key={b.id}
                        className="p-5 border border-border flex flex-col md:flex-row items-start md:items-center justify-between gap-4"
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            {b.status === 'WAITLISTED' ? (
                              <Badge status="WAITLISTED">{`Waitlist #${b.waitlistPosition || 1}`}</Badge>
                            ) : (
                              <Badge status="CONFIRMED">Confirmed</Badge>
                            )}
                            <span className="text-xs font-semibold text-brand-primary uppercase">
                              {b.category}
                            </span>
                          </div>

                          <h3 className="text-lg font-bold">{b.classTitle}</h3>

                          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-text-secondary pt-1">
                            <span className="flex items-center gap-1">
                              <Calendar className="w-3.5 h-3.5 text-text-muted" aria-hidden="true" />
                              {formatDateTime(b.startsAt)}
                            </span>
                            <span className="flex items-center gap-1">
                              <User className="w-3.5 h-3.5 text-text-muted" aria-hidden="true" />
                              {b.trainerName}
                            </span>
                            <span className="flex items-center gap-1">
                              <MapPin className="w-3.5 h-3.5 text-text-muted" aria-hidden="true" />
                              {b.roomName}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 w-full md:w-auto">
                          <Button
                            variant="danger"
                            size="sm"
                            className="w-full md:w-auto"
                            onClick={() =>
                              setCancelModalBooking({
                                id: b.id,
                                classTitle: b.classTitle,
                                startsAt: b.startsAt,
                                isWaitlist: b.status === 'WAITLISTED',
                              })
                            }
                          >
                            {b.status === 'WAITLISTED' ? 'Leave Waitlist' : 'Cancel Reservation'}
                          </Button>
                        </div>
                      </Card>
                    ))
                  ) : (
                    <EmptyState
                      title="No upcoming bookings"
                      description={en.empty.bookings}
                      actionLabel="Explore Class Catalog"
                      onAction={() => {
                        navigate('/app/classes');
                      }}
                    />
                  )}
                </div>
              ),
            },
            {
              id: 'past',
              label: 'Past & Cancelled',
              content: (
                <div className="space-y-4 pt-2">
                  {past.length > 0 ? (
                    past.map((b) => (
                      <Card
                        key={b.id}
                        className="p-5 border border-border opacity-80 flex flex-col md:flex-row items-start md:items-center justify-between gap-4"
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-2">
                            {b.status === 'CANCELLED' ? (
                              <Badge status="CANCELLED">Cancelled</Badge>
                            ) : b.status === 'ATTENDED' ? (
                              <Badge status="SUCCEEDED">Attended</Badge>
                            ) : (
                              <Badge variant="neutral">Completed</Badge>
                            )}
                            <span className="text-xs text-text-muted">{b.category}</span>
                          </div>

                          <h3 className="text-base font-semibold">{b.classTitle}</h3>

                          <div className="flex items-center gap-4 text-xs text-text-muted pt-1">
                            <span>{formatDateTime(b.startsAt)}</span>
                            <span>Trainer: {b.trainerName}</span>
                          </div>
                        </div>
                      </Card>
                    ))
                  ) : (
                    <EmptyState
                      title="No past classes recorded"
                      description="Completed studio attendance records will appear here."
                    />
                  )}
                </div>
              ),
            },
          ]}
        />
      )}

      {/* Cancellation Warning Dialog */}
      {cancelModalBooking && (
        <ConfirmDialog
          isOpen={Boolean(cancelModalBooking)}
          onClose={() => setCancelModalBooking(null)}
          title={
            cancelModalBooking.isWaitlist
              ? 'Leave Class Waitlist?'
              : 'Cancel Class Reservation?'
          }
          description={`Are you sure you want to cancel your reservation for "${cancelModalBooking.classTitle}" scheduled for ${formatDateTime(cancelModalBooking.startsAt)}?`}
          confirmLabel={cancelModalBooking.isWaitlist ? 'Leave Waitlist' : 'Confirm Cancellation'}
          isDestructive={true}
          isLoading={isCancelling}
          onConfirm={handleConfirmCancel}
        >
          <div className="p-3 bg-amber-50 dark:bg-amber-950/30 border border-amber-200 dark:border-amber-900 rounded-md text-xs text-amber-900 dark:text-amber-200 flex items-start gap-2">
            <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" aria-hidden="true" />
            <div>
              <strong className="block font-semibold">2-Hour Studio Cutoff Rule:</strong>
              Cancellations made at least 2 hours before the session start time will immediately restore your monthly session credit. Cancellations made inside the 2-hour window forfeit the credit.
            </div>
          </div>
        </ConfirmDialog>
      )}
    </div>
  );
};
