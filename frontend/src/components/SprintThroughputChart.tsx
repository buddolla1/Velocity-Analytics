import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { SprintMetrics } from '../types/analytics';

interface SprintThroughputChartProps {
  data: SprintMetrics[];
}

export function SprintThroughputChart({ data }: SprintThroughputChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Sprint Throughput</div>
      <div className="chart-panel__subtitle">Total stories, completed stories, points, and defects per sprint.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <BarChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis dataKey="sprint" tickLine={false} axisLine={false} interval={0} />
              <YAxis tickLine={false} axisLine={false} />
              <Tooltip />
              <Bar dataKey="totalStories" name="Total Stories" fill="#6e6f84" radius={[6, 6, 0, 0]} />
              <Bar dataKey="completedStories" name="Completed Stories" fill="#0c66e4" radius={[6, 6, 0, 0]} />
              <Bar dataKey="storyPoints" name="Story Points" fill="#36b37e" radius={[6, 6, 0, 0]} />
              <Bar dataKey="defects" name="Defects" fill="#d04437" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
