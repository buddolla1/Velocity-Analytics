import type { RepositoryPrMetrics } from '../../types/prAnalytics';
import { formatCycleTime, formatPercentage } from '../../utils/prAnalytics';

interface RepositoryPrMetricsTableProps {
  data: RepositoryPrMetrics[];
}

export function RepositoryPrMetricsTable({ data }: RepositoryPrMetricsTableProps) {
  return (
    <section className="table-panel">
      <div className="table-panel__header">
        <div>
          <div className="table-panel__title">Repository Metrics</div>
          <div className="table-panel__subtitle">Merged PRs, cycle time, and Jira match rate by repository.</div>
        </div>
      </div>

      <div className="table-panel__body table-panel__body--scroll">
        <table>
          <thead>
            <tr>
              <th>Repository</th>
              <th>Merged PRs</th>
              <th>Average Cycle Time</th>
              <th>Jira Match %</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row) => (
              <tr key={row.repository}>
                <td>{row.repository}</td>
                <td>{row.prsMerged}</td>
                <td>{formatCycleTime(row.averageCycleTimeDays)}</td>
                <td>{formatPercentage(row.jiraMatchPercentage)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
