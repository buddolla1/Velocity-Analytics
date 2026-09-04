import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { MonthlyVelocity } from '../types/analytics';

interface MonthlyVelocityChartProps {
  data: MonthlyVelocity[];
  title?: string;
  subtitle?: string;
}

export function MonthlyVelocityChart({
  data,
  title = 'Monthly Velocity',
  subtitle = 'Completed stories and story points grouped by month.',
}: MonthlyVelocityChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">{title}</div>
      <div className="chart-panel__subtitle">{subtitle}</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <BarChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis dataKey="monthName" tickLine={false} axisLine={false} />
              <YAxis tickLine={false} axisLine={false} />
              <Tooltip />
              <Bar dataKey="storiesCompleted" name="Stories Completed" fill="#0c66e4" radius={[6, 6, 0, 0]} />
              <Bar
                dataKey="storyPointsCompleted"
                name="Story Points"
                fill="#6e6f84"
                radius={[6, 6, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
