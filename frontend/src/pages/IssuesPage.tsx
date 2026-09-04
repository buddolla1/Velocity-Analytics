import { IssuesTable } from '../components/IssuesTable';
import type { JiraIssue } from '../types/analytics';

interface IssuesPageProps {
  issues: JiraIssue[];
}

export function IssuesPage({ issues }: IssuesPageProps) {
  return <IssuesTable data={issues} />;
}
