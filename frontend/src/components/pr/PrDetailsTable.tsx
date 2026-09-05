import type { PrAnalyticsRecord } from '../../types/prAnalytics';
import { formatCycleTime, formatDate, formatDateOnly, formatNumber } from '../../utils/prAnalytics';

interface PrDetailsTableProps {
  data: PrAnalyticsRecord[];
}

export function PrDetailsTable({ data }: PrDetailsTableProps) {
  return (
    <section className="table-panel">
      <div className="table-panel__header">
        <div>
          <div className="table-panel__title">Detailed PR Traceability</div>
          <div className="table-panel__subtitle">Raw PR-to-Jira linkage and cycle timestamps for every record.</div>
        </div>
      </div>

      <div className="table-panel__body table-panel__body--scroll table-panel__body--tall">
        <table className="pr-details-table">
          <thead>
            <tr>
              <th>PR ID</th>
              <th>Repository</th>
              <th>Project</th>
              <th>Author</th>
              <th>PR Title</th>
              <th>Jira Key</th>
              <th>Jira Match</th>
              <th>Jira Assignee</th>
              <th>Sprint</th>
              <th>Story Points</th>
              <th>PR Created At</th>
              <th>First Commit At</th>
              <th>Jira In Progress At</th>
              <th>Cycle Start</th>
              <th>Cycle Start Source</th>
              <th>PR Merged At</th>
              <th>Cycle Time Days</th>
            </tr>
          </thead>
          <tbody>
            {data.map((row, index) => (
              <tr key={`${row.prId}-${index}`}>
                <td>{row.prId || '—'}</td>
                <td>{row.repositoryName || '—'}</td>
                <td>{row.projectName || '—'}</td>
                <td>{row.authorName || '—'}</td>
                <td>{row.prTitle || '—'}</td>
                <td>{row.jiraKey || '—'}</td>
                <td>{row.jiraMatch ? 'MATCHED' : 'NO JIRA MATCH'}</td>
                <td>{row.jiraAssignee || '—'}</td>
                <td>{row.jiraSprint || '—'}</td>
                <td>{typeof row.jiraStoryPoints === 'number' ? formatNumber(row.jiraStoryPoints, 1) : '—'}</td>
                <td>{formatDate(row.prCreatedAt)}</td>
                <td>{formatDate(row.firstCommitAt)}</td>
                <td>{formatDate(row.jiraInProgressAt)}</td>
                <td>{formatDateOnly(row.cycleStart)}</td>
                <td>{row.cycleStartSource || '—'}</td>
                <td>{formatDate(row.prMergedAt)}</td>
                <td>{formatCycleTime(row.cycleTimeDays)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
