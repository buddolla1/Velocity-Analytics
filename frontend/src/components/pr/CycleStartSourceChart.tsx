import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import type { CycleStartSourceMetric } from '../../types/prAnalytics';
import { formatPercentage } from '../../utils/prAnalytics';

interface CycleStartSourceChartProps {
  data: CycleStartSourceMetric[];
}

const COLORS = ['#0c66e4', '#36b37e', '#6554c0'];

export function CycleStartSourceChart({ data }: CycleStartSourceChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Cycle Start Source</div>
      <div className="chart-panel__subtitle">Where the earliest cycle-start timestamp came from.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <PieChart>
              <Tooltip formatter={(value, name) => [`${value}`, name]} />
              <Pie
                data={data}
                dataKey="count"
                nameKey="source"
                innerRadius={78}
                outerRadius={112}
                paddingAngle={2}
              >
                {data.map((entry, index) => (
                  <Cell key={entry.source} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>

      <div className="chart-legend">
        {data.map((entry, index) => (
          <div key={entry.source} className="chart-legend__item">
            <span className="chart-legend__swatch" style={{ backgroundColor: COLORS[index % COLORS.length] }} />
            <span>{entry.source}</span>
            <strong>{formatPercentage(entry.percentage)}</strong>
          </div>
        ))}
      </div>
    </div>
  );
}
