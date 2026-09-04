import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip, Legend } from 'recharts';
import type { IssueTypeMetrics } from '../types/analytics';

interface IssueTypeChartProps {
  data: IssueTypeMetrics[];
}

const COLORS = ['#0c66e4', '#36b37e', '#ffab00', '#d04437', '#6554c0', '#6e6f84', '#00b8d9'];

export function IssueTypeChart({ data }: IssueTypeChartProps) {
  return (
    <div className="chart-panel">
      <div className="chart-panel__title">Issue Type Distribution</div>
      <div className="chart-panel__subtitle">Count of issues grouped by Jira issue type.</div>
      <div className="chart-panel__body">
        {data.length > 0 ? (
          <ResponsiveContainer width="100%" height={320}>
            <PieChart>
              <Pie
                data={data}
                dataKey="count"
                nameKey="issueType"
                innerRadius={70}
                outerRadius={110}
                paddingAngle={2}
              >
                {data.map((entry, index) => (
                  <Cell key={entry.issueType} fill={COLORS[index % COLORS.length]} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        ) : (
          <div className="chart-empty">No data available for the selected filters.</div>
        )}
      </div>
    </div>
  );
}
