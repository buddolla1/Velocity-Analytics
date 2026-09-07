export interface ProjectOption {
  projectId: number;
  projectKey: string;
  projectName: string;
}

export interface ProjectSso {
  sso: string;
}

export interface BitbucketSyncRequest {
  projectId: number;
  fromDate: string;
  toDate: string;
  ssos: string[];
}

export interface SyncError {
  sso: string;
  error: string;
}

export interface BitbucketSyncResult {
  status: string;
  projectId: number;
  ssosRequested: number;
  userIdsResolved: number;
  prsDiscovered: number;
  prsInserted: number;
  prsUpdated: number;
  errors: SyncError[];
  syncTime: string;
}
