import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, GitPullRequest, RefreshCw, ShieldAlert, SquareCheckBig } from 'lucide-react';
import { fetchProjectSsos, fetchProjects, syncBitbucketData } from '../services/bitbucketSyncApi';
import type { BitbucketSyncResult, ProjectOption } from '../types/bitbucketSync';

export function SyncBitbucketDataPage() {
  const [projects, setProjects] = useState<ProjectOption[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [projectSsos, setProjectSsos] = useState<string[]>([]);
  const [selectedSsos, setSelectedSsos] = useState<string[]>([]);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [loadingProjects, setLoadingProjects] = useState(false);
  const [loadingSsos, setLoadingSsos] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [result, setResult] = useState<BitbucketSyncResult | null>(null);

  const selectedProject = useMemo(
    () => projects.find((project) => String(project.projectId) === selectedProjectId) ?? null,
    [projects, selectedProjectId]
  );

  const canSync =
    Boolean(selectedProject) &&
    selectedSsos.length > 0 &&
    !syncing &&
    !loadingProjects &&
    !loadingSsos;

  useEffect(() => {
    void loadProjects();
  }, []);

  useEffect(() => {
    if (!selectedProjectId) {
      setProjectSsos([]);
      setSelectedSsos([]);
      return;
    }
    void loadProjectSsos(Number(selectedProjectId));
  }, [selectedProjectId]);

  async function loadProjects() {
    setLoadingProjects(true);
    setErrorMessage('');
    try {
      const next = await fetchProjects();
      setProjects(next);
      if (next.length === 1) {
        setSelectedProjectId(String(next[0].projectId));
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load projects.');
    } finally {
      setLoadingProjects(false);
    }
  }

  async function loadProjectSsos(projectId: number) {
    setLoadingSsos(true);
    setErrorMessage('');
    setProjectSsos([]);
    setSelectedSsos([]);
    setResult(null);
    try {
      const next = await fetchProjectSsos(projectId);
      const values = next.map((item) => item.sso).filter(Boolean);
      setProjectSsos(values);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Failed to load project SSOs.');
    } finally {
      setLoadingSsos(false);
    }
  }

  async function handleSync() {
    if (!selectedProject || !canSync) {
      return;
    }

    setSyncing(true);
    setErrorMessage('');
    try {
      const next = await syncBitbucketData({
        projectId: selectedProject.projectId,
        fromDate: fromDate.trim(),
        toDate: toDate.trim(),
        ssos: selectedSsos,
      });
      setResult(next);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Bitbucket sync failed.');
    } finally {
      setSyncing(false);
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
              Select a project, load its SSOs, resolve Bitbucket user IDs, and save the mapping.
            </div>
          </div>
          {selectedProject ? (
            <div className="bitbucket-sync-panel__meta">
              <div>Project ID: {selectedProject.projectId}</div>
              <div>Project Key: {selectedProject.projectKey}</div>
            </div>
          ) : null}
        </div>

        <div className="bitbucket-sync-grid">
          <label className="bitbucket-sync-field">
            <span>Project</span>
            <div className="bitbucket-sync-field__control">
              <GitPullRequest size={16} />
              <select
                value={selectedProjectId}
                onChange={(event) => setSelectedProjectId(event.target.value)}
                disabled={loadingProjects || syncing}
              >
                <option value="">Select a project</option>
                {projects.map((project) => (
                  <option key={project.projectId} value={project.projectId}>
                    {project.projectName}
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

          <label className="bitbucket-sync-field">
            <span>From Date</span>
            <div className="bitbucket-sync-field__control bitbucket-sync-field__control--date">
              <ShieldAlert size={16} />
              <input
                type="date"
                value={fromDate}
                onChange={(event) => setFromDate(event.target.value)}
                disabled={syncing}
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
                disabled={syncing}
              />
            </div>
          </label>
        </div>

        <section className="bitbucket-sso-panel">
          <div className="bitbucket-sso-panel__header">
            <div>
              <div className="bitbucket-sso-panel__title">Project SSOs</div>
              <div className="bitbucket-sso-panel__subtitle">
                {loadingSsos
                  ? 'Loading SSOs...'
                  : projectSsos.length > 0
                    ? `${projectSsos.length} SSOs found for this project.`
                    : 'Select a project to load SSOs.'}
              </div>
            </div>
            <div className="bitbucket-sso-panel__actions">
              <button type="button" className="bitbucket-sync-button bitbucket-sync-button--secondary" onClick={selectAll} disabled={!projectSsos.length || syncing}>
                <SquareCheckBig size={16} />
                Select All
              </button>
              <button type="button" className="bitbucket-sync-button bitbucket-sync-button--secondary" onClick={clearAll} disabled={!selectedSsos.length || syncing}>
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
                    disabled={syncing}
                  />
                  <span>{sso}</span>
                </label>
              ))
            ) : (
              <div className="bitbucket-sso-empty">
                {loadingSsos ? 'Loading project SSOs...' : 'No SSOs available for this project.'}
              </div>
            )}
          </div>
        </section>

        <div className="bitbucket-sync-actions">
          <button type="button" className="bitbucket-sync-button" onClick={handleSync} disabled={!canSync}>
            {syncing ? <RefreshCw size={18} className="spin" /> : <RefreshCw size={18} />}
            {syncing ? 'Syncing Bitbucket data...' : 'Sync Bitbucket Data'}
          </button>
        </div>
      </section>

      {errorMessage ? (
        <section className="error-banner" role="alert">
          <ShieldAlert size={16} />
          <span>{errorMessage}</span>
        </section>
      ) : null}

      {result ? (
        <section className="table-panel bitbucket-sync-result">
          <div className="table-panel__header">
            <div>
              <div className="table-panel__title">Sync Result</div>
              <div className="table-panel__subtitle">SSO mapping sync completed for the selected project.</div>
            </div>
            <div className="bitbucket-sync-panel__meta">
              <div>Status: {result.status}</div>
              <div>Sync Time: {formatSyncTime(result.syncTime)}</div>
            </div>
          </div>

          <div className="kpi-grid kpi-grid--three bitbucket-sync-result__kpis">
            <div className="kpi-card">
              <div className="kpi-card__title">SSOs Selected</div>
              <div className="kpi-card__value">{result.ssosRequested}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-card__title">User IDs Resolved</div>
              <div className="kpi-card__value">{result.userIdsResolved}</div>
            </div>
            <div className="kpi-card">
              <div className="kpi-card__title">Errors</div>
              <div className="kpi-card__value">{result.errors.length}</div>
            </div>
          </div>

          <div className="bitbucket-sync-result__detail">
            <div>Project ID: {result.projectId}</div>
            <div>PRs Found: {result.prsDiscovered}</div>
            <div>New PRs: {result.prsInserted}</div>
            <div>Updated PRs: {result.prsUpdated}</div>
          </div>

          {result.errors.length > 0 ? (
            <div className="bitbucket-sync-error-list">
              {result.errors.map((item) => (
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

function formatSyncTime(syncTime: string): string {
  if (!syncTime) {
    return '—';
  }
  const date = new Date(syncTime);
  if (Number.isNaN(date.getTime())) {
    return syncTime;
  }
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}
