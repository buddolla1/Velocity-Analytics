import { FormEvent, useMemo, useState } from 'react';
import { CheckCircle2, RefreshCw, ShieldAlert, UserRoundPen } from 'lucide-react';
import type { JiraSyncRequest } from '../types/analytics';

interface JiraSyncPageProps {
  loading: boolean;
  lastSyncAt: string;
  onSync: (payload: JiraSyncRequest) => Promise<void>;
}

export function JiraSyncPage({ loading, lastSyncAt, onSync }: JiraSyncPageProps) {
  const [username, setUsername] = useState('');
  const [apiToken, setApiToken] = useState('');
  const [projectKey, setProjectKey] = useState('');
  const [startDate, setStartDate] = useState(() => formatLocalDate(new Date()));

  const canSync = useMemo(
    () =>
      username.trim().length > 0 &&
      apiToken.trim().length > 0 &&
      projectKey.trim().length > 0 &&
      startDate.trim().length > 0 &&
      !loading,
    [apiToken, loading, projectKey, startDate, username]
  );

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!canSync) {
      return;
    }

    await onSync({
      username: username.trim(),
      apiToken: apiToken.trim(),
      projectKey: projectKey.trim(),
      startDate: startDate.trim(),
    });
  };

  return (
    <div className="page-stack">
      <section className="table-panel pr-sync-panel">
        <div className="table-panel__header">
          <div>
            <div className="table-panel__title">Jira Sync</div>
            <div className="table-panel__subtitle">
              Enter one Jira username, one API token, a start date, and one or more project keys, then sync them into the local database.
            </div>
          </div>
          {lastSyncAt ? <div className="pr-sync-panel__stamp">Last sync: {lastSyncAt}</div> : null}
        </div>

        <form className="jira-sync-grid" onSubmit={handleSubmit}>
          <label className="jira-sync-field">
            <span>jira.username</span>
            <div className="jira-sync-field__control jira-sync-field__control--single">
              <UserRoundPen size={16} />
              <input
                type="text"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="user@company.com"
                spellCheck={false}
                disabled={loading}
              />
            </div>
            <div className="jira-sync-field__hint">
              Single Jira Cloud username or email address.
            </div>
          </label>

          <label className="jira-sync-field">
            <span>jira.api-token</span>
            <div className="jira-sync-field__control jira-sync-field__control--single">
              <ShieldAlert size={16} />
              <input
                type="password"
                value={apiToken}
                onChange={(event) => setApiToken(event.target.value)}
                placeholder="Jira API token"
                spellCheck={false}
                autoComplete="off"
                disabled={loading}
              />
            </div>
            <div className="jira-sync-field__hint">
              Single Jira API token for that account.
            </div>
          </label>

          <label className="jira-sync-field">
            <span>jira.start-date</span>
            <div className="jira-sync-field__control jira-sync-field__control--date">
              <CheckCircle2 size={16} />
              <input
                type="date"
                value={startDate}
                onChange={(event) => setStartDate(event.target.value)}
                disabled={loading}
              />
            </div>
            <div className="jira-sync-field__hint">
              Records sync from this date through today.
            </div>
          </label>

          <label className="jira-sync-field">
            <span>jira.project-key</span>
            <div className="jira-sync-field__control">
              <CheckCircle2 size={16} />
              <textarea
                value={projectKey}
                onChange={(event) => setProjectKey(event.target.value)}
                placeholder="OPS, ENG, DATA"
                rows={4}
                spellCheck={false}
                disabled={loading}
              />
            </div>
            <div className="jira-sync-field__hint">
              Multiple project keys are allowed. The sync runs once per key and stores the merged Jira records locally.
            </div>
          </label>

          <button type="submit" className="jira-sync-button" disabled={!canSync}>
            {loading ? <RefreshCw size={18} className="spin" /> : <RefreshCw size={18} />}
            {loading ? 'Syncing Jira...' : 'Sync Jira Data'}
          </button>
        </form>
      </section>
    </div>
  );
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
