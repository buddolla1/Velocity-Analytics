import type { DashboardResponse, JiraSyncRequest } from '../types/analytics';

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

async function readDashboardResponse(response: Response): Promise<DashboardResponse> {
  if (!response.ok) {
    throw new Error(await readErrorMessage(response));
  }

  return (await response.json()) as DashboardResponse;
}

export async function fetchJiraDashboard(): Promise<DashboardResponse> {
  try {
    const response = await fetch('/api/jira/dashboard');
    return await readDashboardResponse(response);
  } catch (error) {
    if (error instanceof Error && error.message && error.message !== 'Failed to fetch') {
      throw error;
    }
    throw new Error('Backend unavailable. Confirm Spring Boot is running on http://localhost:8082.');
  }
}

export async function syncJiraDashboard(payload: JiraSyncRequest): Promise<DashboardResponse> {
  try {
    const response = await fetch('/api/jira/sync', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    });
    return await readDashboardResponse(response);
  } catch (error) {
    if (error instanceof Error && error.message && error.message !== 'Failed to fetch') {
      throw error;
    }
    throw new Error('Backend unavailable. Confirm Spring Boot is running on http://localhost:8082.');
  }
}

export async function uploadJiraFile(file: File): Promise<DashboardResponse> {
  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await fetch('/api/jira/upload', {
      method: 'POST',
      body: formData,
    });

    return await readDashboardResponse(response);
  } catch (error) {
    if (error instanceof Error && error.message && error.message !== 'Failed to fetch') {
      throw error;
    }
    throw new Error('Backend unavailable. Confirm Spring Boot is running on http://localhost:8082.');
  }
}
