export interface JiraIssue {
  issueType: string;
  issueKey: string;
  issueId: string;
  status: string;
  projectKey: string;
  projectName: string;
  summary: string;
  storyPoints: number | null;
  sprint: string | null;
  assignee: string | null;
  sso: string | null;
  resolvedAt: string | null;
  monthNumber: number | null;
  monthName: string | null;
  toDoToInProgressAt: string | null;
  inProgressToValidationAt: string | null;
  validationToDoneAt: string | null;
  jiraCycleTimeDays: number | null;
  jiraUpdatedAt: string | null;
  lastSyncedAt: string | null;
}

export interface DashboardSummary {
  totalIssues: number;
  storiesCompleted: number;
  storyPointsCompleted: number;
  defectsClosed: number;
  activeContributors: number;
  averageCycleTimeDays: number | null;
}

export interface MonthlyVelocity {
  monthNumber: number;
  monthName: string;
  storiesCompleted: number;
  storyPointsCompleted: number;
  defectsCompleted: number;
}

export interface SprintMetrics {
  sprint: string;
  totalStories: number;
  completedStories: number;
  storyPoints: number;
  completionPercentage: number;
  defects: number;
  contributors: number;
}

export interface EmployeeMetrics {
  employee: string;
  storiesCompleted: number;
  storyPointsCompleted: number;
  defectsCompleted: number;
  sprintCount: number;
  averageCycleTimeDays: number | null;
}

export interface IssueTypeMetrics {
  issueType: string;
  count: number;
}

export interface DashboardResponse {
  summary: DashboardSummary;
  monthlyVelocity: MonthlyVelocity[];
  sprintMetrics: SprintMetrics[];
  employeeMetrics: EmployeeMetrics[];
  issueTypeMetrics: IssueTypeMetrics[];
  issues: JiraIssue[];
}

export interface AnalyticsFilters {
  project: string;
  month: string;
  sprint: string;
  assignee: string;
  issueType: string;
}

export interface JiraSyncRequest {
  username: string;
  apiToken: string;
  projectKey: string;
  startDate: string;
}
