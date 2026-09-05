import type { WeeklyCycleTimeReport } from '../../types/prAnalytics';
import { formatCycleTime } from '../../utils/prAnalytics';

interface WeeklyCycleTimeTableProps {
  data: WeeklyCycleTimeReport;
}

export function WeeklyCycleTimeTable({ data }: WeeklyCycleTimeTableProps) {
  return (
    <section className="table-panel">
      <div className="table-panel__header">
        <div>
          <div className="table-panel__title">Weekly Cycle Time</div>
          <div className="table-panel__subtitle">Fixed reporting weeks from 2026-01-01 through 2026-04-01.</div>
        </div>
      </div>

      <div className="table-panel__body table-panel__body--scroll">
        <table className="weekly-cycle-table">
          <thead>
            <tr>
              <th>Week #</th>
              <th>Week Start</th>
              <th>Week End</th>
              {data.employees.map((employee) => (
                <th key={employee}>{employee}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data.weeks.map((week) => (
              <tr key={week.weekNumber}>
                <td>{week.weekNumber}</td>
                <td>{week.weekStart}</td>
                <td>{week.weekEnd}</td>
                {data.employees.map((employee) => (
                  <td key={employee}>{formatCycleTime(week.averagesByEmployee[employee])}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
