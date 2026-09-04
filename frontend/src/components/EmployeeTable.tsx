import type { EmployeeMetrics } from '../types/analytics';
import { formatCycleTime } from '../utils/analytics';

interface EmployeeTableProps {
  data: EmployeeMetrics[];
}

export function EmployeeTable({ data }: EmployeeTableProps) {
  return (
    <section className="table-panel">
      <div className="table-panel__header">
        <div>
          <div className="table-panel__title">Employee Analytics</div>
          <div className="table-panel__subtitle">Completed work grouped by assignee.</div>
        </div>
      </div>
      <div className="table-panel__body table-panel__body--scroll">
        {data.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>Employee</th>
                <th>Stories Completed</th>
                <th>Story Points Completed</th>
                <th>Defects Completed</th>
                <th>Sprint Count</th>
                <th>Average Cycle Time</th>
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.employee}>
                  <td>{row.employee}</td>
                  <td>{row.storiesCompleted}</td>
                  <td>{row.storyPointsCompleted.toFixed(1)}</td>
                  <td>{row.defectsCompleted}</td>
                  <td>{row.sprintCount}</td>
                  <td>{formatCycleTime(row.averageCycleTimeDays)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : (
          <div className="empty-table">No data available for the selected filters.</div>
        )}
      </div>
    </section>
  );
}
