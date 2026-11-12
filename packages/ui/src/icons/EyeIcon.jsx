// Lucide: Eye. Preserve the existing display size and inherited state colors.
import { Eye as LucideIcon } from 'lucide-react';

export default function EyeIcon({ size = 20, className }) {
  return <LucideIcon width={size} height={(size * 19) / 20} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
