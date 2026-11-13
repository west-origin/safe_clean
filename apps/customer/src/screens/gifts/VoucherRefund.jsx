// 금액권 환불 (코드에서 정함, 2026-11-13 친구·선물 5회차 — Figma 에 화면 없음. 들어오는 길: 선물함 › 내 금액권 › 환불).
// 서버가 지금 환불하면 얼마인지(카드 결제 취소 100% | 계좌 100%·95% | 불가 이유)를 알려 주고, 계좌 환불이면 은행·계좌번호·예금주를 받는다.
// 계좌번호는 서버가 암호화해 두고 송금 후 지운다 — 앱은 저장하지 않는다. 환불 기준 문구는 사용자 환불 정책(mock.js GIFTS_COPY.refundPolicy).
import { useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import {
  ScreenLayout, NavBar, Card, SummaryRow, TextField, Button, InfoNote, ScreenError, ScreenLoading, ActionError, useAsync, useAction, showToast,
} from '@ansimclean/ui';
import { getVoucherRefund, getVouchers, requestVoucherRefund } from '../../api/gifts.js';
import { newIdempotencyKey } from '../../api/http.js';
import { GIFTS_COPY as C } from '../../api/mock.js';
import styles from './gifts.module.css';

/** 금액권 환불 화면 (/vouchers/:id/refund) */
export default function VoucherRefund() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(async () => {
    const [quote, mine] = await Promise.all([getVoucherRefund(id), getVouchers()]);
    return { quote, voucher: mine.vouchers.find((v) => v.id === id) ?? null };
  }, [id]);
  const [account, setAccount] = useState({ bankName: '', accountNumber: '', accountHolder: '' });
  const field = (name) => ({ value: account[name], onChange: (e) => setAccount((a) => ({ ...a, [name]: e.target.value })) });
  // 같은 신청을 다시 누르면 같은 키 → 환불이 두 번 접수되지 않는다. 계좌를 고치면 새 키
  // eslint-disable-next-line react-hooks/exhaustive-deps -- account 가 바뀔 때 새 키를 만드는 것이 목적이다
  const key = useMemo(() => newIdempotencyKey(), [account]);
  const submit = useAction(async () => {
    const body = data.quote.needsAccount
      ? { bankName: account.bankName.trim(), accountNumber: account.accountNumber.replace(/[\s-]/g, ''), accountHolder: account.accountHolder.trim() }
      : {};
    const res = await requestVoucherRefund(id, body, key);
    showToast(C.refundDone[res.status]);
    navigate('/gifts?tab=vouchers', { replace: true });
  });

  const navBar = <NavBar title={C.refundTitle} onBack={() => navigate('/gifts?tab=vouchers')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  const { quote, voucher } = data;
  const filled = !quote.needsAccount || (account.bankName.trim() && account.accountNumber.trim() && account.accountHolder.trim());

  return (
    <ScreenLayout
      navBar={navBar}
      gap="md"
      stickyCta={quote.eligible ? <Button disabled={!filled} loading={submit.loading} onClick={submit.run}>{C.requestRefund}</Button> : undefined}
    >
      {voucher && (
        <Card gap="sm">
          <p className={`text-title-card ${styles.plain}`}>{voucher.label}</p>
          <SummaryRow label={C.balance} value={voucher.balanceLabel} size="sm" />
          {voucher.validUntilLabel && <p className={`text-body-s ${styles.plain}`}>{voucher.validUntilLabel}</p>}
        </Card>
      )}

      {quote.openStatus ? (
        <InfoNote tone="brand">{C.openStatus[quote.openStatus]}</InfoNote>
      ) : quote.eligible ? (
        <Card tone="subtle" gap="sm">
          <SummaryRow label={C.refundAmount} value={quote.amountLabel} emphasis="heading" size="sm" />
          <p className={`text-body-s ${styles.plain}`}>{C.refundRate(quote.rate)}</p>
          <p className={`text-body-s ${styles.plain}`}>{C.refundMethod[quote.method]}</p>
        </Card>
      ) : (
        <InfoNote tone="warning" glyph="!">{quote.reason}</InfoNote>
      )}

      {quote.eligible && quote.needsAccount && (
        <div className={styles.stack}>
          <TextField label={C.bankName} placeholder={C.bankPlaceholder} {...field('bankName')} />
          <TextField label={C.accountNumber} placeholder={C.accountPlaceholder} type="tel" {...field('accountNumber')} />
          <TextField label={C.accountHolder} placeholder={C.accountHolderPlaceholder} {...field('accountHolder')} />
        </div>
      )}
      <ActionError error={submit.error} fallback={C.refundFailed} />

      <Card gap="sm">
        <p className={`text-title-card ${styles.plain}`}>{C.refundPolicyTitle}</p>
        <ul className={`text-body-s ${styles.policy}`}>
          {C.refundPolicy.map((line) => <li key={line}>{line}</li>)}
        </ul>
      </Card>
    </ScreenLayout>
  );
}
