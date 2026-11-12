import { ChevronLeft, ChevronRight } from 'lucide-react';

export default function MonthChevronIcon({ direction = 'prev', className }) {
  const Glyph = direction === 'next' ? ChevronRight : ChevronLeft;
  return <Glyph width={7} height={12} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
