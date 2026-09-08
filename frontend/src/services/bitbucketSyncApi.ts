import type {
  BitbucketCatalogRefreshResult,
  BitbucketSyncRequest,
  BitbucketSyncResult,
  ProjectOption,
  ProjectSso,
} from '../types/bitbucketSync';

async function readErrorMessage(response: Response): Promise<string> {
  const contentType = response.headers.get('content-type') ?? '';

  if (contentType.includes('application/json')) {
    try {
      const payload: unknown = await response.json();
      if (payload && typeof payload === 'object') {
        const record = payload as { message?: unknown; error?: unknown; detail?: unknown };
        const message = record.message ?? record.error ?? record.detail;
        if (typeof message === 'string' && message.trim()) {
          return message;
        }
      }
    } catch {
      // Fall through to text handling.
    }
  }

  const text = (await response.text()).trim();
  return text || `HTTP ${response.status}`;
}

async function readJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }
  return (await response.json()) as T;
}

export async function fetchProjects(): Promise<ProjectOption[]> {
  const response = await fetch('/api/projects');
  return await readJson<ProjectOption[]>(response);
}

export async function fetchProjectSsos(projectId: number): Promise<ProjectSso[]> {
  const response = await fetch(`/api/projects/${projectId}/ssos`);
  return await readJson<ProjectSso[]>(response);
}

export async function refreshBitbucketCatalog(): Promise<BitbucketCatalogRefreshResult> {
  const response = await fetch('/api/bitbucket/catalog/refresh', {
    method: 'POST',
  });
  return await readJson<BitbucketCatalogRefreshResult>(response);
}

export async function syncBitbucketData(payload: BitbucketSyncRequest): Promise<BitbucketSyncResult> {
  const response = await fetch('/api/bitbucket/sync', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
  return await readJson<BitbucketSyncResult>(response);
}
