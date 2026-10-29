import React, { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { Badge } from '../../components/Badge';
import { Banner } from '../../components/Banner';
import { Skeleton } from '../../components/Skeleton';
import { useGetSessionByIdQuery } from '../../api/classesApi';
import { useBookSessionMutation } from '../../api/bookingsApi';
import { useIdempotencyKey } from '../../lib/idempotency';
import { formatDateTime } from '../../lib/dates';
import {
  ArrowLeft,
  Calendar,
  Clock,
  MapPin,
  User,
  Users,
  CheckCircle2,
  Sparkles,
} from 'lucide-react';
import en from '../../i18n/en.json';

export const SessionDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { data: session, isLoading, error } = useGetSessionByIdQuery(id || '');
  const [bookMutation, { isLoading: isBooking }] = useBookSessionMutation();
  const { idempotencyKey, resetKey } = useIdempotencyKey();

  const [bookingResult, setBookingResult] = useState<{
    status: 'CONFIRMED' | 'WAITLISTED';
    position?: number;
  } | null>(null);

  const [errorState, setErrorState] = useState<{
    code: string;
    message: string;
    retryAfter?: number;
    showPlansPrompt?: boolean;
  } | null>(null);

  if (isLoading) {
    return (
      <div className="space-y-6">
        <Skeleton variant="button" className="w-24" />
        <Skeleton variant="card" className="h-64" />
      </div>
    );
  }

  if (error || !session) {
    return (
      <div className="text-center py-12">
        <h2 className="text-xl font-bold mb-2">Session Not Found</h2>
        <p className="text-sm text-text-muted mb-4">The session requested could not be loaded.</p>
        <Link to="/app/classes">
          <Button variant="outline" size="sm">
            Back to Classes
          </Button>
        </Link>
      </div>
    );
  }

  const isFull = session.bookedCount >= session.capacity;
  const isCancelled = session.status === 'CANCELLED';

  const handleBook = async () => {
    setErrorState(null);
    try {
      const res = await bookMutation({
        sessionId: session.id,
        idempotencyKey,
      }).unwrap();

      // Reset idempotency key after successful submission
      resetKey();

      if (res.status === 'WAITLISTED') {
        setBookingResult({ status: 'WAITLISTED', position: res.waitlistPosition });
      } else {
        setBookingResult({ status: 'CONFIRMED' });
      }
    } catch (err: any) {
      const code = err.code || 'BOOKING_RULE_VIOLATION';
      const msg = err.message || en.errors.INTERNAL_ERROR;

      if (code === 'UPGRADE_REQUIRED') {
        setErrorState({
          code,
          message: en.errors.UPGRADE_REQUIRED,
          showPlansPrompt: true,
        });
      } else if (code === 'SESSION_FULL') {
        setErrorState({
          code,
          message: en.errors.SESSION_FULL,
        });
      } else if (code === 'BOOKING_RULE_VIOLATION') {
        setErrorState({
          code,
          message: err.message || en.errors.BOOKING_RULE_VIOLATION,
        });
      } else if (code === 'AGE_RESTRICTED') {
        setErrorState({
          code,
          message: en.errors.AGE_RESTRICTED,
        });
      } else {
        setErrorState({
          code,
          message: msg,
          retryAfter: err.retryAfter,
        });
      }
    }
  };

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <Link
        to="/app/classes"
        className="inline-flex items-center gap-1.5 text-xs text-text-muted hover:text-text-primary font-medium"
      >
        <ArrowLeft className="w-3.5 h-3.5" aria-hidden="true" />
        <span>Back to Class Schedule</span>
      </Link>

      {/* Success Notification */}
      {bookingResult && (
        <Card className="p-6 border-green-300 bg-green-50/80 dark:border-green-800 dark:bg-green-950/30">
          <div className="flex items-start gap-4">
            <div className="w-10 h-10 rounded-full bg-green-100 text-green-700 dark:bg-green-900/60 flex items-center justify-center shrink-0">
              <CheckCircle2 className="w-6 h-6" aria-hidden="true" />
            </div>
            <div className="flex-1">
              <h3 className="font-bold text-lg text-green-950 dark:text-green-200">
                {bookingResult.status === 'CONFIRMED'
                  ? 'Booking Confirmed!'
                  : 'Waitlist Spot Confirmed!'}
              </h3>
              <p className="text-xs text-green-900 dark:text-green-300 mt-1">
                {bookingResult.status === 'CONFIRMED'
                  ? `Your spot in "${session.classTitle}" is reserved. We look forward to seeing you in the studio!`
                  : `You are #${bookingResult.position || 1} on the waitlist. You will automatically receive a confirmed spot if someone cancels.`}
              </p>
              <div className="mt-4 flex gap-3">
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => navigate('/app/bookings')}
                >
                  View My Bookings
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => navigate('/app/classes')}
                >
                  Browse More Classes
                </Button>
              </div>
            </div>
          </div>
        </Card>
      )}

      {/* Error / Upgrade Prompt */}
      {errorState && (
        <Banner type="danger" title={errorState.message}>
          {errorState.showPlansPrompt && (
            <div className="mt-3">
              <Button
                variant="primary"
                size="sm"
                onClick={() => navigate('/app/membership')}
              >
                View Membership Plans & Upgrades
              </Button>
            </div>
          )}
        </Banner>
      )}

      {/* Session Details Card */}
      <Card className="p-6 md:p-8 border border-border shadow-md">
        <div className="flex items-center justify-between gap-2 mb-3">
          <span className="text-xs font-bold uppercase tracking-wider text-brand-primary">
            {session.category} &bull; {session.intensity}
          </span>
          <div className="flex items-center gap-2">
            {session.safetyVerdict && <Badge status={session.safetyVerdict} />}
            {isCancelled ? (
              <Badge status="CANCELLED">Cancelled</Badge>
            ) : isFull ? (
              <Badge status="CAUTION">Session Full</Badge>
            ) : (
              <Badge status="ALLOWED">Available</Badge>
            )}
          </div>
        </div>

        <h1 className="text-2xl md:text-3xl font-extrabold text-text-primary mb-6">
          {session.classTitle}
        </h1>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 bg-bg-subtle rounded-lg mb-8 text-sm">
          <div className="flex items-center gap-2.5">
            <Calendar className="w-4 h-4 text-brand-primary shrink-0" aria-hidden="true" />
            <div>
              <span className="text-xs text-text-muted block">Start Time</span>
              <strong className="text-text-primary">{formatDateTime(session.startsAt)}</strong>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            <Clock className="w-4 h-4 text-brand-primary shrink-0" aria-hidden="true" />
            <div>
              <span className="text-xs text-text-muted block">End Time</span>
              <strong className="text-text-primary">{formatDateTime(session.endsAt)}</strong>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            <User className="w-4 h-4 text-brand-primary shrink-0" aria-hidden="true" />
            <div>
              <span className="text-xs text-text-muted block">Instructor</span>
              <strong className="text-text-primary">{session.trainerName}</strong>
            </div>
          </div>

          <div className="flex items-center gap-2.5">
            <MapPin className="w-4 h-4 text-brand-primary shrink-0" aria-hidden="true" />
            <div>
              <span className="text-xs text-text-muted block">Location / Studio</span>
              <strong className="text-text-primary">{session.roomName}</strong>
            </div>
          </div>

          <div className="flex items-center gap-2.5 sm:col-span-2 pt-2 border-t border-border">
            <Users className="w-4 h-4 text-brand-primary shrink-0" aria-hidden="true" />
            <div>
              <span className="text-xs text-text-muted block">Spots Availability</span>
              <strong className="text-text-primary">
                {session.bookedCount} / {session.capacity} spots filled
              </strong>
            </div>
          </div>
        </div>

        {/* Action Button */}
        {!bookingResult && (
          <div className="pt-2">
            {isCancelled ? (
              <Button variant="outline" size="lg" disabled className="w-full">
                This Session is Cancelled
              </Button>
            ) : isFull ? (
              <Button
                variant="outline"
                size="lg"
                onClick={handleBook}
                disabled={isBooking}
                isLoading={isBooking}
                loadingText="Joining waitlist..."
                className="w-full"
                leftIcon={<Sparkles className="w-4 h-4 text-amber-500" />}
              >
                Join Waitlist
              </Button>
            ) : (
              <Button
                variant="primary"
                size="lg"
                onClick={handleBook}
                disabled={isBooking}
                isLoading={isBooking}
                loadingText="Confirming reservation..."
                className="w-full"
              >
                Confirm Spot Reservation
              </Button>
            )}
            <p className="text-[11px] text-text-muted text-center mt-3">
              Cancellation is permitted up to 2 hours before start time with full credit restoration.
            </p>
          </div>
        )}
      </Card>
    </div>
  );
};
