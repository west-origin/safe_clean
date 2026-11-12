// Lucide: UserRoundCheck. Preserve the existing display size and inherited state colors.
import { UserRoundCheck as LucideIcon } from 'lucide-react';

export default function AssignDoneIcon({ size = 18, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
