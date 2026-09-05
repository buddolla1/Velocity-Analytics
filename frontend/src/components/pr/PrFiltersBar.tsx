import { FilterX } from 'lucide-react';
import type { PrAnalyticsFilters } from '../../types/prAnalytics';

interface FilterOptions {
  projects: string[];
  repositories: string[];
  months: string[];
  employees: string[];
  sprints: string[];
  cycleStartSources: string[];
}

interface PrFiltersBarProps {
  filters: PrAnalyticsFilters;
  options: FilterOptions;
  loading?: boolean;
  onChange: (next: PrAnalyticsFilters) => void;
  onClear: () => void;
}

const fieldClassName = 'filters-bar__field';

export function PrFiltersBar({ filters, options, loading = false, onChange, onClear }: PrFiltersBarProps) {
  return (
    <section className="filters-bar filters-bar--pr">
      <label className={fieldClassName}>
        <span>Project</span>
        <select
          value={filters.project}
          onChange={(event) => onChange({ ...filters, project: event.target.value })}
          disabled={loading}
        >
          <option value="">All projects</option>
          {options.projects.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Repository</span>
        <select
          value={filters.repository}
          onChange={(event) => onChange({ ...filters, repository: event.target.value })}
          disabled={loading}
        >
          <option value="">All repositories</option>
          {options.repositories.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Month</span>
        <select
          value={filters.month}
          onChange={(event) => onChange({ ...filters, month: event.target.value })}
          disabled={loading}
        >
          <option value="">All months</option>
          {options.months.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Employee</span>
        <select
          value={filters.employee}
          onChange={(event) => onChange({ ...filters, employee: event.target.value })}
          disabled={loading}
        >
          <option value="">All employees</option>
          {options.employees.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Jira Match</span>
        <select
          value={filters.jiraMatch}
          onChange={(event) =>
            onChange({ ...filters, jiraMatch: event.target.value as PrAnalyticsFilters['jiraMatch'] })
          }
          disabled={loading}
        >
          <option value="all">All</option>
          <option value="matched">Matched</option>
          <option value="unmatched">Not Matched</option>
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Sprint</span>
        <select
          value={filters.sprint}
          onChange={(event) => onChange({ ...filters, sprint: event.target.value })}
          disabled={loading}
        >
          <option value="">All sprints</option>
          {options.sprints.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Cycle Start Source</span>
        <select
          value={filters.cycleStartSource}
          onChange={(event) => onChange({ ...filters, cycleStartSource: event.target.value })}
          disabled={loading}
        >
          <option value="">All sources</option>
          {options.cycleStartSources.map((value) => (
            <option key={value} value={value}>
              {value}
            </option>
          ))}
        </select>
      </label>

      <button type="button" className="filters-bar__clear" onClick={onClear} disabled={loading}>
        <FilterX size={16} />
        Clear Filters
      </button>
    </section>
  );
}
