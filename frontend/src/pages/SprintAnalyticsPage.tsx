import { SprintCompletionChart } from '../components/SprintCompletionChart';
import { SprintTable } from '../components/SprintTable';
import { SprintThroughputChart } from '../components/SprintThroughputChart';
import type { DashboardResponse } from '../types/analytics';

interface SprintAnalyticsPageProps {
  data: DashboardResponse;
}

export function SprintAnalyticsPage({ data }: SprintAnalyticsPageProps) {
  return (
    <div className="page-stack">
      <section className="chart-grid">
        <SprintThroughputChart data={data.sprintMetrics} />
        <SprintCompletionChart data={data.sprintMetrics} />
      </section>
      <SprintTable data={data.sprintMetrics} />
    </div>
  );
}
