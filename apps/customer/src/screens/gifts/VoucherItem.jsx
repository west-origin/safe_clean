// 선물함 › 내 금액권 탭의 금액권 한 장 (코드에서 정함 — GiftBox.jsx 가 그린다). ui 프리미티브(Card·SummaryRow·InfoNote·Button)만 조립한다.
// 보여 주는 것: 이름, 남은 금액, 유효기간, 30일 안 만료 경고, 선물 중·환불 접수 상태. 버튼: 선물하기(선물할 수 있을 때) · 환불(보유 중일 때).
import { Card, SummaryRow, InfoNote, Button } from '@ansimclean/ui';
import { GIFTS_COPY as C } from '../../api/mock.js';
import styles from './gifts.module.css';

/**
 * @param {object} props
 * @param {{ id: string, label: string, balanceLabel: string, status: 'held'|'gifting'|'refund_requested', validUntilLabel: string|null, giftable: boolean, expiringSoon: boolean, blockedReason: string|null }} props.voucher 서버 VoucherView
 * @param {() => void} props.onGift 선물하기 (선물할 수 있을 때만 버튼이 보인다)
 * @param {() => void} props.onRefund 환불 화면으로 (보유 중일 때만 버튼이 보인다)
 */
export default function VoucherItem({ voucher: v, onGift, onRefund }) {
  const state = C.voucherStatus[v.status]; // 선물 중 | 환불 접수 | (보유면 없음)
  return (
    <Card gap="sm">
      <p className={`text-title-card ${styles.plain}`}>{v.label}</p>
      <SummaryRow label={C.balance} value={v.balanceLabel} emphasis="card" size="sm" />
      {v.validUntilLabel && <p className={`text-body-s ${styles.plain}`}>{v.validUntilLabel}</p>}
      {state && <p className={`text-caption-l ${styles.plain}`}>{state}</p>}
      {v.expiringSoon && v.status === 'held' && <InfoNote tone="warning" glyph="!" layout="inline">{C.expiringSoon}</InfoNote>}
      {v.status === 'held' && (
        <div className={styles.buttons}>
          {v.giftable && (
            <Button variant="outline" chevron={false} onClick={onGift} aria-label={`${v.label} ${C.giftThis}`}>{C.giftThis}</Button>
          )}
          <Button variant="outlineGray" chevron={false} onClick={onRefund}>{C.refund}</Button>
        </div>
      )}
    </Card>
  );
}
