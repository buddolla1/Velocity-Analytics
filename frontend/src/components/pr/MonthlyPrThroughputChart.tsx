import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { MonthlyPrMetrics } from '../../types/prAnalytics';

interface MonthlyPrThroughputChartProps {
  data: MonthlyPrMetrics[];
}

export function MonthlyPrThroughputChart({ data }: MonthlyPrThroughputChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Monthly PR Throughput</div>
      <div className="chart-panel__subtitle">PRs merged by the month of merge.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <BarChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis dataKey="monthName" tickLine={false} axisLine={false} />
              <YAxis tickLine={false} axisLine={false} />
              <Tooltip />
              <Bar dataKey="prsMerged" name="PRs Merged" fill="#0c66e4" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
