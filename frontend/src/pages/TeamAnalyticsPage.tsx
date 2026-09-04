import { EmployeeDeliveryChart } from '../components/EmployeeDeliveryChart';
import { EmployeeTable } from '../components/EmployeeTable';
import { MonthlyVelocityChart } from '../components/MonthlyVelocityChart';
import type { DashboardResponse } from '../types/analytics';

interface TeamAnalyticsPageProps {
  data: DashboardResponse;
}

export function TeamAnalyticsPage({ data }: TeamAnalyticsPageProps) {
  return (
    <div className="page-stack">
      <section className="chart-grid">
        <EmployeeDeliveryChart data={data.employeeMetrics} />
        <MonthlyVelocityChart
          data={data.monthlyVelocity}
          title="Employee Monthly Velocity"
          subtitle="Completed stories and story points grouped by month."
        />
      </section>
      <EmployeeTable data={data.employeeMetrics} />
    </div>
  );
}
