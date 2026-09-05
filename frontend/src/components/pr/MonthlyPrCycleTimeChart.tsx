import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { MonthlyPrMetrics } from '../../types/prAnalytics';

interface MonthlyPrCycleTimeChartProps {
  data: MonthlyPrMetrics[];
}

export function MonthlyPrCycleTimeChart({ data }: MonthlyPrCycleTimeChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Monthly Cycle Time</div>
      <div className="chart-panel__subtitle">Average cycle time in days for merged PRs by month.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis dataKey="monthName" tickLine={false} axisLine={false} />
              <YAxis tickLine={false} axisLine={false} />
              <Tooltip />
              <Line
                type="monotone"
                dataKey="averageCycleTimeDays"
                name="Average Cycle Time"
                stroke="#36b37e"
                strokeWidth={3}
                dot={{ r: 3 }}
                connectNulls
              />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
