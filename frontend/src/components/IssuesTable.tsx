import type { JiraIssue } from '../types/analytics';

interface IssuesTableProps {
  data: JiraIssue[];
}

function formatResolved(value: string | null | undefined): string {
  if (!value || !value.trim()) {
    return '—';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

function displayAssignee(value: string | null | undefined): string {
  return value && value.trim() ? value : 'Unassigned';
}

export function IssuesTable({ data }: IssuesTableProps) {
  return (
    <section className="table-panel">
      <div className="table-panel__header">
        <div>
          <div className="table-panel__title">Jira Records</div>
          <div className="table-panel__subtitle">Raw issue records returned by the backend.</div>
        </div>
      </div>
      <div className="table-panel__body table-panel__body--scroll table-panel__body--tall">
        {data.length > 0 ? (
          <table>
            <thead>
              <tr>
                <th>Issue Key</th>
                <th>Issue Type</th>
                <th>Project</th>
                <th>Summary</th>
                <th>Assignee</th>
                <th>Sprint</th>
                <th>Status</th>
                <th>Story Points</th>
                <th>Resolved</th>
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.issueKey}>
                  <td>{row.issueKey}</td>
                  <td>{row.issueType || '—'}</td>
                  <td>{row.projectName || row.projectKey || '—'}</td>
                  <td className="table-panel__summary">{row.summary || '—'}</td>
                  <td>{displayAssignee(row.assignee)}</td>
                  <td>{row.sprint || 'No Sprint'}</td>
                  <td>{row.status || '—'}</td>
                  <td>{typeof row.storyPoints === 'number' ? row.storyPoints.toFixed(1) : '—'}</td>
                  <td>{formatResolved(row.resolvedAt)}</td>
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
