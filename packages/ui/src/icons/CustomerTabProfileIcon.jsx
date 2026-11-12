// Lucide: UserRound. Preserve the existing display size and inherited state colors.
import { UserRound as LucideIcon } from 'lucide-react';

export default function CustomerTabProfileIcon({ size = 30, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
