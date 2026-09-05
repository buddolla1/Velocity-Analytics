import type { PrAnalyticsResponse } from '../types/prAnalytics';

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

export async function uploadJiraBitbucketFiles(
  jiraFile: File,
  bitbucketFile: File
): Promise<PrAnalyticsResponse> {
  const formData = new FormData();
  formData.append('jiraFile', jiraFile);
  formData.append('bitbucketFile', bitbucketFile);

  try {
    const response = await fetch('/api/jira-bitbucket/analytics', {
      method: 'POST',
      body: formData,
    });

    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }

    return (await response.json()) as PrAnalyticsResponse;
  } catch (error) {
    if (error instanceof Error && error.message && error.message !== 'Failed to fetch') {
      throw error;
    }
    throw new Error('Backend unavailable. Confirm Spring Boot is running on http://localhost:8080.');
  }
}
