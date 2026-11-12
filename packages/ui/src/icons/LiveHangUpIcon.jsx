// Lucide: PhoneOff. Preserve the existing display size and inherited state colors.
import { PhoneOff as LucideIcon } from 'lucide-react';

export default function LiveHangUpIcon({ size = 24, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
