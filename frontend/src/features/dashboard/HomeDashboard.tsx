import React from 'react';
import { Link } from 'react-router-dom';
import { StatCard } from '../../components/StatCard';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { Badge } from '../../components/Badge';
import { SessionCard } from '../../components/SessionCard';
import { EmptyState } from '../../components/EmptyState';
import { Skeleton } from '../../components/Skeleton';
import { useGetProfileQuery } from '../../api/accountApi';
import { useGetMyMembershipQuery } from '../../api/membershipApi';
import { useGetMyBookingsQuery } from '../../api/bookingsApi';
import { useGetSessionsQuery } from '../../api/classesApi';
import { formatDateTime } from '../../lib/dates';
import {
  Calendar,
  CreditCard,
  Flame,
  Award,
  ArrowRight,
} from 'lucide-react';
import en from '../../i18n/en.json';

export const HomeDashboard: React.FC = () => {
  const { data: profile } = useGetProfileQuery();
  const { data: membership, isLoading: memLoading } = useGetMyMembershipQuery();
  const { data: bookingsData, isLoading: bookLoading } = useGetMyBookingsQuery({ status: 'CONFIRMED' });
  const { data: sessionsData, isLoading: sessLoading } = useGetSessionsQuery({ size: 3 });

  const upcomingBookings = bookingsData?.content?.filter(
    (b) => new Date(b.startsAt).getTime() > Date.now()
  ) || [];

  return (
    <div className="space-y-8">
      {/* Welcome & Member Status Banner */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border pb-6">
        <div>
          <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">
            Welcome back, {profile?.fullName || 'Member'}!
          </h1>
          <p className="text-sm text-text-secondary mt-1">
            Track your weekly fitness progress, upcoming classes, and studio membership credits.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link to="/app/classes">
            <Button variant="primary" size="md" leftIcon={<Calendar className="w-4 h-4" />}>
              Book a Class
            </Button>
          </Link>
          <Link to="/app/membership">
            <Button variant="outline" size="md" leftIcon={<CreditCard className="w-4 h-4" />}>
              Manage Plan
            </Button>
          </Link>
        </div>
      </div>

      {/* Metric Stat Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Plan Sessions Left"
          value={memLoading ? '...' : membership ? `${membership.sessionsRemaining} / ${membership.sessionsTotal}` : '0'}
          subtitle={membership ? `Plan: ${membership.planName}` : 'No active plan'}
          icon={<CreditCard className="w-5 h-5" />}
        />

        <StatCard
          title="Upcoming Classes"
          value={bookLoading ? '...' : upcomingBookings.length}
          subtitle="Next 7 days"
          icon={<Calendar className="w-5 h-5" />}
        />

        <StatCard
          title="Completed Classes"
          value={bookingsData?.content?.filter((b) => b.status === 'ATTENDED').length || 0}
          trendLabel="+2 this week"
          trendDirection="up"
          icon={<Award className="w-5 h-5" />}
        />

        <StatCard
          title="Active Streak"
          value="4 Days"
          subtitle="Keep it moving!"
          icon={<Flame className="w-5 h-5" />}
        />
      </div>

      {/* Next Up / Scheduled Booking */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-bold">Your Next Scheduled Class</h2>
          <Link to="/app/bookings" className="text-xs text-brand-primary font-medium hover:underline flex items-center gap-1">
            <span>View All Bookings</span>
            <ArrowRight className="w-3.5 h-3.5" aria-hidden="true" />
          </Link>
        </div>

        {bookLoading ? (
          <Skeleton variant="card" className="h-32" />
        ) : upcomingBookings.length > 0 ? (
          <Card className="p-6 border-l-4 border-l-brand-primary flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 mb-1.5">
                <Badge status="CONFIRMED">Confirmed</Badge>
                <span className="text-xs text-text-muted">
                  {formatDateTime(upcomingBookings[0].startsAt)}
                </span>
              </div>
              <h3 className="text-xl font-bold">{upcomingBookings[0].classTitle}</h3>
              <p className="text-xs text-text-secondary mt-1">
                Trainer: {upcomingBookings[0].trainerName} &bull; Room: {upcomingBookings[0].roomName}
              </p>
            </div>
            <Link to="/app/bookings">
              <Button variant="outline" size="sm">
                Manage Booking
              </Button>
            </Link>
          </Card>
        ) : (
          <EmptyState
            title="No upcoming classes scheduled"
            description={en.empty.bookings}
            actionLabel="Browse Class Schedule"
            onAction={() => {
              window.location.href = '/app/classes';
            }}
          />
        )}
      </div>

      {/* Available Sessions to Book */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <div>
            <h2 className="text-lg font-bold">Available Studio Sessions</h2>
            <p className="text-xs text-text-secondary">Open slots this week</p>
          </div>
          <Link to="/app/classes" className="text-xs text-brand-primary font-medium hover:underline flex items-center gap-1">
            <span>Full Studio Calendar</span>
            <ArrowRight className="w-3.5 h-3.5" aria-hidden="true" />
          </Link>
        </div>

        {sessLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Skeleton variant="card" className="h-48" />
            <Skeleton variant="card" className="h-48" />
            <Skeleton variant="card" className="h-48" />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {sessionsData?.content?.slice(0, 3).map((session) => (
              <SessionCard
                key={session.id}
                session={session}
                onBook={(id) => {
                  window.location.href = `/app/sessions/${id}`;
                }}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
