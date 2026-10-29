import React from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { Badge } from '../../components/Badge';
import { SessionCard } from '../../components/SessionCard';
import { Skeleton } from '../../components/Skeleton';
import { EmptyState } from '../../components/EmptyState';
import { useGetClassByIdQuery, useGetSessionsQuery } from '../../api/classesApi';
import { ArrowLeft, Clock, Users, Flame } from 'lucide-react';

export const ClassDetailsPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const { data: cls, isLoading: clsLoading, error: clsError } = useGetClassByIdQuery(id || '');
  const { data: sessionsData, isLoading: sessLoading } = useGetSessionsQuery(
    id ? { classId: id } : undefined
  );

  if (clsLoading) {
    return (
      <div className="space-y-6">
        <Skeleton variant="button" className="w-24" />
        <Skeleton variant="card" className="h-64" />
      </div>
    );
  }

  if (clsError || !cls) {
    return (
      <div className="text-center py-12">
        <h2 className="text-xl font-bold mb-2">Class Not Found</h2>
        <p className="text-sm text-text-muted mb-4">The class you are looking for does not exist or has been removed.</p>
        <Link to="/app/classes">
          <Button variant="outline" size="sm">
            Back to Classes
          </Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        to="/app/classes"
        className="inline-flex items-center gap-1.5 text-xs text-text-muted hover:text-text-primary font-medium"
      >
        <ArrowLeft className="w-3.5 h-3.5" aria-hidden="true" />
        <span>Back to Class Catalog</span>
      </Link>

      {/* Class Overview Header */}
      <Card className="p-6 md:p-8 border border-border">
        <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold uppercase tracking-wider text-brand-primary">
              {cls.category}
            </span>
            <Badge variant={cls.intensity === 'HIGH' ? 'danger' : cls.intensity === 'MEDIUM' ? 'warning' : 'info'}>
              {cls.intensity} Intensity
            </Badge>
          </div>
          <span className="text-xs font-medium text-text-muted">
            Default Capacity: {cls.capacityDefault} members
          </span>
        </div>

        <h1 className="text-2xl md:text-4xl font-extrabold text-text-primary mb-4">
          {cls.title}
        </h1>

        {/* Note: render description strictly as plain text (no dangerouslySetInnerHTML) */}
        <p className="text-sm md:text-base text-text-secondary leading-relaxed mb-6 whitespace-pre-line">
          {cls.description}
        </p>

        <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 pt-4 border-t border-border text-xs text-text-secondary">
          <div className="flex items-center gap-2">
            <Clock className="w-4 h-4 text-brand-primary" aria-hidden="true" />
            <span>Duration: <strong>{cls.durationMinutes} mins</strong></span>
          </div>
          <div className="flex items-center gap-2">
            <Flame className="w-4 h-4 text-brand-primary" aria-hidden="true" />
            <span>Pace: <strong>{cls.intensity}</strong></span>
          </div>
          <div className="flex items-center gap-2">
            <Users className="w-4 h-4 text-brand-primary" aria-hidden="true" />
            <span>Studio Room: <strong>Main Studio</strong></span>
          </div>
        </div>
      </Card>

      {/* Upcoming Scheduled Instances */}
      <div>
        <div className="mb-4">
          <h2 className="text-xl font-bold">Upcoming Sessions for this Class</h2>
          <p className="text-xs text-text-secondary">Pick a convenient time and reserve your studio spot</p>
        </div>

        {sessLoading ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Skeleton variant="card" className="h-48" />
            <Skeleton variant="card" className="h-48" />
          </div>
        ) : sessionsData?.content && sessionsData.content.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {sessionsData.content.map((session) => (
              <SessionCard
                key={session.id}
                session={session}
                onBook={(sessionId) => {
                  navigate(`/app/sessions/${sessionId}`);
                }}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            title="No scheduled sessions right now"
            description="There are currently no upcoming calendar sessions scheduled for this class. Please check back soon or browse other styles."
            actionLabel="Browse Other Classes"
            onAction={() => {
              window.location.href = '/app/classes';
            }}
          />
        )}
      </div>
    </div>
  );
};
