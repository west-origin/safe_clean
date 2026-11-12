import { Sheet, Button } from '@ansimclean/ui';
import { Smartphone } from 'lucide-react';
import common from './booking.module.css';
import styles from './CustomerPhoneProof.module.css';

// 첫 1회 청소 안내 시트. '휴대폰 인증하기' → 인증 화면(PhoneVerify, /booking/:category/phone-verify, 2026-11-10 Supabase SMS OTP).
/**
 * @param {object} props
 * @param {boolean} props.open
 * @param {() => void} props.onClose 닫기·배경·ESC
 * @param {() => void} [props.onVerify] '휴대폰 인증하기' — 인증 화면으로 이동
 */
export default function CustomerPhoneProof({ open, onClose, onVerify }) {
  return <Sheet open={open} onClose={onClose} ariaLabel="첫 1회 청소 휴대폰 인증 안내" className={styles.sheet}>
    <div className={styles.content}>
      <div className={styles.icon}><Smartphone size={44} strokeWidth={2} aria-hidden="true" /></div>
      <h2 className="text-heading-s">첫 1회 청소를 선택하시면<br />휴대폰 인증이 필요해요.</h2>
      <p className="text-body-default">서비스 이용을 위해 본인 명의의<br />휴대폰 인증을 진행해 주세요.</p>
    </div>
    <div className={styles.actions}>
      <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={onVerify}>휴대폰 인증하기</Button>
      <Button variant="secondary" chevron={false} className={common.ctaRounded} onClick={onClose}>닫기</Button>
    </div>
  </Sheet>;
}
