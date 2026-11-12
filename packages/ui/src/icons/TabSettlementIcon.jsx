// Lucide: ReceiptText. Preserve the existing display size and inherited state colors.
import { ReceiptText as LucideIcon } from 'lucide-react';

export default function TabSettlementIcon({ size = 24, className }) {
  return <LucideIcon width={size} height={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
