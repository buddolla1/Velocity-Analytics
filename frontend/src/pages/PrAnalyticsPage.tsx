import { useMemo, useState, type ChangeEvent } from 'react';
import {
  Activity,
  CheckCircle2,
  FileSpreadsheet,
  RefreshCw,
  Rocket,
  ShieldAlert,
  Upload,
  Users,
} from 'lucide-react';
import { PrKpiCard } from '../components/pr/PrKpiCard';
import { PrFiltersBar } from '../components/pr/PrFiltersBar';
import { MonthlyPrThroughputChart } from '../components/pr/MonthlyPrThroughputChart';
import { MonthlyPrCycleTimeChart } from '../components/pr/MonthlyPrCycleTimeChart';
import { EmployeePrCycleTimeChart } from '../components/pr/EmployeePrCycleTimeChart';
import { CycleStartSourceChart } from '../components/pr/CycleStartSourceChart';
import { RepositoryAnalyticsChart } from '../components/pr/RepositoryAnalyticsChart';
import { EmployeePrMetricsTable } from '../components/pr/EmployeePrMetricsTable';
import { RepositoryPrMetricsTable } from '../components/pr/RepositoryPrMetricsTable';
import { WeeklyCycleTimeTable } from '../components/pr/WeeklyCycleTimeTable';
import { PrDetailsTable } from '../components/pr/PrDetailsTable';
import { uploadJiraBitbucketFiles } from '../services/prAnalyticsApi';
import type { PrAnalyticsFilters, PrAnalyticsRecord, PrAnalyticsResponse } from '../types/prAnalytics';
import {
  buildPrAnalytics,
  normalizeText,
  resolveEmployeeName,
  resolveMonthDescriptor,
  resolveRepositoryName,
  filterPrRecords,
} from '../utils/prAnalytics';

const EMPTY_FILTERS: PrAnalyticsFilters = {
  project: '',
  repository: '',
  month: '',
  employee: '',
  jiraMatch: 'all',
  sprint: '',
  cycleStartSource: '',
};

const CYCLE_START_SOURCE_ORDER = ['BB FIRST COMMIT', 'BB PR CREATED', 'JIRA IN PROGRESS'];

export default function PrAnalyticsPage() {
  const [report, setReport] = useState<PrAnalyticsResponse | null>(null);
  const [filters, setFilters] = useState<PrAnalyticsFilters>(EMPTY_FILTERS);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [jiraFile, setJiraFile] = useState<File | null>(null);
  const [bitbucketFile, setBitbucketFile] = useState<File | null>(null);
  const [jiraFileName, setJiraFileName] = useState('');
  const [bitbucketFileName, setBitbucketFileName] = useState('');

  const rawRecords = report?.records ?? [];
  const filteredRecords = useMemo(() => filterPrRecords(rawRecords, filters), [rawRecords, filters]);
  const analytics = useMemo(() => buildPrAnalytics(filteredRecords), [filteredRecords]);

  const options = useMemo(() => buildFilterOptions(rawRecords), [rawRecords]);
  const hasLoadedRecords = rawRecords.length > 0;
  const hasFilteredRecords = filteredRecords.length > 0;
  const canGenerate = Boolean(jiraFile && bitbucketFile) && !loading;

  const handleJiraFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    event.target.value = '';
    setJiraFile(file);
    setJiraFileName(file?.name ?? '');
  };

  const handleBitbucketFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    event.target.value = '';
    setBitbucketFile(file);
    setBitbucketFileName(file?.name ?? '');
  };

  const handleGenerate = async () => {
    if (!jiraFile || !bitbucketFile || loading) {
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      const next = await uploadJiraBitbucketFiles(jiraFile, bitbucketFile);
      setReport(next);
      setFilters(EMPTY_FILTERS);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Upload failed.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-stack">
      <header className="topbar topbar--page">
        <div>
          <div className="topbar__eyebrow">Velocity Analytics</div>
          <h1 className="topbar__title">Cycle Time / PR Analytics</h1>
          <p className="topbar__subtitle">
            Analyze the combined Jira and Bitbucket report, then slice the returned PR records locally across project,
            repository, month, employee, Jira match, sprint, and cycle-start source.
          </p>
        </div>
      </header>

      <section className="table-panel pr-upload-panel">
        <div className="table-panel__header">
          <div>
            <div className="table-panel__title">Upload Report Files</div>
            <div className="table-panel__subtitle">Select the Jira Excel and Bitbucket Excel exports before generating analytics.</div>
          </div>
        </div>

        <div className="pr-upload-grid">
          <label className="pr-upload-field">
            <span>Jira Excel</span>
            <div className="pr-upload-field__control">
              <Upload size={16} />
              <span>Choose File</span>
              <input type="file" accept=".xlsx,.xls" onChange={handleJiraFileChange} disabled={loading} />
            </div>
            <div className="pr-upload-field__name">{jiraFileName || 'No file selected'}</div>
          </label>

          <label className="pr-upload-field">
            <span>Bitbucket Excel</span>
            <div className="pr-upload-field__control">
              <Upload size={16} />
              <span>Choose File</span>
              <input type="file" accept=".xlsx,.xls" onChange={handleBitbucketFileChange} disabled={loading} />
            </div>
            <div className="pr-upload-field__name">{bitbucketFileName || 'No file selected'}</div>
          </label>

          <button type="button" className="pr-generate-button" onClick={handleGenerate} disabled={!canGenerate}>
            {loading ? <RefreshCw size={18} className="spin" /> : <Rocket size={18} />}
            Generate PR Analytics
          </button>
        </div>
      </section>

      {errorMessage ? (
        <div className="error-banner" role="alert">
          <ShieldAlert size={16} />
          <span>{errorMessage}</span>
        </div>
      ) : null}

      {hasLoadedRecords ? (
        <PrFiltersBar
          filters={filters}
          options={options}
          loading={loading}
          onChange={setFilters}
          onClear={() => setFilters(EMPTY_FILTERS)}
        />
      ) : null}

      {hasLoadedRecords && !hasFilteredRecords ? (
        <section className="empty-state">
          <FileSpreadsheet size={42} />
          <h2>No PR records match the selected filters.</h2>
          <p>Clear or adjust filters to bring records back into view.</p>
          <div className="empty-state__hint">Filtering stays local after the initial upload.</div>
        </section>
      ) : null}

      {hasLoadedRecords && hasFilteredRecords ? (
        <>
          <section className="kpi-grid">
            <PrKpiCard title="Total PRs" value={analytics.summary.totalPrs} icon={<FileSpreadsheet size={18} />} />
            <PrKpiCard title="Merged PRs" value={analytics.summary.mergedPrs} icon={<CheckCircle2 size={18} />} />
            <PrKpiCard
              title="Jira Match %"
              value={analytics.summary.jiraMatchPercentage}
              icon={<Activity size={18} />}
              suffix="%"
            />
            <PrKpiCard
              title="Average Cycle Time"
              value={analytics.summary.averageCycleTimeDays}
              icon={<Activity size={18} />}
              suffix="days"
            />
            <PrKpiCard
              title="Median Cycle Time"
              value={analytics.summary.medianCycleTimeDays}
              icon={<Activity size={18} />}
              suffix="days"
            />
            <PrKpiCard title="Active PR Authors" value={analytics.summary.activePrAuthors} icon={<Users size={18} />} />
          </section>

          <section className="table-panel match-summary-panel">
            <div className="table-panel__header">
              <div>
                <div className="table-panel__title">Jira Match Analytics</div>
                <div className="table-panel__subtitle">Matched PRs, unmatched PRs, and match percentage for the filtered records.</div>
              </div>
            </div>
            <div className="kpi-grid kpi-grid--three">
              <PrKpiCard title="Matched PRs" value={analytics.summary.jiraMatchedPrs} icon={<CheckCircle2 size={18} />} />
              <PrKpiCard title="Unmatched PRs" value={analytics.summary.unmatchedPrs} icon={<ShieldAlert size={18} />} />
              <PrKpiCard
                title="Jira Match Percentage"
                value={analytics.summary.jiraMatchPercentage}
                icon={<Activity size={18} />}
                suffix="%"
              />
            </div>
          </section>

          <div className="chart-grid">
            <MonthlyPrThroughputChart data={analytics.monthlyMetrics} />
            <MonthlyPrCycleTimeChart data={analytics.monthlyMetrics} />
          </div>

          <div className="chart-grid">
            <EmployeePrCycleTimeChart data={analytics.employeeMetrics} />
            <CycleStartSourceChart data={analytics.cycleStartSources} />
          </div>

          <div className="page-stack">
            <RepositoryAnalyticsChart data={analytics.repositoryMetrics} />
            <RepositoryPrMetricsTable data={analytics.repositoryMetrics} />
            <WeeklyCycleTimeTable data={analytics.weeklyCycleTime} />
            <EmployeePrMetricsTable data={analytics.employeeMetrics} />
            <PrDetailsTable data={filteredRecords} />
          </div>
        </>
      ) : !hasLoadedRecords ? (
        <section className="empty-state">
          <FileSpreadsheet size={42} />
          <h2>{loading ? 'Generating PR analytics...' : 'Upload Jira and Bitbucket Excel files to begin.'}</h2>
          <p>PR analytics stay on this page, separate from the Jira-only dashboard.</p>
          <div className="empty-state__hint">The upload step runs once. Filtering and recomputation happen locally afterwards.</div>
        </section>
      ) : null}

      {hasLoadedRecords ? <div className="footer-note">Loaded PR records: {rawRecords.length}</div> : null}
    </div>
  );
}

function buildFilterOptions(records: PrAnalyticsRecord[]) {
  const projects = uniqueSortedValues(records.map((record) => normalizeText(record.projectName)).filter(Boolean));
  const repositories = uniqueSortedValues(records.map((record) => resolveRepositoryName(record)));
  const employees = uniqueSortedValues(records.map((record) => resolveEmployeeName(record)));
  const sprints = uniqueSortedValues(records.map((record) => normalizeText(record.jiraSprint)).filter(Boolean));
  const monthGroups = new Map<string, number>();
  const cycleStartSources = uniqueSortedValues(records.map((record) => normalizeText(record.cycleStartSource)).filter(Boolean));

  for (const record of records) {
    const month = resolveMonthDescriptor(record);
    if (!month) {
      continue;
    }

    const current = monthGroups.get(month.monthName);
    if (current === undefined || month.monthNumber < current) {
      monthGroups.set(month.monthName, month.monthNumber);
    }
  }

  const months = [...monthGroups.entries()]
    .map(([monthName, monthNumber]) => ({ monthName, monthNumber }))
    .sort((left, right) => left.monthNumber - right.monthNumber || left.monthName.localeCompare(right.monthName))
    .map((entry) => entry.monthName);

  return {
    projects,
    repositories,
    months,
    employees,
    sprints,
    cycleStartSources: orderedCycleStartSources(cycleStartSources),
  };
}

function orderedCycleStartSources(values: string[]): string[] {
  return [
    ...CYCLE_START_SOURCE_ORDER.filter((source) => values.includes(source)),
    ...values.filter((value) => !CYCLE_START_SOURCE_ORDER.includes(value)),
  ];
}

function uniqueSortedValues(values: string[]): string[] {
  return [...new Set(values.filter((value) => value.trim().length > 0))].sort((left, right) =>
    left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' })
  );
}
