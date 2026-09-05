import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { EmployeePrMetrics } from '../../types/prAnalytics';
import { formatCycleTime } from '../../utils/prAnalytics';

interface EmployeePrCycleTimeChartProps {
  data: EmployeePrMetrics[];
}

export function EmployeePrCycleTimeChart({ data }: EmployeePrCycleTimeChartProps) {
  const visibleData = data.slice(0, 15);

  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Employee PR Cycle Time</div>
      <div className="chart-panel__subtitle">Average cycle time for the first 15 employees alphabetically.</div>
      <div className="chart-panel__body">
        {visibleData.length > 0 ? (
          <ResponsiveContainer width="100%" height={360}>
            <BarChart data={visibleData} layout="vertical" margin={{ top: 8, right: 16, left: 24, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e6e8ee" />
              <XAxis type="number" tickLine={false} axisLine={false} />
              <YAxis type="category" dataKey="employee" tickLine={false} axisLine={false} width={140} />
              <Tooltip formatter={(value) => formatCycleTime(typeof value === 'number' ? value : null)} />
              <Bar dataKey="averageCycleTimeDays" name="Average Cycle Time" fill="#0c66e4" radius={[0, 6, 6, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
