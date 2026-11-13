// 예약 결제 화면의 금액권 칸 (코드에서 정함, 2026-11-13 친구·선물 5회차 — Checkout.jsx 가 그린다).
// 쓰는 금액권이 있으면: 이름·쓴 금액 + '금액권 빼기'. 없으면: '금액권 쓰기' → 시트에서 한 장 고르기 (예약당 1장 — 서버 규칙).
// 바꿀 수 있는 것은 카드 결제 승인 요청 전뿐이다(editable). 쓸 수 있는 금액권도 쓰는 금액권도 없으면 아무것도 그리지 않는다.
import { useState } from 'react';
import { Card, SummaryRow, Button, Sheet, RadioOption, ActionError, useAction } from '@ansimclean/ui';
import { applyVoucher, removeVoucher } from '../../api/gifts.js';
import { GIFTS_COPY as C } from '../../api/mock.js';
import styles from '../gifts/gifts.module.css';

/**
 * @param {object} props
 * @param {string} props.bookingId
 * @param {{ voucherId: string|null, label: string|null, appliedLabel: string }} props.voucher 이 예약에 쓰는 금액권 (GET …/voucher 응답)
 * @param {{ id: string, label: string, balanceLabel: string, validUntilLabel: string|null }[]} props.vouchers 지금 쓸 수 있는 내 금액권 (usable)
 * @param {boolean} props.editable 카드 결제 승인 요청 전이라 바꿀 수 있나
 * @param {() => void} props.onChanged 쓰거나 뺀 뒤 — 결제 화면이 새 금액으로 다시 준비한다
 */
export default function CheckoutVoucher({ bookingId, voucher, vouchers, editable, onChanged }) {
  const [picking, setPicking] = useState(false);
  const [chosen, setChosen] = useState(null);
  const change = useAction(async (fn) => {
    await fn();
    setPicking(false);
    onChanged();
  });
  const applied = Boolean(voucher?.voucherId);
  if (!applied && (!editable || vouchers.length === 0)) return null;

  return (
    <Card gap="sm">
      <p className={`text-title-card ${styles.plain}`}>{C.voucherSection}</p>
      {applied ? (
        <SummaryRow label={C.voucherApplied(voucher.label)} value={`-${voucher.appliedLabel}`} tone="brand" size="sm" />
      ) : null}
      {editable && applied && (
        <Button variant="outlineGray" chevron={false} loading={change.loading} onClick={() => change.run(() => removeVoucher(bookingId))}>{C.removeVoucher}</Button>
      )}
      {editable && !applied && (
        <Button variant="outline" chevron={false} onClick={() => { change.reset(); setChosen(vouchers[0].id); setPicking(true); }}>{C.useVoucher}</Button>
      )}
      <p className={`text-body-s ${styles.plain}`}>{C.perBooking}</p>
      {!picking && <ActionError error={change.error} fallback={C.voucherFailed} />}

      <Sheet open={picking} onClose={() => setPicking(false)} title={C.pickVoucher}>
        <div className={styles.list} role="radiogroup" aria-label={C.pickVoucher}>
          {vouchers.map((v) => (
            <RadioOption key={v.id} label={v.label} description={C.voucherLine(v)} selected={chosen === v.id} onSelect={() => setChosen(v.id)} />
          ))}
        </div>
        <ActionError error={change.error} fallback={C.voucherFailed} />
        <Button loading={change.loading} disabled={!chosen} onClick={() => change.run(() => applyVoucher(bookingId, chosen))}>{C.applyVoucher}</Button>
        <Button variant="outlineGray" chevron={false} onClick={() => setPicking(false)}>{C.close}</Button>
      </Sheet>
    </Card>
  );
}
