import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { EmployeeMetrics } from '../types/analytics';

interface EmployeeDeliveryChartProps {
  data: EmployeeMetrics[];
}

export function EmployeeDeliveryChart({ data }: EmployeeDeliveryChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Employee Delivery</div>
      <div className="chart-panel__subtitle">Completed stories, story points, and defects grouped by assignee.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={340}>
            <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, left: 16, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis type="number" tickLine={false} axisLine={false} />
              <YAxis type="category" dataKey="employee" width={120} tickLine={false} axisLine={false} />
              <Tooltip />
              <Bar dataKey="storiesCompleted" name="Stories Completed" fill="#0c66e4" radius={[0, 6, 6, 0]} />
              <Bar
                dataKey="storyPointsCompleted"
                name="Story Points"
                fill="#36b37e"
                radius={[0, 6, 6, 0]}
              />
              <Bar dataKey="defectsCompleted" name="Defects" fill="#d04437" radius={[0, 6, 6, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
