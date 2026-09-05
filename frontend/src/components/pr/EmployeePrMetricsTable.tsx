import type { EmployeePrMetrics } from '../../types/prAnalytics';
import { formatCycleTime, formatPercentage } from '../../utils/prAnalytics';

interface EmployeePrMetricsTableProps {
  data: EmployeePrMetrics[];
}

export function EmployeePrMetricsTable({ data }: EmployeePrMetricsTableProps) {
  return (
    <section className="table-panel">
      <div className="table-panel__header">
        <div>
          <div className="table-panel__title">Employee PR Analytics</div>
          <div className="table-panel__subtitle">Merged PRs, Jira matches, cycle time, and repository spread.</div>
        </div>
      </div>

      <div className="table-panel__body table-panel__body--scroll">
        <table>
          <thead>
            <tr>
              <th>Employee</th>
              <th>PRs Merged</th>
              <th>Jira Matched PRs</th>
              <th>Jira Match %</th>
              <th>Average Cycle Time</th>
              <th>Median Cycle Time</th>
              <th>Repositories</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row) => (
              <tr key={row.employee}>
                <td>{row.employee}</td>
                <td>{row.prsMerged}</td>
                <td>{row.jiraMatchedPrs}</td>
                <td>{formatPercentage(row.prsMerged === 0 ? 0 : (row.jiraMatchedPrs * 100) / row.prsMerged)}</td>
                <td>{formatCycleTime(row.averageCycleTimeDays)}</td>
                <td>{formatCycleTime(row.medianCycleTimeDays)}</td>
                <td>{row.repositories}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
