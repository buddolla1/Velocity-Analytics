import { BarChart3, CheckCircle2, Clock3, Bug, Users } from 'lucide-react';
import type { DashboardResponse } from '../types/analytics';
import { EmployeeDeliveryChart } from '../components/EmployeeDeliveryChart';
import { IssueTypeChart } from '../components/IssueTypeChart';
import { KpiCard } from '../components/KpiCard';
import { MonthlyStoryPointsChart } from '../components/MonthlyStoryPointsChart';
import { MonthlyVelocityChart } from '../components/MonthlyVelocityChart';
import { SprintTable } from '../components/SprintTable';
import { SprintThroughputChart } from '../components/SprintThroughputChart';

interface OverviewPageProps {
  data: DashboardResponse;
}

export function OverviewPage({ data }: OverviewPageProps) {
  return (
    <div className="page-stack">
      <section className="kpi-grid">
        <KpiCard title="Stories Completed" value={data.summary.storiesCompleted} icon={<CheckCircle2 size={18} />} />
        <KpiCard
          title="Story Points Completed"
          value={data.summary.storyPointsCompleted}
          icon={<BarChart3 size={18} />}
        />
        <KpiCard
          title="Average Cycle Time"
          value={data.summary.averageCycleTimeDays === null ? '—' : data.summary.averageCycleTimeDays.toFixed(1)}
          suffix={data.summary.averageCycleTimeDays === null ? '' : ' days'}
          icon={<Clock3 size={18} />}
        />
        <KpiCard title="Defects Closed" value={data.summary.defectsClosed} icon={<Bug size={18} />} />
        <KpiCard title="Active Contributors" value={data.summary.activeContributors} icon={<Users size={18} />} />
      </section>

      <section className="chart-grid">
        <MonthlyVelocityChart data={data.monthlyVelocity} />
        <MonthlyStoryPointsChart data={data.monthlyVelocity} />
        <SprintThroughputChart data={data.sprintMetrics} />
        <IssueTypeChart data={data.issueTypeMetrics} />
      </section>

      <section className="chart-grid">
        <EmployeeDeliveryChart data={data.employeeMetrics} />
        <SprintTable data={data.sprintMetrics} />
      </section>
    </div>
  );
}
