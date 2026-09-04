import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { SprintMetrics } from '../types/analytics';

interface SprintCompletionChartProps {
  data: SprintMetrics[];
}

export function SprintCompletionChart({ data }: SprintCompletionChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Sprint Completion Percentage</div>
      <div className="chart-panel__subtitle">Completed stories divided by total stories in the sprint.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis dataKey="sprint" tickLine={false} axisLine={false} interval={0} />
              <YAxis tickLine={false} axisLine={false} domain={[0, 100]} tickFormatter={(value) => `${value}%`} />
              <Tooltip formatter={(value: number | string) => `${Number(value).toFixed(1)}%`} />
              <Bar dataKey="completionPercentage" name="Completion %" fill="#0c66e4" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
