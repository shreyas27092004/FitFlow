import React from 'react';
import * as RadixTabs from '@radix-ui/react-tabs';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

export interface TabItem {
  id: string;
  label: React.ReactNode;
  icon?: React.ReactNode;
  badge?: React.ReactNode;
  content: React.ReactNode;
}

export interface TabsProps {
  tabs: TabItem[];
  activeTab?: string;
  defaultValue?: string;
  onTabChange?: (tabId: string) => void;
  ariaLabel?: string;
  className?: string;
}

export const Tabs: React.FC<TabsProps> = ({
  tabs,
  activeTab,
  defaultValue,
  onTabChange,
  ariaLabel = 'Content tabs',
  className,
}) => {
  const fallbackDefault = defaultValue || (tabs.length > 0 ? tabs[0].id : undefined);

  return (
    <RadixTabs.Root
      value={activeTab}
      defaultValue={fallbackDefault}
      onValueChange={onTabChange}
      className={twMerge(clsx('w-full flex flex-col', className))}
    >
      <RadixTabs.List
        aria-label={ariaLabel}
        className="flex border-b border-border gap-6 overflow-x-auto"
      >
        {tabs.map((tab) => (
          <RadixTabs.Trigger
            key={tab.id}
            value={tab.id}
            className={clsx(
              'flex items-center gap-2 py-3 px-1 text-sm font-medium border-b-2 -mb-px transition-colors whitespace-nowrap outline-none',
              'text-text-secondary hover:text-text-primary focus-visible:ring-2 focus-visible:ring-brand-focus-ring rounded-t',
              'data-[state=active]:border-brand-primary data-[state=active]:text-brand-primary font-semibold',
              'data-[state=inactive]:border-transparent'
            )}
          >
            {tab.icon && <span aria-hidden="true">{tab.icon}</span>}
            <span>{tab.label}</span>
            {tab.badge && <span>{tab.badge}</span>}
          </RadixTabs.Trigger>
        ))}
      </RadixTabs.List>

      {tabs.map((tab) => (
        <RadixTabs.Content
          key={tab.id}
          value={tab.id}
          className="pt-4 outline-none focus-visible:ring-2 focus-visible:ring-brand-focus-ring rounded-b"
        >
          {tab.content}
        </RadixTabs.Content>
      ))}
    </RadixTabs.Root>
  );
};
