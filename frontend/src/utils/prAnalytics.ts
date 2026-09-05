import type {
  CycleStartSourceMetric,
  EmployeePrMetrics,
  MonthlyPrMetrics,
  PrAnalyticsFilters,
  PrAnalyticsRecord,
  PrAnalyticsSummary,
  PrAnalyticsViewModel,
  RepositoryPrMetrics,
  WeeklyCycleTimeReport,
  WeeklyCycleTimeWeek,
} from '../types/prAnalytics';

const CANONICAL_CYCLE_START_SOURCES = ['BB FIRST COMMIT', 'BB PR CREATED', 'JIRA IN PROGRESS'] as const;
const FIXED_WEEK_STARTS = [
  '2026-01-01',
  '2026-01-08',
  '2026-01-15',
  '2026-01-22',
  '2026-01-29',
  '2026-02-05',
  '2026-02-12',
  '2026-02-19',
  '2026-02-26',
  '2026-03-05',
  '2026-03-12',
  '2026-03-19',
  '2026-03-26',
];

export function normalizeText(value: string | null | undefined): string {
  return (value ?? '').trim();
}

export function normalizeKey(value: string | null | undefined): string {
  return normalizeText(value).toLowerCase();
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }

  const parsed = parseDate(value);
  if (!parsed) {
    return normalizeText(value) || '—';
  }

  return parsed.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDateOnly(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }

  const parsed = parseDate(value);
  if (!parsed) {
    return normalizeText(value) || '—';
  }

  return parsed.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

export function formatNumber(value: number, digits = 1): string {
  return new Intl.NumberFormat(undefined, {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  }).format(value);
}

export function formatPercentage(value: number, digits = 1): string {
  return `${formatNumber(value, digits)}%`;
}

export function formatCycleTime(value: number | null | undefined): string {
  return typeof value === 'number' && Number.isFinite(value) ? `${formatNumber(value)} days` : '—';
}

export function parseDate(value: string | null | undefined): Date | null {
  if (!value || !value.trim()) {
    return null;
  }

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export function isMergedRecord(record: PrAnalyticsRecord): boolean {
  return normalizeKey(record.state) === 'merged';
}

export function resolveEmployeeName(record: PrAnalyticsRecord): string {
  if (record.jiraMatch && normalizeText(record.jiraAssignee)) {
    return normalizeText(record.jiraAssignee);
  }
  return normalizeText(record.authorName) || normalizeText(record.authorUsername) || 'Unassigned';
}

export function resolveRepositoryName(record: PrAnalyticsRecord): string {
  return normalizeText(record.repositoryName) || 'Unknown';
}

export function resolveMonthDescriptor(record: PrAnalyticsRecord): { monthNumber: number; monthName: string } | null {
  const mergedAt = parseDate(record.prMergedAt);
  if (!mergedAt) {
    return null;
  }

  return {
    monthNumber: mergedAt.getMonth() + 1,
    monthName: new Intl.DateTimeFormat('en-US', { month: 'long' }).format(mergedAt),
  };
}

function getCycleStart(record: PrAnalyticsRecord): Date | null {
  return parseDate(record.cycleStart) ?? parseDate(record.jiraInProgressAt) ?? parseDate(record.firstCommitAt) ?? parseDate(record.prCreatedAt);
}

function getFirstCommitToMergeDays(record: PrAnalyticsRecord): number | null {
  const start = parseDate(record.firstCommitAt);
  const end = parseDate(record.prMergedAt);
  return differenceInDays(start, end);
}

function getCreatedToMergeDays(record: PrAnalyticsRecord): number | null {
  const start = parseDate(record.prCreatedAt);
  const end = parseDate(record.prMergedAt);
  return differenceInDays(start, end);
}

function differenceInDays(start: Date | null, end: Date | null): number | null {
  if (!start || !end || end.getTime() < start.getTime()) {
    return null;
  }

  return (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24);
}

function average(values: number[]): number | null {
  if (values.length === 0) {
    return null;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export function median(values: Array<number | null | undefined>): number | null {
  const numbers = values.filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
  if (numbers.length === 0) {
    return null;
  }

  const sorted = [...numbers].sort((left, right) => left - right);
  const middle = Math.floor(sorted.length / 2);

  if (sorted.length % 2 === 1) {
    return sorted[middle];
  }

  return (sorted[middle - 1] + sorted[middle]) / 2;
}

export function filterPrRecords(records: PrAnalyticsRecord[], filters: PrAnalyticsFilters): PrAnalyticsRecord[] {
  return records.filter((record) => {
    if (filters.project && normalizeText(record.projectName) !== filters.project) {
      return false;
    }

    if (filters.repository && resolveRepositoryName(record) !== filters.repository) {
      return false;
    }

    if (filters.month) {
      const monthDescriptor = resolveMonthDescriptor(record);
      if (!monthDescriptor || monthDescriptor.monthName !== filters.month) {
        return false;
      }
    }

    if (filters.employee && resolveEmployeeName(record) !== filters.employee) {
      return false;
    }

    if (filters.jiraMatch === 'matched' && !record.jiraMatch) {
      return false;
    }

    if (filters.jiraMatch === 'unmatched' && record.jiraMatch) {
      return false;
    }

    if (filters.sprint && normalizeText(record.jiraSprint) !== filters.sprint) {
      return false;
    }

    if (filters.cycleStartSource && normalizeText(record.cycleStartSource) !== filters.cycleStartSource) {
      return false;
    }

    return true;
  });
}

export function calculatePrSummary(records: PrAnalyticsRecord[]): PrAnalyticsSummary {
  const mergedRecords = records.filter(isMergedRecord);
  const cycleTimes = mergedRecords
    .map((record) => record.cycleTimeDays)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
  const firstCommitToMerge = mergedRecords
    .map(getFirstCommitToMergeDays)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
  const createdToMerge = mergedRecords
    .map(getCreatedToMergeDays)
    .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
  const activeAuthors = new Set(mergedRecords.map(resolveEmployeeName).filter((value) => value.length > 0)).size;
  const matched = records.filter((record) => record.jiraMatch).length;

  return {
    totalPrs: records.length,
    mergedPrs: mergedRecords.length,
    jiraMatchedPrs: matched,
    unmatchedPrs: records.length - matched,
    jiraMatchPercentage: records.length === 0 ? 0 : (matched * 100) / records.length,
    averageCycleTimeDays: average(cycleTimes),
    medianCycleTimeDays: median(cycleTimes),
    averageFirstCommitToMergeDays: average(firstCommitToMerge),
    averageCreatedToMergeDays: average(createdToMerge),
    activePrAuthors: activeAuthors,
  };
}

export function calculateMonthlyPrMetrics(records: PrAnalyticsRecord[]): MonthlyPrMetrics[] {
  const grouped = new Map<number, MonthlyPrMetrics & { cycleTimes: number[] }>();

  for (const record of records) {
    if (!isMergedRecord(record)) {
      continue;
    }

    const monthDescriptor = resolveMonthDescriptor(record);
    if (!monthDescriptor) {
      continue;
    }

    const current =
      grouped.get(monthDescriptor.monthNumber) ?? {
        monthNumber: monthDescriptor.monthNumber,
        monthName: monthDescriptor.monthName,
        prsMerged: 0,
        averageCycleTimeDays: null,
        matchedPrs: 0,
        unmatchedPrs: 0,
        cycleTimes: [],
      };

    current.prsMerged += 1;
    if (record.jiraMatch) {
      current.matchedPrs += 1;
    } else {
      current.unmatchedPrs += 1;
    }

    if (typeof record.cycleTimeDays === 'number' && Number.isFinite(record.cycleTimeDays)) {
      current.cycleTimes.push(record.cycleTimeDays);
    }

    grouped.set(monthDescriptor.monthNumber, current);
  }

  return [...grouped.values()]
    .map(({ cycleTimes, ...metric }) => ({
      ...metric,
      averageCycleTimeDays: average(cycleTimes),
    }))
    .sort((left, right) => left.monthNumber - right.monthNumber);
}

export function calculateEmployeePrMetrics(records: PrAnalyticsRecord[]): EmployeePrMetrics[] {
  const grouped = new Map<string, PrAnalyticsRecord[]>();

  for (const record of records) {
    if (!isMergedRecord(record)) {
      continue;
    }

    const employee = resolveEmployeeName(record);
    const current = grouped.get(employee) ?? [];
    current.push(record);
    grouped.set(employee, current);
  }

  return [...grouped.entries()]
    .map(([employee, employeeRecords]) => {
      const cycleTimes = employeeRecords
        .map((record) => record.cycleTimeDays)
        .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
      const repositories = new Set(employeeRecords.map(resolveRepositoryName)).size;
      const jiraMatchedPrs = employeeRecords.filter((record) => record.jiraMatch).length;

      return {
        employee,
        prsMerged: employeeRecords.length,
        jiraMatchedPrs,
        averageCycleTimeDays: average(cycleTimes),
        medianCycleTimeDays: median(cycleTimes),
        repositories,
      };
    })
    .sort((left, right) => left.employee.localeCompare(right.employee, undefined, { numeric: true, sensitivity: 'base' }));
}

export function calculateRepositoryMetrics(records: PrAnalyticsRecord[]): RepositoryPrMetrics[] {
  const grouped = new Map<string, PrAnalyticsRecord[]>();

  for (const record of records) {
    if (!isMergedRecord(record)) {
      continue;
    }

    const repository = resolveRepositoryName(record);
    const current = grouped.get(repository) ?? [];
    current.push(record);
    grouped.set(repository, current);
  }

  return [...grouped.entries()]
    .map(([repository, repositoryRecords]) => {
      const cycleTimes = repositoryRecords
        .map((record) => record.cycleTimeDays)
        .filter((value): value is number => typeof value === 'number' && Number.isFinite(value));
      const matched = repositoryRecords.filter((record) => record.jiraMatch).length;
      return {
        repository,
        prsMerged: repositoryRecords.length,
        averageCycleTimeDays: average(cycleTimes),
        jiraMatchPercentage: repositoryRecords.length === 0 ? 0 : (matched * 100) / repositoryRecords.length,
      };
    })
    .sort((left, right) => left.repository.localeCompare(right.repository, undefined, { numeric: true, sensitivity: 'base' }));
}

export function calculateCycleStartSources(records: PrAnalyticsRecord[]): CycleStartSourceMetric[] {
  const mergedRecords = records.filter(isMergedRecord);
  const counts = new Map<string, number>();
  for (const source of CANONICAL_CYCLE_START_SOURCES) {
    counts.set(source, 0);
  }

  for (const record of mergedRecords) {
    const source = normalizeText(record.cycleStartSource);
    if (!source) {
      continue;
    }
    counts.set(source, (counts.get(source) ?? 0) + 1);
  }

  const total = mergedRecords.filter((record) => normalizeText(record.cycleStartSource).length > 0).length;
  return CANONICAL_CYCLE_START_SOURCES.map((source) => {
    const count = counts.get(source) ?? 0;
    return {
      source,
      count,
      percentage: total === 0 ? 0 : (count * 100) / total,
    };
  });
}

export function calculateWeeklyCycleTime(records: PrAnalyticsRecord[]): WeeklyCycleTimeReport {
  const employees = [...new Set(records.filter(isMergedRecord).map(resolveEmployeeName))].sort((left, right) =>
    left.localeCompare(right, undefined, { numeric: true, sensitivity: 'base' })
  );

  const weeks = FIXED_WEEK_STARTS.map((start, index) => buildWeekWindow(start, index + 1, employees));
  const mergedRecords = records.filter(isMergedRecord);

  for (const record of mergedRecords) {
    const mergedAt = parseDate(record.prMergedAt);
    if (!mergedAt || typeof record.cycleTimeDays !== 'number' || !Number.isFinite(record.cycleTimeDays)) {
      continue;
    }

    const employee = resolveEmployeeName(record);
    const week = weeks.find((item) => isWithinWeek(mergedAt, item.weekStart, item.weekEnd));
    if (!week) {
      continue;
    }

    const current = week._bucket.get(employee) ?? [];
    current.push(record.cycleTimeDays);
    week._bucket.set(employee, current);
  }

  return {
    employees,
    weeks: weeks.map(({ _bucket, ...week }) => ({
      ...week,
      averagesByEmployee: Object.fromEntries(
        employees.map((employee) => {
          const values = _bucket.get(employee) ?? [];
          return [employee, values.length > 0 ? average(values) : null];
        })
      ),
    })),
  };
}

export function buildPrAnalytics(records: PrAnalyticsRecord[]): PrAnalyticsViewModel {
  return {
    summary: calculatePrSummary(records),
    monthlyMetrics: calculateMonthlyPrMetrics(records),
    employeeMetrics: calculateEmployeePrMetrics(records),
    repositoryMetrics: calculateRepositoryMetrics(records),
    cycleStartSources: calculateCycleStartSources(records),
    weeklyCycleTime: calculateWeeklyCycleTime(records),
    records,
  };
}

function buildWeekWindow(startDate: string, weekNumber: number, employees: string[]): WeeklyCycleTimeWeek & { _bucket: Map<string, number[]> } {
  const start = parseDate(`${startDate}T00:00:00`) ?? new Date(startDate);
  const end = new Date(start);
  end.setDate(end.getDate() + 6);

  return {
    weekNumber,
    weekStart: formatIsoDate(start),
    weekEnd: formatIsoDate(end),
    averagesByEmployee: Object.fromEntries(employees.map((employee) => [employee, null])),
    _bucket: new Map<string, number[]>(),
  };
}

function formatIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function isWithinWeek(date: Date, weekStart: string, weekEnd: string): boolean {
  const start = parseDate(`${weekStart}T00:00:00`);
  const end = parseDate(`${weekEnd}T23:59:59`);
  if (!start || !end) {
    return false;
  }
  return date.getTime() >= start.getTime() && date.getTime() <= end.getTime();
}
