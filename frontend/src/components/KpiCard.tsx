import type { ReactNode } from 'react';
import { formatNumber } from '../utils/analytics';

interface KpiCardProps {
  title: string;
  value: number | string | null;
  icon: ReactNode;
  suffix?: string;
}

export function KpiCard({ title, value, icon, suffix }: KpiCardProps) {
  const displayValue =
    typeof value === 'number' ? formatNumber(value) : value === null || value === '' ? '—' : value;

  return (
    <article className="kpi-card">
      <div className="kpi-card__header">
        <span className="kpi-card__title">{title}</span>
        <span className="kpi-card__icon" aria-hidden="true">
          {icon}
        </span>
      </div>
      <div className="kpi-card__value">
        {displayValue}
        {suffix ? <span className="kpi-card__suffix">{suffix}</span> : null}
      </div>
    </article>
  );
}
