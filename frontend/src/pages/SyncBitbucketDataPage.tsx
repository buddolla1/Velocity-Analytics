import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, GitPullRequest, RefreshCw, ShieldAlert, SquareCheckBig } from 'lucide-react';
import { fetchProjectSsos, fetchProjects, refreshBitbucketCatalog, syncBitbucketData } from '../services/bitbucketSyncApi';
import type {
  BitbucketCatalogRefreshResult,
  BitbucketSyncResult,
  ProjectOption,
} from '../types/bitbucketSync';

type ProgressState = 'idle' | 'loading-projects' | 'loading-ssos' | 'refreshing-catalog' | 'syncing';

export function SyncBitbucketDataPage() {
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [projectSsos, setProjectSsos] = useState<string[]>([]);
  const [selectedSsos, setSelectedSsos] = useState<string[]>([]);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [fullRefresh, setFullRefresh] = useState(false);
  const [progressState, setProgressState] = useState<ProgressState>('idle');
  const [statusMessage, setStatusMessage] = useState('Select a project to begin.');
  const [errorMessage, setErrorMessage] = useState('');
  const [catalogResult, setCatalogResult] = useState<BitbucketCatalogRefreshResult | null>(null);
  const [syncResult, setSyncResult] = useState<BitbucketSyncResult | null>(null);

  const selectedProject = useMemo(
    () => projects.find((project) => String(project.projectId) === selectedProjectId) ?? null,
    [projects, selectedProjectId]
  );

  const canSync =
    Boolean(selectedProject) &&
    selectedSsos.length > 0 &&
    progressState !== 'loading-projects' &&
    progressState !== 'loading-ssos' &&
    progressState !== 'refreshing-catalog' &&
    progressState !== 'syncing';

  useEffect(() => {
    void loadProjects();
  }, []);

  useEffect(() => {
    if (!selectedProjectId) {
      setProjectSsos([]);
      setSelectedSsos([]);
      setSyncResult(null);
      return;
    }
    void loadProjectSsos(Number(selectedProjectId));
  }, [selectedProjectId]);

  async function loadProjects() {
    setProgressState('loading-projects');
    setStatusMessage('Loading application projects.');
    setErrorMessage('');

    try {
      const next = await fetchProjects();
      setProjects(next);
      if (next.length === 1) {
        setSelectedProjectId(String(next[0].projectId));
      }
      setStatusMessage(next.length > 0 ? 'Choose a project, then load SSOs.' : 'No projects found in the database.');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load projects.');
      setStatusMessage('Project load failed.');
    } finally {
      setProgressState('idle');
    }
  }

  async function loadProjectSsos(projectId: number) {
    setProgressState('loading-ssos');
    setStatusMessage('Loading SSOs for the selected project.');
    setErrorMessage('');
    setProjectSsos([]);
    setSelectedSsos([]);
    setSyncResult(null);

    try {
      const next = await fetchProjectSsos(projectId);
      const values = next.map((item) => item.sso).filter(Boolean);
      setProjectSsos(values);
      setStatusMessage(values.length > 0 ? `${values.length} SSOs loaded.` : 'No SSOs were found for this project.');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load project SSOs.');
      setStatusMessage('SSO load failed.');
    } finally {
      setProgressState('idle');
    }
  }

  async function handleRefreshCatalog() {
    if (progressState === 'syncing') {
      return;
    }

    setProgressState('refreshing-catalog');
    setStatusMessage('Refreshing the Bitbucket repository catalog.');
    setErrorMessage('');

    try {
      const next = await refreshBitbucketCatalog();
      setCatalogResult(next);
      setStatusMessage(
        `Catalog refreshed: ${next.projectsDiscovered} projects, ${next.repositoriesDiscovered} repositories.`
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Repository catalog refresh failed.');
      setStatusMessage('Catalog refresh failed.');
    } finally {
      setProgressState('idle');
    }
  }

  async function handleSync() {
    if (!selectedProject || !canSync) {
      return;
    }

    setProgressState('syncing');
    setStatusMessage('Syncing Bitbucket pull requests.');
    setErrorMessage('');

    try {
      const next = await syncBitbucketData({
        projectId: selectedProject.projectId,
        fromDate: fromDate.trim(),
        toDate: toDate.trim(),
        ssos: selectedSsos,
        fullRefresh,
      });
      setSyncResult(next);
      setStatusMessage(
        `Sync complete: ${next.prsDiscovered} PRs discovered, ${next.prsInserted} inserted, ${next.prsUpdated} updated.`
      );
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Bitbucket sync failed.');
      setStatusMessage('Sync failed.');
    } finally {
      setProgressState('idle');
    }
  }

  function toggleSso(sso: string) {
    setSelectedSsos((current) =>
      current.includes(sso) ? current.filter((value) => value !== sso) : [...current, sso]
    );
  }

  function selectAll() {
    setSelectedSsos(projectSsos);
  }

  function clearAll() {
    setSelectedSsos([]);
  }

  return (
    <div className="page-stack">
      <section className="table-panel bitbucket-sync-panel">
        <div className="table-panel__header">
          <div>
            <div className="table-panel__title">Sync Bitbucket Data</div>
            <div className="table-panel__subtitle">
              Select an application project, resolve its SSOs to Bitbucket users, refresh the repository catalog, and sync PR analytics.
            </div>
          </div>
          {selectedProject ? (
            <div className="bitbucket-sync-panel__meta">
              <div>Project ID: {selectedProject.projectId}</div>
              <div>Project Key: {selectedProject.projectKey}</div>
              <div>Project Name: {selectedProject.projectName}</div>
            </div>
          ) : null}
        </div>

        <div className="bitbucket-sync-grid">
          <label className="bitbucket-sync-field">
            <span>Application Project / Team</span>
            <div className="bitbucket-sync-field__control">
              <GitPullRequest size={16} />
              <select
                value={selectedProjectId}
                onChange={(event) => setSelectedProjectId(event.target.value)}
                disabled={progressState === 'loading-projects' || progressState === 'refreshing-catalog' || progressState === 'syncing'}
              >
                <option value="">Select a project</option>
                {projects.map((project) => (
                  <option key={project.projectId} value={project.projectId}>
                    {project.projectName} ({project.projectKey}, #{project.projectId})
                  </option>
                ))}
              </select>
            </div>
          </label>

          <div className="bitbucket-sync-field">
            <span>Project ID</span>
            <div className="bitbucket-sync-field__readonly">
              <CheckCircle2 size={16} />
              <div>{selectedProject ? selectedProject.projectId : 'Select a project to continue'}</div>
            </div>
          </div>

          <div className="bitbucket-sync-field">
            <span>Project Key</span>
            <div className="bitbucket-sync-field__readonly">
              <CheckCircle2 size={16} />
              <div>{selectedProject ? selectedProject.projectKey : 'Select a project to continue'}</div>
            </div>
          </div>

          <div className="bitbucket-sync-field">
            <span>Project Name</span>
            <div className="bitbucket-sync-field__readonly">
              <CheckCircle2 size={16} />
              <div>{selectedProject ? selectedProject.projectName : 'Select a project to continue'}</div>
            </div>
          </div>

          <label className="bitbucket-sync-field">
            <span>From Date</span>
            <div className="bitbucket-sync-field__control bitbucket-sync-field__control--date">
              <ShieldAlert size={16} />
              <input
                type="date"
                value={fromDate}
                onChange={(event) => setFromDate(event.target.value)}
                disabled={progressState === 'syncing'}
              />
            </div>
          </label>

          <label className="bitbucket-sync-field">
            <span>To Date</span>
            <div className="bitbucket-sync-field__control bitbucket-sync-field__control--date">
              <ShieldAlert size={16} />
              <input
                type="date"
                value={toDate}
                onChange={(event) => setToDate(event.target.value)}
                disabled={progressState === 'syncing'}
              />
            </div>
          </label>

          <label className="bitbucket-sync-field bitbucket-sync-field--toggle">
            <span>Full Refresh</span>
            <div className="bitbucket-sync-field__toggle">
              <input
                type="checkbox"
                checked={fullRefresh}
                onChange={(event) => setFullRefresh(event.target.checked)}
                disabled={progressState === 'syncing'}
              />
              <div>Re-resolve SSO mappings and re-enrich PRs even when cached data exists.</div>
            </div>
          </label>
        </div>

        <section className="bitbucket-sso-panel">
          <div className="bitbucket-sso-panel__header">
            <div>
              <div className="bitbucket-sso-panel__title">Team SSOs</div>
              <div className="bitbucket-sso-panel__subtitle">
                {progressState === 'loading-ssos'
                  ? 'Loading SSOs...'
                  : projectSsos.length > 0
                    ? `${projectSsos.length} SSOs found for this project.`
                    : 'Select a project to load SSOs.'}
              </div>
            </div>
            <div className="bitbucket-sso-panel__actions">
              <button
                type="button"
                className="bitbucket-sync-button bitbucket-sync-button--secondary"
                onClick={selectAll}
                disabled={!projectSsos.length || progressState === 'syncing'}
              >
                <SquareCheckBig size={16} />
                Select All
              </button>
              <button
                type="button"
                className="bitbucket-sync-button bitbucket-sync-button--secondary"
                onClick={clearAll}
                disabled={!selectedSsos.length || progressState === 'syncing'}
              >
                Clear All
              </button>
            </div>
          </div>

          <div className="bitbucket-sso-list">
            {projectSsos.length > 0 ? (
              projectSsos.map((sso) => (
                <label key={sso} className="bitbucket-sso-item">
                  <input
                    type="checkbox"
                    checked={selectedSsos.includes(sso)}
                    onChange={() => toggleSso(sso)}
                    disabled={progressState === 'syncing'}
                  />
                  <span>{sso}</span>
                </label>
              ))
            ) : (
              <div className="bitbucket-sso-empty">
                {progressState === 'loading-ssos'
                  ? 'Loading project SSOs...'
                  : 'No SSOs are available for this project.'}
              </div>
            )}
          </div>
        </section>

        <div className="bitbucket-sync-actions">
          <button
            type="button"
            className="bitbucket-sync-button bitbucket-sync-button--secondary"
            onClick={handleRefreshCatalog}
            disabled={progressState === 'loading-projects' || progressState === 'loading-ssos' || progressState === 'refreshing-catalog' || progressState === 'syncing'}
          >
            {progressState === 'refreshing-catalog' ? <RefreshCw size={18} className="spin" /> : <RefreshCw size={18} />}
            Refresh Repository Catalog
          </button>
          <button
            type="button"
            className="bitbucket-sync-button"
            onClick={handleSync}
            disabled={!canSync}
          >
            {progressState === 'syncing' ? <RefreshCw size={18} className="spin" /> : <RefreshCw size={18} />}
            {progressState === 'syncing' ? 'Syncing Bitbucket Data...' : 'Sync Bitbucket Data'}
          </button>
        </div>
      </section>

      <section className="table-panel bitbucket-sync-summary">
        <div className="table-panel__header">
          <div>
            <div className="table-panel__title">Sync Progress</div>
            <div className="table-panel__subtitle">{statusMessage}</div>
          </div>
          <div className="bitbucket-sync-panel__meta">
            <div>State: {progressState}</div>
            <div>Selected SSOs: {selectedSsos.length}</div>
          </div>
        </div>
      </section>

      {errorMessage ? (
        <section className="error-banner" role="alert">
          <ShieldAlert size={16} />
          <span>{errorMessage}</span>
        </section>
      ) : null}

      {catalogResult ? (
        <section className="table-panel bitbucket-sync-result">
          <div className="table-panel__header">
            <div>
              <div className="table-panel__title">Repository Catalog Refresh</div>
              <div className="table-panel__subtitle">The Bitbucket project and repository cache has been updated.</div>
            </div>
            <div className="bitbucket-sync-panel__meta">
              <div>Status: {catalogResult.status}</div>
              <div>Refreshed At: {formatTimestamp(catalogResult.refreshedAt)}</div>
            </div>
          </div>

          <div className="kpi-grid kpi-grid--three bitbucket-sync-result__kpis">
            <div className="kpi-card">
              <div className="kpi-card__title">Projects Discovered</div>
              <div className="kpi-card__value">{catalogResult.projectsDiscovered}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-card__title">Repositories Discovered</div>
              <div className="kpi-card__value">{catalogResult.repositoriesDiscovered}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-card__title">Catalog Status</div>
              <div className="kpi-card__value">{catalogResult.status}</div>
            </div>
          </div>
        </section>
      ) : null}

      {syncResult ? (
        <section className="table-panel bitbucket-sync-result">
          <div className="table-panel__header">
            <div>
              <div className="table-panel__title">Sync Summary</div>
              <div className="table-panel__subtitle">Bitbucket PRs were resolved, enriched, normalized, and written to the database.</div>
            </div>
            <div className="bitbucket-sync-panel__meta">
              <div>Status: {syncResult.status}</div>
              <div>Sync Time: {formatTimestamp(syncResult.syncTime)}</div>
            </div>
          </div>

          <div className="kpi-grid kpi-grid--three bitbucket-sync-result__kpis">
            <div className="kpi-card">
              <div className="kpi-card__title">SSOs Selected</div>
              <div className="kpi-card__value">{syncResult.ssosRequested}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-card__title">User IDs Resolved</div>
              <div className="kpi-card__value">{syncResult.userIdsResolved}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-card__title">Repositories Scanned</div>
              <div className="kpi-card__value">{syncResult.repositoriesScanned}</div>
            </div>
          </div>

          <div className="bitbucket-sync-result__detail">
            <div>Project ID: {syncResult.projectId}</div>
            <div>Catalog Status: {syncResult.catalogStatus}</div>
            <div>Projects Discovered: {syncResult.projectsDiscovered}</div>
            <div>Repositories Discovered: {syncResult.repositoriesDiscovered}</div>
            <div>PRs Found: {syncResult.prsDiscovered}</div>
            <div>New PRs: {syncResult.prsInserted}</div>
            <div>Updated PRs: {syncResult.prsUpdated}</div>
          </div>

          {syncResult.errors.length > 0 ? (
            <div className="bitbucket-sync-error-list">
              {syncResult.errors.map((item) => (
                <div key={`${item.sso}-${item.error}`} className="bitbucket-sync-error-item">
                  <strong>{item.sso}</strong>
                  <span>{item.error}</span>
                </div>
              ))}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

function formatTimestamp(value: string): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}
