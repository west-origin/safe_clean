// Lucide: CircleCheck. Preserve the existing display size and inherited state colors.
import { CircleCheck as LucideIcon } from 'lucide-react';

export default function SuccessCheckIcon({ size = 56, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
