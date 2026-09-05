import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { RepositoryPrMetrics } from '../../types/prAnalytics';
import { formatCycleTime, formatPercentage } from '../../utils/prAnalytics';

interface RepositoryAnalyticsChartProps {
  data: RepositoryPrMetrics[];
}

export function RepositoryAnalyticsChart({ data }: RepositoryAnalyticsChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Repository Analytics</div>
      <div className="chart-panel__subtitle">Merged PR count and average cycle time by repository.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={340}>
            <ComposedChart data={data} margin={{ top: 8, right: 24, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis dataKey="repository" tickLine={false} axisLine={false} interval={0} />
              <YAxis yAxisId="left" tickLine={false} axisLine={false} />
              <YAxis yAxisId="right" orientation="right" tickLine={false} axisLine={false} />
              <Tooltip
                formatter={(value, name) => {
                  if (name === 'Average Cycle Time') {
                    return [formatCycleTime(typeof value === 'number' ? value : null), name];
                  }
                  if (name === 'Jira Match %') {
                    return [formatPercentage(typeof value === 'number' ? value : 0), name];
                  }
                  return [value, name];
                }}
              />
              <Bar yAxisId="left" dataKey="prsMerged" name="PRs Merged" fill="#0c66e4" radius={[6, 6, 0, 0]} />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="averageCycleTimeDays"
                name="Average Cycle Time"
                stroke="#36b37e"
                strokeWidth={3}
                dot={{ r: 3 }}
                connectNulls
              />
            </ComposedChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
