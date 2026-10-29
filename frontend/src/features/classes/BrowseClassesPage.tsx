import React from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Card } from '../../components/Card';
import { Button } from '../../components/Button';
import { TextField } from '../../components/TextField';
import { Tag } from '../../components/Tag';
import { Badge } from '../../components/Badge';
import { Skeleton } from '../../components/Skeleton';
import { EmptyState } from '../../components/EmptyState';
import { Pagination } from '../../components/Pagination';
import { useGetClassesQuery, useGetClassesMetadataQuery } from '../../api/classesApi';
import { Search, Clock, Users } from 'lucide-react';
import en from '../../i18n/en.json';

export const BrowseClassesPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  const search = searchParams.get('search') || '';
  const category = searchParams.get('category') || '';
  const page = parseInt(searchParams.get('page') || '1', 10);
  const pageSize = 6;

  const { data: metadata } = useGetClassesMetadataQuery();
  const { data, isLoading } = useGetClassesQuery({
    search: search || undefined,
    category: category || undefined,
    page: page - 1,
    size: pageSize,
  });

  const updateParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    next.set('page', '1'); // Reset to page 1 on filter change
    setSearchParams(next);
  };

  const handlePageChange = (newPage: number) => {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(newPage));
    setSearchParams(next);
  };

  const totalPages = Math.ceil((data?.totalElements || 0) / pageSize);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl md:text-3xl font-extrabold tracking-tight">Class Catalog</h1>
        <p className="text-sm text-text-secondary mt-1">
          Explore workout styles and upcoming studio sessions designed for all fitness levels.
        </p>
      </div>

      {/* Filter and Search Bar */}
      <div className="flex flex-col md:flex-row gap-4 items-stretch md:items-center justify-between bg-bg-surface p-4 rounded-xl border border-border">
        <div className="w-full md:w-80">
          <TextField
            label="Search classes"
            placeholder="Search by title or style..."
            value={search}
            onChange={(e) => updateParam('search', e.target.value)}
            leftIcon={<Search className="w-4 h-4" />}
          />
        </div>

        <div className="flex items-center gap-2 overflow-x-auto pb-1 md:pb-0">
          <Tag
            label="All Categories"
            selected={!category}
            onClick={() => updateParam('category', '')}
          />
          {metadata?.categories?.map((cat) => (
            <Tag
              key={cat}
              label={cat}
              selected={category === cat}
              onClick={() => updateParam('category', cat === category ? '' : cat)}
            />
          ))}
        </div>
      </div>

      {/* Class Cards Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} variant="card" className="h-64" />
          ))}
        </div>
      ) : data?.content && data.content.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {data.content.map((cls) => (
            <Card
              key={cls.id}
              className="flex flex-col justify-between h-full border border-border hover:border-brand-primary/60 transition-all hover:shadow-md"
            >
              <div>
                <div className="flex items-start justify-between gap-2 mb-2">
                  <span className="text-xs font-bold uppercase tracking-wider text-brand-primary">
                    {cls.category}
                  </span>
                  <Badge variant={cls.intensity === 'HIGH' ? 'danger' : cls.intensity === 'MEDIUM' ? 'warning' : 'info'}>
                    {cls.intensity}
                  </Badge>
                </div>

                <h3 className="text-lg font-bold text-text-primary mb-2 line-clamp-1">
                  {cls.title}
                </h3>

                <p className="text-xs text-text-secondary line-clamp-3 mb-4 leading-relaxed">
                  {cls.description}
                </p>

                <div className="flex items-center gap-4 text-xs text-text-muted mb-4">
                  <div className="flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 text-text-muted" aria-hidden="true" />
                    <span>{cls.durationMinutes} mins</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <Users className="w-3.5 h-3.5 text-text-muted" aria-hidden="true" />
                    <span>Capacity: {cls.capacityDefault}</span>
                  </div>
                </div>
              </div>

              <div className="pt-4 border-t border-border">
                <Link to={`/app/classes/${cls.id}`}>
                  <Button variant="outline" size="sm" className="w-full">
                    View Schedule & Details
                  </Button>
                </Link>
              </div>
            </Card>
          ))}
        </div>
      ) : (
        <EmptyState
          title="No classes match your filters"
          description={en.empty.classes}
          actionLabel="Clear Filters"
          onAction={() => {
            setSearchParams(new URLSearchParams());
          }}
        />
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="pt-4">
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            onPageChange={handlePageChange}
          />
        </div>
      )}
    </div>
  );
};
