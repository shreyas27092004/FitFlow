import React from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface TableColumn<T> {
  key: string;
  header: string;
  render?: (row: T) => React.ReactNode;
  sortable?: boolean;
  className?: string;
}

export interface TableProps<T> {
  caption: string;
  columns: TableColumn<T>[];
  data: T[];
  sortColumn?: string;
  sortDirection?: 'asc' | 'desc';
  onSort?: (columnKey: string) => void;
  isLoading?: boolean;
  emptyMessage?: string;
  className?: string;
}

export function Table<T extends { id?: string | number }>({
  caption,
  columns,
  data,
  sortColumn,
  sortDirection,
  onSort,
  isLoading,
  emptyMessage = 'No records found.',
  className,
}: TableProps<T>) {
  return (
    <div className="w-full overflow-x-auto rounded-lg border border-border bg-bg-surface">
      <table className={twMerge(clsx('w-full text-left text-sm', className))}>
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-bg-subtle text-text-secondary border-b border-border text-xs uppercase font-semibold">
          <tr>
            {columns.map((col) => {
              const isSorted = sortColumn === col.key;
              const ariaSort = !col.sortable
                ? undefined
                : isSorted
                ? sortDirection === 'asc'
                  ? 'ascending'
                  : 'descending'
                : 'none';

              return (
                <th
                  key={col.key}
                  scope="col"
                  aria-sort={ariaSort}
                  className={twMerge(clsx('px-4 py-3', col.className))}
                >
                  {col.sortable ? (
                    <button
                      type="button"
                      onClick={() => onSort?.(col.key)}
                      className="flex items-center gap-1 font-semibold uppercase tracking-wider text-text-primary hover:text-brand-primary focus:outline-none focus:ring-1 focus:ring-brand-focus-ring rounded"
                    >
                      <span>{col.header}</span>
                      <span className="flex flex-col">
                        <ChevronUp
                          className={clsx(
                            'w-3 h-3 -mb-1',
                            isSorted && sortDirection === 'asc' ? 'text-brand-primary' : 'text-text-muted opacity-40'
                          )}
                          aria-hidden="true"
                        />
                        <ChevronDown
                          className={clsx(
                            'w-3 h-3',
                            isSorted && sortDirection === 'desc' ? 'text-brand-primary' : 'text-text-muted opacity-40'
                          )}
                          aria-hidden="true"
                        />
                      </span>
                    </button>
                  ) : (
                    <span>{col.header}</span>
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {isLoading ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-text-muted">
                <span className="animate-pulse">Loading data...</span>
              </td>
            </tr>
          ) : data.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="px-4 py-8 text-center text-text-muted">
                {emptyMessage}
              </td>
            </tr>
          ) : (
            data.map((row, rowIdx) => (
              <tr key={row.id !== undefined ? String(row.id) : rowIdx} className="hover:bg-bg-subtle/50 transition-colors">
                {columns.map((col) => (
                  <td key={col.key} className={twMerge(clsx('px-4 py-3 text-text-primary', col.className))}>
                    {col.render ? col.render(row) : (row as any)[col.key]}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
