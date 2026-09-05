import type {
  AnalyticsFilters,
  DashboardResponse,
  DashboardSummary,
  EmployeeMetrics,
  IssueTypeMetrics,
  JiraIssue,
  MonthlyVelocity,
  SprintMetrics,
} from '../types/analytics';

const COMPLETED_STATUSES = new Set(['done', 'closed', 'resolved']);
const DEFECT_TYPES = new Set(['bug', 'defect']);
const STORY_TYPES = new Set(['story']);

export function normalizeText(value: string | null | undefined): string {
  return (value ?? '').trim();
}

export function normalizeKey(value: string | null | undefined): string {
  return normalizeText(value).toLowerCase();
}

export function formatAssignee(value: string | null | undefined): string {
  return normalizeText(value) || 'Unassigned';
}

export function isCompletedIssue(issue: JiraIssue): boolean {
  return (
    COMPLETED_STATUSES.has(normalizeKey(issue.status)) ||
    Boolean(normalizeText(issue.resolvedAt))
  );
}

export function isStoryIssue(issue: JiraIssue): boolean {
  return STORY_TYPES.has(normalizeKey(issue.issueType));
}

export function isDefectIssue(issue: JiraIssue): boolean {
  return DEFECT_TYPES.has(normalizeKey(issue.issueType));
}

function issuePoints(issue: JiraIssue): number {
  return typeof issue.storyPoints === 'number' && Number.isFinite(issue.storyPoints)
    ? issue.storyPoints
    : 0;
}

function issueCycleTime(issue: JiraIssue): number | null {
  return typeof issue.jiraCycleTimeDays === 'number' && Number.isFinite(issue.jiraCycleTimeDays)
    ? issue.jiraCycleTimeDays
    : null;
}

function getMonthGroup(issue: JiraIssue): { monthNumber: number; monthName: string } {
  const monthName = normalizeText(issue.monthName) || 'Unknown';
  const monthNumber =
    typeof issue.monthNumber === 'number' && Number.isFinite(issue.monthNumber)
      ? issue.monthNumber
      : 99;

  return { monthNumber, monthName };
}

export function filterIssues(issues: JiraIssue[], filters: AnalyticsFilters): JiraIssue[] {
  return issues.filter((issue) => {
    if (filters.project && normalizeText(issue.projectName) !== filters.project) {
      return false;
    }
    if (filters.month && normalizeText(issue.monthName) !== filters.month) {
      return false;
    }
    if (filters.sprint && normalizeText(issue.sprint) !== filters.sprint) {
      return false;
    }
    if (filters.assignee && formatAssignee(issue.assignee) !== filters.assignee) {
      return false;
    }
    if (filters.issueType && normalizeText(issue.issueType) !== filters.issueType) {
      return false;
    }
    return true;
  });
}

export function calculateSummary(issues: JiraIssue[]): DashboardSummary {
  const completedIssues = issues.filter(isCompletedIssue);
  const completedStories = completedIssues.filter(isStoryIssue);
  const completedDefects = completedIssues.filter(isDefectIssue);
  const contributors = new Set(
    completedIssues
      .map((issue) => normalizeText(issue.assignee))
      .filter((value) => value.length > 0)
  );

  const cycleTimes = completedIssues
    .map(issueCycleTime)
    .filter((value): value is number => value !== null);

  const averageCycleTimeDays =
    cycleTimes.length > 0
      ? cycleTimes.reduce((sum, value) => sum + value, 0) / cycleTimes.length
      : null;

  return {
    totalIssues: issues.length,
    storiesCompleted: completedStories.length,
    storyPointsCompleted: completedIssues.reduce((sum, issue) => sum + issuePoints(issue), 0),
    defectsClosed: completedDefects.length,
    activeContributors: contributors.size,
    averageCycleTimeDays,
  };
}

export function calculateMonthlyVelocity(issues: JiraIssue[]): MonthlyVelocity[] {
  const groups = new Map<string, MonthlyVelocity>();

  for (const issue of issues) {
    if (!isCompletedIssue(issue)) {
      continue;
    }

    const { monthNumber, monthName } = getMonthGroup(issue);
    const key = `${monthNumber}-${monthName}`;
    const current =
      groups.get(key) ?? {
        monthNumber,
        monthName,
        storiesCompleted: 0,
        storyPointsCompleted: 0,
        defectsCompleted: 0,
      };

    if (isStoryIssue(issue)) {
      current.storiesCompleted += 1;
    }

    if (isDefectIssue(issue)) {
      current.defectsCompleted += 1;
    }

    current.storyPointsCompleted += issuePoints(issue);
    groups.set(key, current);
  }

  return [...groups.values()].sort((left, right) => {
    if (left.monthNumber !== right.monthNumber) {
      return left.monthNumber - right.monthNumber;
    }
    return left.monthName.localeCompare(right.monthName, undefined, { numeric: true, sensitivity: 'base' });
  });
}

export function calculateSprintMetrics(issues: JiraIssue[]): SprintMetrics[] {
  const groups = new Map<string, JiraIssue[]>();

  for (const issue of issues) {
    const sprint = normalizeText(issue.sprint) || 'No Sprint';
    const current = groups.get(sprint) ?? [];
    current.push(issue);
    groups.set(sprint, current);
  }

  return [...groups.entries()]
    .map(([sprint, sprintIssues]) => {
      const stories = sprintIssues.filter(isStoryIssue);
      const completedStories = stories.filter(isCompletedIssue);
      const completedIssues = sprintIssues.filter(isCompletedIssue);
      const defectCount = completedIssues.filter(isDefectIssue).length;
      const contributors = new Set(
        completedIssues
          .map((issue) => normalizeText(issue.assignee))
          .filter((value) => value.length > 0)
      );

      const completedStoryPoints = completedIssues.reduce((sum, issue) => sum + issuePoints(issue), 0);

      return {
        sprint,
        totalStories: stories.length,
        completedStories: completedStories.length,
        storyPoints: completedStoryPoints,
        completionPercentage: stories.length > 0 ? (completedStories.length / stories.length) * 100 : 0,
        defects: defectCount,
        contributors: contributors.size,
      };
    })
    .sort((left, right) =>
      left.sprint.localeCompare(right.sprint, undefined, { numeric: true, sensitivity: 'base' })
    );
}

export function calculateEmployeeMetrics(issues: JiraIssue[]): EmployeeMetrics[] {
  const groups = new Map<string, JiraIssue[]>();

  for (const issue of issues) {
    if (!isCompletedIssue(issue)) {
      continue;
    }

    const employee = formatAssignee(issue.assignee);
    const current = groups.get(employee) ?? [];
    current.push(issue);
    groups.set(employee, current);
  }

  return [...groups.entries()]
    .map(([employee, employeeIssues]) => {
      const cycleTimes = employeeIssues
        .map(issueCycleTime)
        .filter((value): value is number => value !== null);
      const sprintCount = new Set(
        employeeIssues.map((issue) => normalizeText(issue.sprint)).filter((value) => value.length > 0)
      ).size;

      return {
        employee,
        storiesCompleted: employeeIssues.filter(isStoryIssue).length,
        storyPointsCompleted: employeeIssues.reduce((sum, issue) => sum + issuePoints(issue), 0),
        defectsCompleted: employeeIssues.filter(isDefectIssue).length,
        sprintCount,
        averageCycleTimeDays:
          cycleTimes.length > 0
            ? cycleTimes.reduce((sum, value) => sum + value, 0) / cycleTimes.length
            : null,
      };
    })
    .sort((left, right) =>
      right.storyPointsCompleted - left.storyPointsCompleted ||
      left.employee.localeCompare(right.employee, undefined, { numeric: true, sensitivity: 'base' })
    );
}

export function calculateIssueTypes(issues: JiraIssue[]): IssueTypeMetrics[] {
  const groups = new Map<string, number>();

  for (const issue of issues) {
    const issueType = normalizeText(issue.issueType) || 'Unknown';
    groups.set(issueType, (groups.get(issueType) ?? 0) + 1);
  }

  return [...groups.entries()]
    .map(([issueType, count]) => ({ issueType, count }))
    .sort((left, right) => right.count - left.count || left.issueType.localeCompare(right.issueType));
}

export function buildAnalytics(issues: JiraIssue[]): DashboardResponse {
  return {
    summary: calculateSummary(issues),
    monthlyVelocity: calculateMonthlyVelocity(issues),
    sprintMetrics: calculateSprintMetrics(issues),
    employeeMetrics: calculateEmployeeMetrics(issues),
    issueTypeMetrics: calculateIssueTypes(issues),
    issues,
  };
}

export function formatCycleTime(days: number | null | undefined): string {
  if (typeof days !== 'number' || !Number.isFinite(days)) {
    return '—';
  }
  return `${days.toFixed(1)} days`;
}

export function formatNumber(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '—';
  }
  return value.toLocaleString(undefined, { maximumFractionDigits: 1 });
}
