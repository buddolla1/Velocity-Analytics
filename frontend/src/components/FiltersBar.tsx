import { FilterX } from 'lucide-react';
import type { AnalyticsFilters } from '../types/analytics';

interface FilterOptions {
  projects: string[];
  months: string[];
  sprints: string[];
  assignees: string[];
  issueTypes: string[];
}

interface FiltersBarProps {
  filters: AnalyticsFilters;
  options: FilterOptions;
  loading?: boolean;
  onChange: (next: AnalyticsFilters) => void;
  onClear: () => void;
}

const fieldClassName = 'filters-bar__field';

export function FiltersBar({ filters, options, loading = false, onChange, onClear }: FiltersBarProps) {
  return (
    <section className="filters-bar">
      <label className={fieldClassName}>
        <span>Project</span>
        <select
          value={filters.project}
          onChange={(event) => onChange({ ...filters, project: event.target.value })}
          disabled={loading}
        >
          <option value="">All projects</option>
          {options.projects.map((project) => (
            <option key={project} value={project}>
              {project}
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
          {options.months.map((month) => (
            <option key={month} value={month}>
              {month}
            </option>
          ))}
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
          {options.sprints.map((sprint) => (
            <option key={sprint} value={sprint}>
              {sprint}
            </option>
          ))}
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Assignee</span>
        <select
          value={filters.assignee}
          onChange={(event) => onChange({ ...filters, assignee: event.target.value })}
          disabled={loading}
        >
          <option value="">All assignees</option>
          {options.assignees.map((assignee) => (
            <option key={assignee} value={assignee}>
              {assignee}
            </option>
          ))}
        </select>
      </label>

      <label className={fieldClassName}>
        <span>Issue Type</span>
        <select
          value={filters.issueType}
          onChange={(event) => onChange({ ...filters, issueType: event.target.value })}
          disabled={loading}
        >
          <option value="">All issue types</option>
          {options.issueTypes.map((issueType) => (
            <option key={issueType} value={issueType}>
              {issueType}
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
