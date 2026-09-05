export interface PrAnalyticsRecord {
  prId: string;
  repositoryName: string;
  projectName: string;
  authorName: string;
  authorUsername?: string | null;
  state: string;
  prTitle?: string | null;
  prDescription?: string | null;
  sourceBranch?: string | null;
  destinationBranch?: string | null;
  jiraKey?: string | null;
  jiraMatch: boolean;
  jiraAssignee?: string | null;
  jiraStatus?: string | null;
  jiraProjectName?: string | null;
  jiraSprint?: string | null;
  jiraStoryPoints?: number | null;
  jiraInProgressAt?: string | null;
  prCreatedAt?: string | null;
  firstCommitAt?: string | null;
  cycleStart?: string | null;
  cycleStartSource?: string | null;
  prMergedAt?: string | null;
  cycleTimeHours?: number | null;
  cycleTimeDays?: number | null;
}

export interface PrAnalyticsSummary {
  totalPrs: number;
  mergedPrs: number;
  jiraMatchedPrs: number;
  unmatchedPrs: number;
  jiraMatchPercentage: number;
  averageCycleTimeDays: number | null;
  medianCycleTimeDays: number | null;
  averageFirstCommitToMergeDays: number | null;
  averageCreatedToMergeDays: number | null;
  activePrAuthors: number;
}

export interface MonthlyPrMetrics {
  monthNumber: number;
  monthName: string;
  prsMerged: number;
  averageCycleTimeDays: number | null;
  matchedPrs: number;
  unmatchedPrs: number;
}

export interface EmployeePrMetrics {
  employee: string;
  prsMerged: number;
  jiraMatchedPrs: number;
  averageCycleTimeDays: number | null;
  medianCycleTimeDays: number | null;
  repositories: number;
}

export interface RepositoryPrMetrics {
  repository: string;
  prsMerged: number;
  averageCycleTimeDays: number | null;
  jiraMatchPercentage: number;
}

export interface CycleStartSourceMetric {
  source: string;
  count: number;
  percentage: number;
}

export interface WeeklyCycleTimeWeek {
  weekNumber: number;
  weekStart: string;
  weekEnd: string;
  averagesByEmployee: Record<string, number | null>;
}

export interface WeeklyCycleTimeReport {
  employees: string[];
  weeks: WeeklyCycleTimeWeek[];
}

export interface PrAnalyticsResponse {
  summary: PrAnalyticsSummary;
  monthlyMetrics: MonthlyPrMetrics[];
  employeeMetrics: EmployeePrMetrics[];
  repositoryMetrics: RepositoryPrMetrics[];
  cycleStartSources: CycleStartSourceMetric[];
  records: PrAnalyticsRecord[];
}

export interface PrAnalyticsFilters {
  project: string;
  repository: string;
  month: string;
  employee: string;
  jiraMatch: 'all' | 'matched' | 'unmatched';
  sprint: string;
  cycleStartSource: string;
}

export interface PrAnalyticsViewModel {
  summary: PrAnalyticsSummary;
  monthlyMetrics: MonthlyPrMetrics[];
  employeeMetrics: EmployeePrMetrics[];
  repositoryMetrics: RepositoryPrMetrics[];
  cycleStartSources: CycleStartSourceMetric[];
  weeklyCycleTime: WeeklyCycleTimeReport;
  records: PrAnalyticsRecord[];
}
