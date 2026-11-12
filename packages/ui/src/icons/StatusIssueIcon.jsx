// Lucide: TriangleAlert. Preserve the existing display size and inherited state colors.
import { TriangleAlert as LucideIcon } from 'lucide-react';

export default function StatusIssueIcon({ size = 18, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
