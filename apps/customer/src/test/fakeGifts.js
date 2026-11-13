// 가짜 서버의 금액권·선물 부분 (test/fakeHttp.js 가 합친다). 서버 규칙 중 화면이 의존하는 것만 흉내 낸다:
// 친구에게만 선물(state.friend.friends), 구매 선물은 결제 전(awaiting_payment) + checkout, 보유 금액권 선물은 바로 sent + 금액권 gifting,
// 받는 사람만 받기·거절, 보낸 사람만 취소, 예약당 금액권 1장(예약 금액은 BOOKING_TOTAL), 환불은 금액권마다 정해 둔 quote 를 돌려준다.
// 처음 상태: 받은 선물 g-1(f-1 이 보냄, 받기 전), 보낸 선물 g-2(f-2 가 받음), 금액권 v-1(선물·사용 가능, 카드 환불 가능)·v-2(사용한 금액권 — 선물 불가, 환불 불가)
import { ApiError } from './fakeHttp.js';
import { FRIEND_PEOPLE, GIFT_CATALOG } from '../api/mock.js';

/** 가짜 예약 금액 (금액권을 쓰면 남은 카드 결제 금액 = 이 값 − 쓴 금액) */
export const BOOKING_TOTAL = 85000;
const won = (n) => `${n.toLocaleString('ko-KR')}원`;
const person = (id) => FRIEND_PEOPLE.find((p) => p.userId === id);
const notFound = () => new ApiError(404, 'NOT_FOUND', '찾을 수 없어요');
const invalid = (message) => new ApiError(409, 'INVALID_STATE', message);

/** 금액권 한 장 (서버 VoucherView 와 같은 키 + 가짜 서버만 쓰는 refund·faceAmount) */
const voucher = (patch) => ({
  status: 'held', validUntil: '2027-11-13T03:00:00Z', validUntilLabel: '2027년 11월 13일까지', giftable: true, expiringSoon: false,
  blockedReason: null, usable: true, useBlockedReason: null, ...patch,
});

/** 선물 state 의 처음 모양 */
export function giftState() {
  return {
    vouchers: [
      voucher({ id: 'v-1', label: '원룸 1회 청소 (1회 2시간)', faceAmount: 69000, balance: 69000,
        refund: { eligible: true, method: 'card', rate: 100, amount: 69000, amountLabel: '69,000원', reason: null, needsAccount: false, openStatus: null } }),
      voucher({ id: 'v-2', label: '후드·덕트 분해청소 600mm (일반 주방)', faceAmount: 130000, balance: 30000, expiringSoon: true,
        validUntil: '2026-12-01T03:00:00Z', validUntilLabel: '2026년 12월 1일까지', giftable: false, blockedReason: '사용한 금액권은 선물할 수 없어요',
        refund: { eligible: false, method: null, rate: null, amount: 0, amountLabel: null, reason: '구매 후 7일이 지났거나 사용한 금액권은 유효기간이 끝난 뒤 남은 금액이 5만 원을 넘을 때 95%를 돌려드려요', needsAccount: false, openStatus: null } }),
    ],
    gifts: [
      { id: 'g-1', direction: 'received', method: 'purchase', status: 'sent', amount: 150000, label: '후드·덕트 분해청소 900mm (넓은 주방)',
        message: '이사 축하해요!', personId: 'f-1', expiresLabel: '11월 20일 14:32까지', voucherId: 'v-r1' },
      { id: 'g-2', direction: 'sent', method: 'voucher', status: 'accepted', amount: 69000, label: '원룸 1회 청소 (1회 2시간)',
        message: null, personId: 'f-2', expiresLabel: null, voucherId: 'v-s1' },
    ],
    bookingVoucher: {}, // bookingId → voucherId
    sent: [], // POST /customer/gifts 로 받은 요청 (테스트가 확인)
  };
}

/** fakeHttp 의 exact·patterns 에 더할 처리 함수들 (s = state.gift, 친구 관계는 state.friend) */
export function giftRoutes(state) {
  const s = () => state.gift;
  const view = (g) => ({
    id: g.id, direction: g.direction, method: g.method, status: g.status, amount: g.amount, amountLabel: won(g.amount), label: g.label,
    message: g.message, person: person(g.personId), createdAt: '2026-11-13T03:00:00Z', sentAt: g.status === 'awaiting_payment' ? null : '2026-11-13T03:00:00Z',
    expiresAt: null, expiresLabel: g.status === 'sent' ? g.expiresLabel : null, decidedAt: null,
  });
  const checkoutOf = (g) => ({
    id: `pay-${g.id}`, bookingId: null, giftId: g.id, orderId: `pay-${g.id}`, amount: g.amount, currency: 'KRW', status: 'pending',
    refundedAmount: 0, clientKey: 'test_ck_stub', orderName: '안심클린 금액권 선물', payable: true, refundStatus: null,
  });
  const gift = (id) => s().gifts.find((g) => g.id === id) ?? (() => { throw notFound(); })();
  const voucherOf = (id) => s().vouchers.find((v) => v.id === id) ?? (() => { throw notFound(); })();
  // 응답에는 가짜 서버만 쓰는 refund 를 빼고 balanceLabel 을 붙인다
  const publicVoucher = (v) => {
    const out = { ...v, balanceLabel: won(v.balance) };
    delete out.refund;
    return out;
  };
  const bookingView = (bookingId) => {
    const v = s().vouchers.find((x) => x.id === s().bookingVoucher[bookingId]);
    const applied = v ? Math.min(v.faceAmount, BOOKING_TOTAL) : 0;
    const due = BOOKING_TOTAL - applied;
    return { bookingId, voucherId: v?.id ?? null, label: v?.label ?? null, appliedAmount: applied, appliedLabel: won(applied), remainingDue: due, remainingLabel: won(due), coveredByVoucher: Boolean(v) && due === 0 };
  };
  const returnVoucher = (g) => {
    const v = s().vouchers.find((x) => x.id === g.voucherId);
    if (v) Object.assign(v, { status: 'held', giftable: true, blockedReason: null, usable: true, useBlockedReason: null });
  };

  const exact = {
    'GET /customer/gifts/catalog': () => ({ items: GIFT_CATALOG.map((i) => ({ ...i, amountLabel: won(i.amount) })), acceptDays: 7, validDays: 365 }),
    'GET /customer/vouchers': () => ({ vouchers: s().vouchers.map(publicVoucher) }),
    'POST /customer/gifts': (b) => {
      s().sent.push(b);
      if (!state.friend.friends.includes(b.recipientId)) throw invalid('친구에게만 선물할 수 있어요');
      const created = { id: `g-${s().gifts.length + 1}`, direction: 'sent', method: b.method, message: b.message ?? null, personId: b.recipientId, expiresLabel: '11월 20일 15:00까지' };
      if (b.method === 'purchase') {
        const item = GIFT_CATALOG.find((i) => i.key === b.catalogKey);
        if (!item) throw new ApiError(400, 'VALIDATION', '선물할 수 없는 상품이에요');
        Object.assign(created, { status: 'awaiting_payment', amount: item.amount, label: item.label, voucherId: `v-new-${s().gifts.length}` });
        s().gifts.unshift(created);
        return { gift: view(created), checkout: checkoutOf(created) };
      }
      const v = voucherOf(b.voucherId);
      if (!v.giftable) throw invalid(v.blockedReason);
      Object.assign(v, { status: 'gifting', giftable: false, blockedReason: '선물을 보내고 받는 사람의 결정을 기다리고 있어요', usable: false, useBlockedReason: '선물 중인 금액권은 쓸 수 없어요' });
      Object.assign(created, { status: 'sent', amount: v.faceAmount, label: v.label, voucherId: v.id });
      s().gifts.unshift(created);
      return { gift: view(created), checkout: null };
    },
  };
  const patterns = [
    ['GET', /^\/customer\/gifts\?box=(sent|received)$/, ([box]) => ({
      box,
      gifts: s().gifts.filter((g) => g.direction === box && (box === 'sent' || !['awaiting_payment', 'payment_failed'].includes(g.status))).map(view),
    })],
    ['POST', /^\/customer\/gifts\/([^/]+)\/accept$/, ([id]) => {
      const g = gift(id);
      if (g.direction !== 'received') throw notFound();
      if (g.status !== 'sent' && g.status !== 'accepted') throw invalid('이미 처리된 선물이에요');
      if (g.status === 'sent') {
        g.status = 'accepted';
        s().vouchers.unshift(voucher({ id: g.voucherId, label: g.label, faceAmount: g.amount, balance: g.amount,
          refund: { eligible: true, method: 'bank', rate: 100, amount: g.amount, amountLabel: won(g.amount), reason: null, needsAccount: true, openStatus: null } }));
      }
      return { gift: view(g), checkout: null };
    }],
    ['POST', /^\/customer\/gifts\/([^/]+)\/reject$/, ([id]) => {
      const g = gift(id);
      if (g.direction !== 'received') throw notFound();
      if (g.status !== 'sent') throw invalid('이미 처리된 선물이에요');
      g.status = 'rejected';
      return { gift: view(g), checkout: null };
    }],
    ['POST', /^\/customer\/gifts\/([^/]+)\/cancel$/, ([id]) => {
      const g = gift(id);
      if (g.direction !== 'sent') throw notFound();
      if (g.status !== 'sent' && g.status !== 'awaiting_payment') throw invalid('이미 처리된 선물이에요');
      g.status = 'cancelled';
      returnVoucher(g);
      return { gift: view(g), checkout: null };
    }],
    ['GET', /^\/customer\/gifts\/([^/?]+)$/, ([id]) => {
      const g = gift(id);
      return { gift: view(g), checkout: g.direction === 'sent' && g.status === 'awaiting_payment' ? checkoutOf(g) : null };
    }],
    ['GET', /^\/customer\/bookings\/([^/]+)\/voucher$/, ([bookingId]) => bookingView(bookingId)],
    ['POST', /^\/customer\/bookings\/([^/]+)\/voucher$/, ([bookingId], b) => {
      const v = voucherOf(b.voucherId);
      const current = s().bookingVoucher[bookingId];
      if (current && current !== v.id) throw invalid('이 예약에는 이미 다른 금액권을 쓰고 있어요');
      if (!current && !v.usable) throw invalid(v.useBlockedReason);
      s().bookingVoucher[bookingId] = v.id;
      return bookingView(bookingId);
    }],
    ['DELETE', /^\/customer\/bookings\/([^/]+)\/voucher$/, ([bookingId]) => {
      delete s().bookingVoucher[bookingId];
      return bookingView(bookingId);
    }],
    ['GET', /^\/customer\/vouchers\/([^/]+)\/refund$/, ([id]) => ({ ...voucherOf(id).refund })],
    ['POST', /^\/customer\/vouchers\/([^/]+)\/refund$/, ([id], b) => {
      const v = voucherOf(id);
      if (!v.refund.eligible) throw invalid(v.refund.reason);
      if (v.refund.needsAccount && !/^[0-9]{6,20}$/.test(String(b.accountNumber ?? '').replace(/[\s-]/g, ''))) {
        throw new ApiError(400, 'VALIDATION', '계좌번호를 숫자로 입력해주세요');
      }
      const status = v.refund.method === 'card' ? 'processing' : 'requested';
      v.status = 'refund_requested';
      v.refund = { ...v.refund, eligible: false, reason: '환불을 접수했어요. 처리되면 알려드릴게요', openStatus: status };
      return { refundId: `rf-${id}`, method: v.refund.method, status, rate: v.refund.rate, amount: v.refund.amount, amountLabel: v.refund.amountLabel };
    }],
  ];
  return { exact, patterns };
}
