// 선물하기 4단계: 내용 확인 후 보내기 (코드에서 정함 — GiftSend.jsx 가 그린다). 구매면 '결제하고 선물하기'(토스 결제창), 내 금액권이면 '선물 보내기'.
import { ScreenLayout, Card, SummaryRow, Button, InfoNote, ActionError } from '@ansimclean/ui';
import { GIFTS_COPY as C } from '../../api/mock.js';
import styles from './gifts.module.css';

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.navBar
 * @param {{ handle: string, maskedName: string }} props.friend 받는 사람
 * @param {'purchase'|'voucher'} props.method
 * @param {string} props.label 선물 이름 (요금표 항목·금액권 이름)
 * @param {string} props.amountLabel "69,000원"
 * @param {string} [props.message] 함께 보낼 메시지 (없으면 줄을 그리지 않는다)
 * @param {number} props.acceptDays 받을 수 있는 기간(일) — 서버 선물 목록 응답
 * @param {{ run: () => void, loading: boolean, error: unknown }} props.action 보내기 (useAction)
 */
export default function GiftConfirmStep({ navBar, friend, method, label, amountLabel, message, acceptDays, action }) {
  return (
    <ScreenLayout
      navBar={navBar}
      gap="md"
      stickyCta={
        <Button loading={action.loading} onClick={action.run}>
          {method === 'purchase' ? C.payAndSend : C.send}
        </Button>
      }
    >
      <h1 className={`text-heading-m ${styles.plain}`}>{C.steps.confirm}</h1>
      <Card gap="xs">
        <SummaryRow label={C.summary.to} value={`${friend.handle} (${friend.maskedName})`} />
        <SummaryRow label={C.summary.item} value={label} />
        <SummaryRow label={C.summary.method} value={C.methodShort[method]} />
        <SummaryRow label={C.summary.amount} value={amountLabel} emphasis="card" />
      </Card>
      {message && (
        <Card tone="subtle" gap="xs">
          <p className={`text-caption-l ${styles.plain}`}>{C.summary.message}</p>
          <p className={`text-body-default ${styles.message}`}>{message}</p>
        </Card>
      )}
      <InfoNote tone="neutral">{C.confirmNote(acceptDays)}</InfoNote>
      <ActionError error={action.error} fallback={C.sendFailed} />
    </ScreenLayout>
  );
}
