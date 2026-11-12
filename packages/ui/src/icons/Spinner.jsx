import { LoaderCircle } from 'lucide-react';
import styles from './Spinner.module.css';

// Keep the loading announcement and rotation used by both apps.
export default function Spinner({ size = 22, className }) {
  return <LoaderCircle size={size} strokeWidth={2} role="img" aria-label="로딩 중" focusable="false" className={[styles.spinner, className].filter(Boolean).join(' ')} />;
}
