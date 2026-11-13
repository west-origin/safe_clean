// 선물함 (코드에서 정함, 2026-11-13 친구·선물 5회차 — Figma 에 화면 없음. 들어오는 길: 설정 › 친구·선물 › 선물함, 선물 화면의 뒤로, 금액권 푸시)
// 위: 선물하기 버튼. 탭 3개: 받은 선물 / 보낸 선물 / 내 금액권(?tab=received|sent|vouchers). 선물 줄의 '보기' → 선물 한 건 화면.
// 푸시를 받으면(선물 도착·결과) 목록을 바로 다시 받는다. 문구는 mock.js GIFTS_COPY, 데이터는 api/gifts.js.
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, Card, Button, UnderlineTabs, PersonRow, EmptyState, ScreenError, ScreenLoading, usePolling } from '@ansimclean/ui';
import { getGifts, getVouchers } from '../../api/gifts.js';
import { onPushReceived } from '../../push/pushClient.js';
import { GIFTS_COPY as C } from '../../api/mock.js';
import VoucherItem from './VoucherItem.jsx';
import { personLabel } from './giftLabels.js';
import styles from './gifts.module.css';

const TABS = ['received', 'sent', 'vouchers'];

/** 선물함 화면 */
export default function GiftBox() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = TABS.includes(params.get('tab')) ? params.get('tab') : 'received';
  const { data, error, reload } = usePolling(async () => {
    const [received, sent, vouchers] = await Promise.all([getGifts('received'), getGifts('sent'), getVouchers()]);
    return { received: received.gifts, sent: sent.gifts, vouchers: vouchers.vouchers };
  }, [], { subscribe: onPushReceived });

  const navBar = <NavBar title={C.boxTitle} onBack={() => navigate('/settings')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  // 받기 전 선물 수를 탭에 붙인다 (받은 선물 탭은 처리할 것이 있다는 표시)
  const waiting = data.received.filter((g) => g.status === 'sent').length;
  const options = TABS.map((key) => ({
    key,
    label: key === 'received' && waiting ? `${C.tabs.received} ${waiting}` : key === 'vouchers' && data.vouchers.length ? `${C.tabs.vouchers} ${data.vouchers.length}` : C.tabs[key],
  }));
  const rows = data[tab];

  return (
    <ScreenLayout navBar={navBar} gap="md">
      <Card tone="subtle" gap="sm">
        <p className={`text-body-s ${styles.plain}`}>{C.boxIntro}</p>
        <Button chevron={false} onClick={() => navigate('/gifts/new')}>{C.sendCta}</Button>
      </Card>

      <UnderlineTabs options={options} value={tab} onChange={(key) => setParams({ tab: key }, { replace: true })} ariaLabel={C.boxTitle} />
      {rows.length === 0 ? (
        <EmptyState title={C.empty[tab].title} description={C.empty[tab].description} />
      ) : tab === 'vouchers' ? (
        <div className={styles.list}>
          {rows.map((v) => (
            <VoucherItem
              key={v.id}
              voucher={v}
              onGift={() => navigate(`/gifts/new?voucher=${encodeURIComponent(v.id)}`)}
              onRefund={() => navigate(`/vouchers/${encodeURIComponent(v.id)}/refund`)}
            />
          ))}
        </div>
      ) : (
        <div className={styles.list}>
          {rows.map((g) => (
            <PersonRow
              key={g.id}
              title={g.label}
              subtitle={`${g.amountLabel} · ${personLabel(g.person)} · ${C.status[g.status]}`}
              actions={[{ label: C.view, tone: g.status === 'sent' && tab === 'received' ? 'primary' : 'neutral', ariaLabel: `${g.label} ${C.view}`, onClick: () => navigate(`/gifts/${encodeURIComponent(g.id)}`) }]}
            />
          ))}
        </div>
      )}
    </ScreenLayout>
  );
}
