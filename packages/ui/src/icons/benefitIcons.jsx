import { Calendar, Tag, House } from 'lucide-react';

export function BenefitCalendarIcon({ size = 16, className }) {
  return <Calendar size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}

export function BenefitTagIcon({ size = 16, className }) {
  return <Tag size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}

export function BenefitHomeIcon({ size = 16, className }) {
  return <House size={size} strokeWidth={2} aria-hidden="true" focusable="false" className={className} />;
}
