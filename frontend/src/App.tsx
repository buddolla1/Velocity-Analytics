import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  Activity,
  ArrowUpRight,
  CheckCircle2,
  GitPullRequest,
  LayoutDashboard,
  RefreshCw,
  Rocket,
  ShieldAlert,
  Users,
} from 'lucide-react';
import { FiltersBar } from './components/FiltersBar';
import { SyncBitbucketDataPage } from './pages/SyncBitbucketDataPage';
import { OverviewPage } from './pages/OverviewPage';
import { JiraSyncPage } from './pages/JiraSyncPage';
import PrAnalyticsPage from './pages/PrAnalyticsPage';
import { SprintAnalyticsPage } from './pages/SprintAnalyticsPage';
import { TeamAnalyticsPage } from './pages/TeamAnalyticsPage';
import { IssuesPage } from './pages/IssuesPage';
import { fetchJiraDashboard, syncJiraDashboard } from './services/jiraApi';
import type { AnalyticsFilters, DashboardResponse, JiraIssue } from './types/analytics';
import { buildAnalytics, filterIssues, formatAssignee, normalizeText } from './utils/analytics';

type PageKey = 'sync' | 'bitbucket-sync' | 'overview' | 'sprints' | 'team' | 'issues' | 'pr';

const EMPTY_FILTERS: AnalyticsFilters = {
  project: '',
  month: '',
  sprint: '',
  assignee: '',
  issueType: '',
};

const NAVIGATION: Array<{ key: PageKey; label: string; icon: ReactNode }> = [
  { key: 'sync', label: 'Jira Sync', icon: <RefreshCw size={16} /> },
  { key: 'bitbucket-sync', label: 'Sync Bitbucket Data', icon: <GitPullRequest size={16} /> },
  { key: 'overview', label: 'Overview', icon: <LayoutDashboard size={16} /> },
  { key: 'sprints', label: 'Sprints', icon: <Rocket size={16} /> },
  { key: 'team', label: 'Team', icon: <Users size={16} /> },
  { key: 'issues', label: 'Issues', icon: <Activity size={16} /> },
  { key: 'pr', label: 'Cycle Time / PR Analytics', icon: <ArrowUpRight size={16} /> },
];

export default function App() {
  const [rawIssues, setRawIssues] = useState<JiraIssue[]>([]);
  const [filters, setFilters] = useState<AnalyticsFilters>(EMPTY_FILTERS);
  const [activePage, setActivePage] = useState<PageKey>(() => getPageFromPath(window.location.pathname));
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [lastSyncAt, setLastSyncAt] = useState('');

  const filteredIssues = useMemo(() => filterIssues(rawIssues, filters), [rawIssues, filters]);
  const analytics = useMemo(() => buildAnalytics(filteredIssues), [filteredIssues]);

  useEffect(() => {
    void loadDashboard();
  }, []);

  useEffect(() => {
    const handlePopState = () => {
      setActivePage(getPageFromPath(window.location.pathname));
    };

    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, []);

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
  const topbarSubtitle =
    activePage === 'sync'
    ? 'Enter one Jira username, one API token, a start date, and project keys on the sync page.'
      : activePage === 'bitbucket-sync'
        ? 'Select a project, load SSOs, refresh the repository catalog, and sync Bitbucket PR data.'
      : 'Sync Jira Cloud data into the local database, then filter and analyze the returned records locally.';

  async function loadDashboard() {
    setLoading(true);
    setErrorMessage('');

    try {
      const response = await fetchJiraDashboard();
      const issues = Array.isArray(response.issues) ? response.issues : [];
      setRawIssues(issues);
      setLastSyncAt(formatLatestSyncAt(issues));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load Jira dashboard.');
    } finally {
      setLoading(false);
    }
  }

  async function handleSync(payload: { username: string; apiToken: string; projectKey: string; startDate: string }) {
    if (loading) {
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      const response = await syncJiraDashboard(payload);
      const issues = Array.isArray(response.issues) ? response.issues : [];
      setRawIssues(issues);
      setFilters(EMPTY_FILTERS);
      setActivePage('overview');
      setLastSyncAt(formatLatestSyncAt(issues));
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Sync failed.');
    } finally {
      setLoading(false);
    }
  }

  const renderPage = () => {
    if (activePage === 'sync') {
      return <JiraSyncPage loading={loading} lastSyncAt={lastSyncAt} onSync={handleSync} />;
    }

    if (activePage === 'bitbucket-sync') {
      return <SyncBitbucketDataPage />;
    }

    if (activePage === 'pr') {
      return <PrAnalyticsPage />;
    }

    if (!hasRawData) {
      return <EmptyState title={noRowsMessage} description="Sync Jira data to build the dashboard." />;
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
              onClick={() => navigateToPage(item.key, setActivePage)}
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
            <p className="topbar__subtitle">{topbarSubtitle}</p>
            {lastSyncAt ? <div className="topbar__file">Last sync: {lastSyncAt}</div> : null}
          </div>

        </header>

        {activePage === 'pr' ? null : errorMessage ? (
          <div className="error-banner" role="alert">
            <ShieldAlert size={16} />
            <span>{errorMessage}</span>
          </div>
        ) : null}

        {activePage === 'sync' || activePage === 'bitbucket-sync' || activePage === 'pr' ? null : (
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
        )}

        {renderPage()}

        {activePage !== 'pr' && activePage !== 'sync' && activePage !== 'bitbucket-sync' && hasRawData ? (
          <div className="footer-note">Loaded records: {rawIssues.length}</div>
        ) : null}
      </main>
    </div>
  );
}

function uniqueSortedValues(values: string[]): string[] {
  return [...new Set(values.filter((value) => value.trim().length > 0))].sort((left, right) =>
    left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' })
  );
}

function getPageFromPath(pathname: string): PageKey {
  if (pathname === '/bitbucket/sync') {
    return 'bitbucket-sync';
  }
  if (pathname === '/overview') {
    return 'overview';
  }
  if (pathname === '/sprints') {
    return 'sprints';
  }
  if (pathname === '/team') {
    return 'team';
  }
  if (pathname === '/issues') {
    return 'issues';
  }
  if (pathname === '/pr') {
    return 'pr';
  }
  return 'sync';
}

function getPathForPage(page: PageKey): string {
  switch (page) {
    case 'bitbucket-sync':
      return '/bitbucket/sync';
    case 'overview':
      return '/overview';
    case 'sprints':
      return '/sprints';
    case 'team':
      return '/team';
    case 'issues':
      return '/issues';
    case 'pr':
      return '/pr';
    case 'sync':
    default:
      return '/';
  }
}

function navigateToPage(page: PageKey, setActivePage: (page: PageKey) => void) {
  const nextPath = getPathForPage(page);
  if (window.location.pathname !== nextPath) {
    window.history.pushState({}, '', nextPath);
  }
  setActivePage(page);
}

function formatLatestSyncAt(issues: JiraIssue[]): string {
  const syncValues = issues
    .map((issue) => issue.lastSyncedAt)
    .filter((value): value is string => Boolean(value && value.trim().length > 0));

  if (syncValues.length === 0) {
    return '';
  }

  const latest = syncValues
    .map((value) => new Date(value))
    .filter((value) => !Number.isNaN(value.getTime()))
    .sort((left, right) => right.getTime() - left.getTime())[0];

  if (!latest) {
    return '';
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(latest);
}

function EmptyState({ title, description }: { title: string; description: string }) {
  return (
    <section className="empty-state">
      <RefreshCw size={42} />
      <h2>{title}</h2>
      <p>{description}</p>
      <div className="empty-state__hint">Sync Jira data and the dashboard will populate automatically.</div>
    </section>
  );
}
