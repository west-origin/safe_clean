// 선물하기 (코드에서 정함, 2026-11-13 친구·선물 5회차 — Figma 에 화면 없음).
// 들어오는 길: 홈 하단 선물 안내 · 수리 화면 선물 배너 · 설정 › 친구·선물 › 선물하기 · 친구 목록의 '선물'(?to=친구 id) · 내 금액권의 '선물하기'(?voucher=금액권 id).
// 단계: 친구 고르기 → 선물 고르기(새로 구매 | 내 금액권) → 메시지(선택) → 확인. 단계는 주소의 ?step= 이라 휴대폰 뒤로가기가 이전 단계로 간다.
// 고른 값(draft)은 이 화면의 state 에만 있다 — 새로고침하면 처음 단계부터 다시 고른다.
// 보내기: 구매면 서버가 결제 전 선물 + 결제 주문을 만들고 토스 결제창을 연다(결제가 확정되면 서버가 선물을 보낸다). 내 금액권이면 바로 보내진다.
// 문구는 mock.js GIFTS_COPY, 데이터는 api/gifts.js · api/friends.js.
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { NavBar, ScreenError, ScreenLoading, useAsync, useAction, showToast } from '@ansimclean/ui';
import { getFriends } from '../../api/friends.js';
import { getGiftCatalog, getVouchers, sendGift } from '../../api/gifts.js';
import { openPayment } from '../../api/payments.js';
import { newIdempotencyKey } from '../../api/http.js';
import { GIFTS_COPY as C } from '../../api/mock.js';
import GiftFriendStep from './GiftFriendStep.jsx';
import GiftItemStep from './GiftItemStep.jsx';
import GiftMessageStep from './GiftMessageStep.jsx';
import GiftConfirmStep from './GiftConfirmStep.jsx';

const STEPS = ['friend', 'item', 'message', 'confirm'];

/** 선물하기 화면 (단계마다 아래 Gift*Step 컴포넌트를 그린다) */
export default function GiftSend() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const { data, error, reload } = useAsync(async () => {
    const [friends, catalog, vouchers] = await Promise.all([getFriends(), getGiftCatalog(), getVouchers()]);
    return { friends: friends.friends, catalog, vouchers: vouchers.vouchers };
  }, []);
  // 고른 값. ?voucher= 로 들어오면 '내 금액권' 방식으로 그 금액권을 미리 고른다
  const [draft, setDraft] = useState(() => ({
    to: params.get('to'),
    method: params.get('voucher') ? 'voucher' : 'purchase',
    catalogKey: null,
    voucherId: params.get('voucher'),
    message: '',
  }));
  const update = (patch) => setDraft((d) => ({ ...d, ...patch }));

  // 서버에 보낼 요청. 내용이 바뀌면 멱등 키도 새로 만든다(같은 내용을 다시 누르면 같은 키 → 선물이 두 번 생기지 않는다)
  const request = useMemo(() => ({
    recipientId: draft.to,
    method: draft.method,
    ...(draft.method === 'purchase' ? { catalogKey: draft.catalogKey } : { voucherId: draft.voucherId }),
    ...(draft.message.trim() ? { message: draft.message.trim() } : {}),
  }), [draft]);
  // eslint-disable-next-line react-hooks/exhaustive-deps -- request 가 바뀔 때 새 키를 만드는 것이 목적이다
  const key = useMemo(() => newIdempotencyKey(), [request]);

  const send = useAction(async () => {
    const res = await sendGift(request, key);
    navigate(`/gifts/${encodeURIComponent(res.gift.id)}`, { replace: true });
    if (!res.checkout) {
      showToast(C.sentToast);
      return;
    }
    // 구매 선물: 토스 결제창으로 간다. 열지 못하면 선물 화면의 '결제하기' 로 다시 열 수 있다
    try {
      await openPayment(res.checkout);
    } catch {
      showToast(C.paymentOpenFailed);
    }
  });

  const navBar = <NavBar title={C.sendTitle} onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  // 지금 단계. 앞 단계를 고르지 않았으면(새로고침·주소 직접 입력) 그 단계로 돌아간다
  const friend = data.friends.find((f) => f.user.userId === draft.to);
  const item = draft.method === 'purchase'
    ? data.catalog.items.find((i) => i.key === draft.catalogKey)
    : data.vouchers.find((v) => v.id === draft.voucherId && v.giftable);
  let step = STEPS.includes(params.get('step')) ? params.get('step') : 'friend';
  if (step !== 'friend' && !friend) step = 'friend';
  else if ((step === 'message' || step === 'confirm') && !item) step = 'item';
  const go = (next) => setParams((p) => {
    p.set('step', next);
    return p;
  });

  if (step === 'friend') {
    return <GiftFriendStep navBar={navBar} friends={data.friends} selected={draft.to} onSelect={(to) => update({ to })} onNext={() => go('item')} />;
  }
  if (step === 'item') {
    return <GiftItemStep navBar={navBar} catalog={data.catalog.items} vouchers={data.vouchers} draft={draft} onChange={update} onNext={() => go('message')} />;
  }
  if (step === 'message') {
    return <GiftMessageStep navBar={navBar} message={draft.message} onChange={(message) => update({ message })} onNext={() => go('confirm')} />;
  }
  return (
    <GiftConfirmStep
      navBar={navBar}
      friend={friend.user}
      method={draft.method}
      label={item.label}
      amountLabel={draft.method === 'purchase' ? item.amountLabel : item.balanceLabel}
      message={request.message}
      acceptDays={data.catalog.acceptDays}
      action={send}
    />
  );
}
