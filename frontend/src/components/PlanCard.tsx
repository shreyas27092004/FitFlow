import React from 'react';
import { Check } from 'lucide-react';
import { Card } from './Card';
import { Badge } from './Badge';
import { Button } from './Button';
import { formatPaise } from '../lib/money';
import { clsx } from 'clsx';

export interface PlanData {
  id: string;
  name: string;
  description?: string;
  pricePaise: number;
  sessionsPerMonth: number;
  features?: string[];
}

export interface PlanCardProps {
  plan: PlanData;
  isCurrent?: boolean;
  isPopular?: boolean;
  actionLabel?: string;
  onSelect?: (planId: string) => void;
  isLoading?: boolean;
}

export const PlanCard: React.FC<PlanCardProps> = ({
  plan,
  isCurrent = false,
  isPopular = false,
  actionLabel = 'Select Plan',
  onSelect,
  isLoading = false,
}) => {
  return (
    <Card
      className={clsx(
        'relative flex flex-col justify-between h-full border transition-all',
        isCurrent
          ? 'border-brand-primary ring-2 ring-brand-primary/20 bg-brand-primary/5'
          : isPopular
          ? 'border-brand-primary/80 shadow-md'
          : 'border-border'
      )}
    >
      {isCurrent && (
        <div className="absolute -top-3 right-4">
          <Badge status="ACTIVE">Current Plan</Badge>
        </div>
      )}

      {isPopular && !isCurrent && (
        <div className="absolute -top-3 right-4">
          <Badge variant="info">Most Popular</Badge>
        </div>
      )}

      <div>
        <h3 className="text-xl font-bold text-text-primary mb-1">{plan.name}</h3>
        {plan.description && (
          <p className="text-xs text-text-muted mb-4">{plan.description}</p>
        )}

        <div className="flex items-baseline gap-1 my-4">
          <span className="text-3xl font-extrabold text-text-primary">
            {formatPaise(plan.pricePaise)}
          </span>
          <span className="text-xs text-text-secondary">/ month</span>
        </div>

        <div className="p-3 bg-bg-subtle rounded-md mb-6">
          <p className="text-sm font-semibold text-text-primary">
            {plan.sessionsPerMonth} sessions included
          </p>
          <p className="text-xs text-text-muted mt-0.5">Renews monthly on purchase date</p>
        </div>

        {plan.features && plan.features.length > 0 && (
          <ul className="flex flex-col gap-2.5 text-xs text-text-secondary mb-6">
            {plan.features.map((feat, i) => (
              <li key={i} className="flex items-start gap-2">
                <Check className="w-4 h-4 text-brand-primary shrink-0 mt-0.5" aria-hidden="true" />
                <span>{feat}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="pt-4 border-t border-border">
        <Button
          type="button"
          variant={isCurrent ? 'secondary' : 'primary'}
          onClick={() => onSelect?.(plan.id)}
          disabled={isCurrent || isLoading}
          isLoading={isLoading}
          className="w-full"
        >
          {isCurrent ? 'Your Current Plan' : actionLabel}
        </Button>
      </div>
    </Card>
  );
};
