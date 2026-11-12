// Lucide: UserRoundSearch. Preserve the existing display size and inherited state colors.
import { UserRoundSearch as LucideIcon } from 'lucide-react';

export default function AssignPendingIcon({ size = 18, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
