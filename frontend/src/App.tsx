import { useMemo, useState, type ChangeEvent, type ReactNode } from 'react';
import {
  Activity,
  ArrowUpRight,
  CheckCircle2,
  FileSpreadsheet,
  LayoutDashboard,
  RefreshCw,
  Rocket,
  ShieldAlert,
  Upload,
  Users,
} from 'lucide-react';
import { FiltersBar } from './components/FiltersBar';
import { OverviewPage } from './pages/OverviewPage';
import { SprintAnalyticsPage } from './pages/SprintAnalyticsPage';
import { TeamAnalyticsPage } from './pages/TeamAnalyticsPage';
import { IssuesPage } from './pages/IssuesPage';
import { uploadJiraFile } from './services/jiraApi';
import type { AnalyticsFilters, DashboardResponse, JiraIssue } from './types/analytics';
import { buildAnalytics, filterIssues, formatAssignee, normalizeText } from './utils/analytics';

type PageKey = 'overview' | 'sprints' | 'team' | 'issues';

const EMPTY_FILTERS: AnalyticsFilters = {
  project: '',
  month: '',
  sprint: '',
  assignee: '',
  issueType: '',
};

const EMPTY_RESPONSE: DashboardResponse = {
  summary: {
    totalIssues: 0,
    storiesCompleted: 0,
    storyPointsCompleted: 0,
    defectsClosed: 0,
    activeContributors: 0,
    averageCycleTimeDays: null,
  },
  monthlyVelocity: [],
  sprintMetrics: [],
  employeeMetrics: [],
  issueTypeMetrics: [],
  issues: [],
};

const NAVIGATION: Array<{ key: PageKey; label: string; icon: ReactNode }> = [
  { key: 'overview', label: 'Overview', icon: <LayoutDashboard size={16} /> },
  { key: 'sprints', label: 'Sprints', icon: <Rocket size={16} /> },
  { key: 'team', label: 'Team', icon: <Users size={16} /> },
  { key: 'issues', label: 'Issues', icon: <Activity size={16} /> },
];

export default function App() {
  const [rawIssues, setRawIssues] = useState<JiraIssue[]>([]);
  const [filters, setFilters] = useState<AnalyticsFilters>(EMPTY_FILTERS);
  const [activePage, setActivePage] = useState<PageKey>('overview');
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [uploadedFileName, setUploadedFileName] = useState('');

  const filteredIssues = useMemo(() => filterIssues(rawIssues, filters), [rawIssues, filters]);
  const analytics = useMemo(() => buildAnalytics(filteredIssues), [filteredIssues]);

  const projectOptions = useMemo(
    () =>
      uniqueSortedValues(rawIssues.map((issue) => normalizeText(issue.projectName)).filter(Boolean)),
    [rawIssues]
  );

  const monthOptions = useMemo(() => {
    const monthEntries = rawIssues
      .map((issue) => ({
        monthName: normalizeText(issue.monthName),
        monthNumber: typeof issue.monthNumber === 'number' && Number.isFinite(issue.monthNumber) ? issue.monthNumber : 99,
      }))
      .filter((entry): entry is { monthName: string; monthNumber: number } => entry.monthName.length > 0);

    const grouped = new Map<string, number>();
    for (const entry of monthEntries) {
      const current = grouped.get(entry.monthName);
      if (current === undefined || entry.monthNumber < current) {
        grouped.set(entry.monthName, entry.monthNumber);
      }
    }

    return [...grouped.entries()]
      .map(([monthName, monthNumber]) => ({ monthName, monthNumber }))
      .sort((left, right) => left.monthNumber - right.monthNumber || left.monthName.localeCompare(right.monthName))
      .map((entry) => entry.monthName);
  }, [rawIssues]);

  const sprintOptions = useMemo(
    () =>
      uniqueSortedValues(rawIssues.map((issue) => normalizeText(issue.sprint)).filter(Boolean)),
    [rawIssues]
  );

  const assigneeOptions = useMemo(
    () =>
      uniqueSortedValues(
        rawIssues.map((issue) => formatAssignee(issue.assignee)).filter((value) => value !== 'Unassigned')
      ),
    [rawIssues]
  );

  const issueTypeOptions = useMemo(
    () =>
      uniqueSortedValues(rawIssues.map((issue) => normalizeText(issue.issueType)).filter(Boolean)),
    [rawIssues]
  );

  const hasRawData = rawIssues.length > 0;
  const hasFilteredData = filteredIssues.length > 0;
  const noRowsMessage = hasRawData ? 'No records match the selected filters.' : 'No Jira records found.';

  const handleUpload = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';

    if (!file || loading) {
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      const response = await uploadJiraFile(file);
      const issues = Array.isArray(response.issues) ? response.issues : [];
      setRawIssues(issues);
      setFilters(EMPTY_FILTERS);
      setActivePage('overview');
      setUploadedFileName(file.name);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Upload failed.');
    } finally {
      setLoading(false);
    }
  };

  const renderPage = () => {
    if (!hasRawData) {
      return <EmptyState title={noRowsMessage} description="Upload a Jira Excel report to build the dashboard." />;
    }

    if (!hasFilteredData) {
      return <EmptyState title={noRowsMessage} description="Clear or adjust filters to bring records back." />;
    }

    if (activePage === 'overview') {
      return <OverviewPage data={analytics} />;
    }

    if (activePage === 'sprints') {
      return <SprintAnalyticsPage data={analytics} />;
    }

    if (activePage === 'team') {
      return <TeamAnalyticsPage data={analytics} />;
    }

    return <IssuesPage issues={filteredIssues} />;
  };

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar__brand">
          <div className="sidebar__brand-mark">
            <ShieldAlert size={18} />
          </div>
          <div>
            <div className="sidebar__brand-title">Velocity Analytics</div>
            <div className="sidebar__brand-subtitle">Delivery reporting platform</div>
          </div>
        </div>

        <nav className="sidebar__nav" aria-label="Primary">
          {NAVIGATION.map((item) => (
            <button
              key={item.key}
              type="button"
              className={item.key === activePage ? 'sidebar__nav-item sidebar__nav-item--active' : 'sidebar__nav-item'}
              onClick={() => setActivePage(item.key)}
            >
              <span className="sidebar__nav-icon">{item.icon}</span>
              <span>{item.label}</span>
            </button>
          ))}
        </nav>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div>
            <div className="topbar__eyebrow">Velocity Analytics</div>
            <h1 className="topbar__title">Velocity Analytics</h1>
            <p className="topbar__subtitle">
              Upload Jira or Bitbucket delivery exports once, then filter and analyze the returned records locally.
            </p>
            {uploadedFileName ? <div className="topbar__file">Latest upload: {uploadedFileName}</div> : null}
          </div>

          <label className={loading ? 'upload-button upload-button--disabled' : 'upload-button'}>
            {loading ? <RefreshCw size={18} className="spin" /> : <Upload size={18} />}
            <span>{loading ? 'Processing Jira Report...' : 'Upload Jira Excel'}</span>
            <input type="file" accept=".xlsx,.xls" onChange={handleUpload} disabled={loading} />
          </label>
        </header>

        {errorMessage ? (
          <div className="error-banner" role="alert">
            <ShieldAlert size={16} />
            <span>{errorMessage}</span>
          </div>
        ) : null}

        <FiltersBar
          filters={filters}
          options={{
            projects: projectOptions,
            months: monthOptions,
            sprints: sprintOptions,
            assignees: assigneeOptions,
            issueTypes: issueTypeOptions,
          }}
          loading={loading}
          onChange={setFilters}
          onClear={() => setFilters(EMPTY_FILTERS)}
        />

        {renderPage()}

        {hasRawData ? <div className="footer-note">Loaded records: {rawIssues.length}</div> : null}
      </main>
    </div>
  );
}

function uniqueSortedValues(values: string[]): string[] {
  return [...new Set(values.filter((value) => value.trim().length > 0))].sort((left, right) =>
    left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' })
  );
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <section className="empty-state">
      <FileSpreadsheet size={42} />
      <h2>{title}</h2>
      <p>{description}</p>
      <div className="empty-state__hint">Upload a file and the dashboard will populate automatically.</div>
    </section>
  );
}
