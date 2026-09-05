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
  const [endDate, setEndDate] = useState(() => formatLocalDate(new Date()));

  const canSync = useMemo(
    () =>
      username.trim().length > 0 &&
      apiToken.trim().length > 0 &&
      projectKey.trim().length > 0 &&
      endDate.trim().length > 0 &&
      !loading,
    [apiToken, endDate, loading, projectKey, username]
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
      endDate: endDate.trim(),
    });
  };

  return (
    <div className="page-stack">
      <section className="table-panel pr-sync-panel">
        <div className="table-panel__header">
          <div>
            <div className="table-panel__title">Jira Sync</div>
            <div className="table-panel__subtitle">
              Enter Jira Cloud credentials and comma-separated project keys, then sync them into the local database.
            </div>
          </div>
          {lastSyncAt ? <div className="pr-sync-panel__stamp">Last sync: {lastSyncAt}</div> : null}
        </div>

        <form className="jira-sync-grid" onSubmit={handleSubmit}>
          <label className="jira-sync-field">
            <span>jira.username</span>
            <div className="jira-sync-field__control">
              <UserRoundPen size={16} />
              <textarea
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                placeholder="user@company.com, another-user@company.com"
                rows={4}
                spellCheck={false}
                disabled={loading}
              />
            </div>
            <div className="jira-sync-field__hint">
              One value or several comma-separated values. If you provide one username, it applies to every project key.
            </div>
          </label>

          <label className="jira-sync-field">
            <span>jira.api-token</span>
            <div className="jira-sync-field__control">
              <ShieldAlert size={16} />
              <textarea
                value={apiToken}
                onChange={(event) => setApiToken(event.target.value)}
                placeholder="token-1, token-2"
                rows={4}
                spellCheck={false}
                disabled={loading}
              />
            </div>
            <div className="jira-sync-field__hint">
              Tokens can be comma-separated as well. A single token is reused for every project key.
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

          <label className="jira-sync-field">
            <span>end date</span>
            <div className="jira-sync-field__control jira-sync-field__control--date">
              <CheckCircle2 size={16} />
              <input
                type="date"
                value={endDate}
                onChange={(event) => setEndDate(event.target.value)}
                disabled={loading}
              />
            </div>
            <div className="jira-sync-field__hint">The sync query uses `updated &lt;=` this date in `yyyy-MM-dd` format.</div>
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
