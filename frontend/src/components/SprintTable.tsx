import type { SprintMetrics } from '../types/analytics';

interface SprintTableProps {
  data: SprintMetrics[];
}

export function SprintTable({ data }: SprintTableProps) {
  return (
    <section className="table-panel">
      <div className="table-panel__header">
        <div>
          <div className="table-panel__title">Sprint Summary</div>
          <div className="table-panel__subtitle">Completion percentage, throughput, defects, and contributors.</div>
        </div>
      </div>
      <div className="table-panel__body table-panel__body--scroll">
        {data.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>Sprint</th>
                <th>Total Stories</th>
                <th>Completed Stories</th>
                <th>Story Points</th>
                <th>Completion %</th>
                <th>Defects</th>
                <th>Contributors</th>
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.sprint}>
                  <td>{row.sprint}</td>
                  <td>{row.totalStories}</td>
                  <td>{row.completedStories}</td>
                  <td>{row.storyPoints.toFixed(1)}</td>
                  <td>{row.completionPercentage.toFixed(1)}%</td>
                  <td>{row.defects}</td>
                  <td>{row.contributors}</td>
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
