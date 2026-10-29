import React from 'react';
import { Clock, MapPin, User, Users } from 'lucide-react';
import { Card } from './Card';
import { Badge } from './Badge';
import { Button } from './Button';
import { formatDateTime } from '../lib/dates';
import { clsx } from 'clsx';

export interface SessionData {
  id: string;
  classTitle: string;
  category: string;
  trainerName: string;
  roomName: string;
  startsAt: string;
  endsAt: string;
  capacity: number;
  bookedCount: number;
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  safetyVerdict?: 'ALLOWED' | 'CAUTION' | 'RESTRICTED' | 'RED_FLAG';
}

export interface SessionCardProps {
  session: SessionData;
  isBooked?: boolean;
  waitlistPosition?: number;
  onBook?: (sessionId: string) => void;
  onCancel?: (sessionId: string) => void;
  onJoinWaitlist?: (sessionId: string) => void;
  isLoading?: boolean;
}

export const SessionCard: React.FC<SessionCardProps> = ({
  session,
  isBooked = false,
  waitlistPosition,
  onBook,
  onCancel,
  onJoinWaitlist,
  isLoading = false,
}) => {
  const isFull = session.bookedCount >= session.capacity;
  const isCancelled = session.status === 'CANCELLED';
  const isWaitlisted = waitlistPosition !== undefined && waitlistPosition > 0;

  return (
    <Card className="flex flex-col justify-between h-full border border-border hover:shadow-md transition-shadow">
      <div>
        <div className="flex items-start justify-between gap-2 mb-2">
          <span className="text-xs font-semibold uppercase tracking-wider text-brand-primary">
            {session.category}
          </span>
          <div className="flex items-center gap-1.5 flex-wrap justify-end">
            {session.safetyVerdict && <Badge status={session.safetyVerdict} />}
            {isBooked ? (
              <Badge status="CONFIRMED">Booked</Badge>
            ) : isWaitlisted ? (
              <Badge status="WAITLISTED">{`Waitlist #${waitlistPosition}`}</Badge>
            ) : isCancelled ? (
              <Badge status="CANCELLED">Cancelled</Badge>
            ) : isFull ? (
              <Badge status="CAUTION">Full</Badge>
            ) : (
              <Badge status="ALLOWED">Available</Badge>
            )}
          </div>
        </div>

        <h3 className="text-base font-bold text-text-primary mb-2 line-clamp-1">
          {session.classTitle}
        </h3>

        <div className="flex flex-col gap-1.5 text-xs text-text-secondary mb-4">
          <div className="flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5 text-text-muted shrink-0" aria-hidden="true" />
            <span>{formatDateTime(session.startsAt)}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <User className="w-3.5 h-3.5 text-text-muted shrink-0" aria-hidden="true" />
            <span>Trainer: {session.trainerName}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <MapPin className="w-3.5 h-3.5 text-text-muted shrink-0" aria-hidden="true" />
            <span>{session.roomName}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Users className="w-3.5 h-3.5 text-text-muted shrink-0" aria-hidden="true" />
            <span
              className={clsx(
                'font-medium',
                isFull ? 'text-amber-600 dark:text-amber-400' : 'text-text-secondary'
              )}
            >
              {session.bookedCount} / {session.capacity} spots filled
            </span>
          </div>
        </div>
      </div>

      <div className="pt-3 border-t border-border flex items-center justify-between gap-2">
        {isBooked ? (
          <Button
            type="button"
            variant="danger"
            size="sm"
            onClick={() => onCancel?.(session.id)}
            disabled={isLoading || isCancelled}
            isLoading={isLoading}
            className="w-full"
          >
            Cancel Booking
          </Button>
        ) : isWaitlisted ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onCancel?.(session.id)}
            disabled={isLoading}
            isLoading={isLoading}
            className="w-full"
          >
            Leave Waitlist
          </Button>
        ) : isFull ? (
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => onJoinWaitlist?.(session.id)}
            disabled={isLoading || isCancelled}
            isLoading={isLoading}
            className="w-full"
          >
            Join Waitlist
          </Button>
        ) : (
          <Button
            type="button"
            variant="primary"
            size="sm"
            onClick={() => onBook?.(session.id)}
            disabled={isLoading || isCancelled}
            isLoading={isLoading}
            className="w-full"
          >
            Book Session
          </Button>
        )}
      </div>
    </Card>
  );
};
