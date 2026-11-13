// 선물·금액권 화면 테스트 (코드에서 정한 화면, 2026-11-13 친구·선물 5회차). 서버는 test/fakeGifts.js —
// 받은 선물 g-1(f-1, 받기 전), 보낸 선물 g-2(f-2 가 받음), 금액권 v-1(선물·사용 가능, 카드 환불 가능)·v-2(사용함, 30일 안 만료). 친구는 f-1·f-2.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import GiftBox from './GiftBox.jsx';
import GiftSend from './GiftSend.jsx';
import GiftDetail from './GiftDetail.jsx';
import GiftPayment from './GiftPayment.jsx';
import VoucherRefund from './VoucherRefund.jsx';
import { __fake } from '../../api/http.js';
import * as payments from '../../api/payments.js';
import { friendState } from '../../test/fakeFriends.js';
import { giftState } from '../../test/fakeGifts.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
vi.mock('../../api/payments.js', () => ({ openPayment: vi.fn(), getPayment: vi.fn(), confirmPayment: vi.fn() }));

/** 지금 주소 (화면 이동 확인용) */
function Where() {
  const l = useLocation();
  return <p data-testid="where">{l.pathname + l.search}</p>;
}

const renderAt = (path) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/gifts" element={<GiftBox />} />
        <Route path="/gifts/new" element={<GiftSend />} />
        <Route path="/gifts/:id" element={<GiftDetail />} />
        <Route path="/gifts/:id/payment" element={<GiftPayment />} />
        <Route path="/vouchers/:id/refund" element={<VoucherRefund />} />
        <Route path="/friends" element={<p>친구 화면</p>} />
      </Routes>
      <Where />
    </MemoryRouter>,
  );
const next = () => fireEvent.click(screen.getByRole('button', { name: '다음' }));
/** 화면 이동이 끝날 때까지 기다려 지금 주소를 확인한다 */
const expectAt = (path) => waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent(path));

beforeEach(() => {
  vi.clearAllMocks();
  __fake.friend = friendState();
  __fake.gift = giftState();
});

describe('선물함', () => {
  it('받은 선물·보낸 선물·내 금액권 탭', async () => {
    renderAt('/gifts');
    expect(await screen.findByRole('tab', { name: '받은 선물 1' })).toBeInTheDocument(); // 받기 전 1건
    expect(screen.getByText('150,000원 · 청소왕#0420 · 받기 전')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '보낸 선물' }));
    expect(screen.getByText('69,000원 · 반짝이#1004 · 받음')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '내 금액권 2' }));
    // v-1: 선물·환불 둘 다 / v-2: 사용해서 선물은 없고 환불만, 30일 안 만료 경고
    expect(screen.getByRole('button', { name: '원룸 1회 청소 (1회 2시간) 선물하기' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '후드·덕트 분해청소 600mm (일반 주방) 선물하기' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: '환불' })).toHaveLength(2);
    expect(screen.getByText('유효기간이 30일 안 남았어요')).toBeInTheDocument();
  });
});

describe('선물하기', () => {
  it('새로 구매: 친구 → 서비스 → 메시지 → 결제하고 선물하기 → 결제창', async () => {
    renderAt('/gifts/new');
    const nextButton = await screen.findByRole('button', { name: '다음' });
    expect(nextButton).toBeDisabled(); // 친구를 고르기 전
    fireEvent.click(screen.getByRole('radio', { name: /청소왕#0420/ }));
    next();
    // 첫 1회 체험은 목록에 없다
    expect(screen.queryByText(/체험/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: /원룸 1회 청소/ }));
    next();
    fireEvent.change(screen.getByLabelText('메시지 (선택)'), { target: { value: '고마워요' } });
    expect(screen.getByText('4/200')).toBeInTheDocument();
    next();
    expect(screen.getByText('청소왕#0420 (김*수)')).toBeInTheDocument();
    expect(screen.getByText('69,000원')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '결제하고 선물하기' }));
    await expectAt('/gifts/g-3');
    expect(__fake.gift.sent.at(-1)).toEqual({ recipientId: 'f-1', method: 'purchase', catalogKey: 'studio_once', message: '고마워요' });
    expect(payments.openPayment).toHaveBeenCalledWith(expect.objectContaining({ giftId: 'g-3', amount: 69000 }));
  });

  it('내 금액권: ?to·?voucher 로 미리 고르고 바로 보낸다', async () => {
    renderAt('/gifts/new?to=f-2&voucher=v-1');
    expect(await screen.findByRole('radio', { name: /반짝이#1004/ })).toHaveAttribute('aria-checked', 'true');
    next();
    // 사용한 금액권(v-2)은 고를 수 없다
    expect(screen.getByRole('radio', { name: /내 금액권 선물/ })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: /원룸 1회 청소/ })).toHaveAttribute('aria-checked', 'true');
    expect(screen.queryByRole('radio', { name: /후드·덕트 분해청소 600mm/ })).not.toBeInTheDocument();
    next();
    next(); // 메시지는 비워도 된다
    fireEvent.click(screen.getByRole('button', { name: '선물 보내기' }));
    expect(await screen.findByText('친구가 선물을 받기를 기다리고 있어요.')).toBeInTheDocument();
    expect(__fake.gift.sent.at(-1)).toEqual({ recipientId: 'f-2', method: 'voucher', voucherId: 'v-1' });
    expect(__fake.gift.vouchers[0].status).toBe('gifting');
    expect(payments.openPayment).not.toHaveBeenCalled();
  });

  it('친구가 없으면 친구 추가로 안내한다', async () => {
    __fake.friend.friends = [];
    renderAt('/gifts/new');
    expect(await screen.findByText('선물할 친구가 없어요')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '친구 추가하러 가기' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/friends');
  });

  it('서버가 친구가 아니라고 거부하면 문구를 보여 준다', async () => {
    renderAt('/gifts/new?to=f-1');
    fireEvent.click(await screen.findByRole('button', { name: '다음' }));
    __fake.friend.friends = ['f-2']; // 그 사이 친구가 해제됐다
    fireEvent.click(await screen.findByRole('radio', { name: /원룸 1회 청소/ }));
    next();
    next();
    fireEvent.click(screen.getByRole('button', { name: '결제하고 선물하기' }));
    expect(await screen.findByText('친구에게만 선물할 수 있어요')).toBeInTheDocument();
  });

  it('주소로 단계를 건너뛰면 처음 단계로 돌아간다', async () => {
    renderAt('/gifts/new?step=confirm');
    expect(await screen.findByText('누구에게 보낼까요?')).toBeInTheDocument();
  });
});

describe('선물 한 건', () => {
  it('받은 선물을 받으면 금액권이 생긴다', async () => {
    renderAt('/gifts/g-1');
    expect(await screen.findByText('이사 축하해요!')).toBeInTheDocument();
    expect(screen.getByText('11월 20일 14:32까지')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '선물 받기' }));
    expect(await screen.findByText('받은 선물이에요. 내 금액권에서 확인할 수 있어요.')).toBeInTheDocument();
    expect(__fake.gift.vouchers.map((v) => v.id)).toContain('v-r1');
    expect(screen.getByRole('button', { name: '내 금액권 보기' })).toBeInTheDocument();
  });

  it('받은 선물 거절', async () => {
    renderAt('/gifts/g-1');
    fireEvent.click(await screen.findByRole('button', { name: '거절하기' }));
    expect(await screen.findByText('거절한 선물이에요.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '선물 받기' })).not.toBeInTheDocument();
  });

  it('보낸 결제 전 선물: 결제하기 · 취소하면 다시 선물하기', async () => {
    __fake.gift.gifts.unshift({ id: 'g-9', direction: 'sent', method: 'purchase', status: 'awaiting_payment', amount: 69000, label: '원룸 1회 청소 (1회 2시간)', message: null, personId: 'f-1', expiresLabel: null, voucherId: 'v-9' });
    renderAt('/gifts/g-9');
    fireEvent.click(await screen.findByRole('button', { name: '결제하기' }));
    expect(payments.openPayment).toHaveBeenCalledWith(expect.objectContaining({ giftId: 'g-9' }));
    fireEvent.click(screen.getByRole('button', { name: '선물 취소' }));
    expect(await screen.findByText('취소한 선물이에요.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '다시 선물하기' }));
    expect(screen.getByTestId('where')).toHaveTextContent('/gifts/new?to=f-1');
  });

  it('결제창에서 돌아오면 승인을 요청하고 선물 화면으로 바꿔 간다', async () => {
    payments.getPayment.mockResolvedValue({ id: 'pay-g-2', giftId: 'g-2' });
    payments.confirmPayment.mockResolvedValue({});
    renderAt('/gifts/g-2/payment?orderId=pay-g-2&paymentKey=pk&amount=69000');
    expect(await screen.findByText('친구가 선물을 받았어요.')).toBeInTheDocument();
    expect(payments.confirmPayment).toHaveBeenCalledWith('pay-g-2', 'pk', 69000);
    expect(screen.getByTestId('where')).toHaveTextContent(/^\/gifts\/g-2$/); // PG 키가 주소에 남지 않는다
  });

  it('다른 선물의 결제 주문이면 승인하지 않는다', async () => {
    payments.getPayment.mockResolvedValue({ id: 'pay-x', giftId: 'g-other' });
    renderAt('/gifts/g-2/payment?orderId=pay-x&paymentKey=pk&amount=69000');
    expect(await screen.findByText('결제 정보를 확인할 수 없어요. 내 예약에서 다시 열어주세요.')).toBeInTheDocument();
    expect(payments.confirmPayment).not.toHaveBeenCalled();
  });
});

describe('금액권 환불', () => {
  it('카드 결제 취소 환불', async () => {
    renderAt('/vouchers/v-1/refund');
    expect(await screen.findByText('카드 결제를 취소해 돌려드려요.')).toBeInTheDocument();
    expect(screen.queryByLabelText('계좌번호')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '환불 신청' }));
    await expectAt('/gifts?tab=vouchers');
    expect(__fake.gift.vouchers.find((v) => v.id === 'v-1').status).toBe('refund_requested');
  });

  it('받은 금액권은 계좌를 입력해야 신청할 수 있다', async () => {
    __fake.gift.vouchers.push({ ...__fake.gift.vouchers[0], id: 'v-r', refund: { eligible: true, method: 'bank', rate: 100, amount: 150000, amountLabel: '150,000원', reason: null, needsAccount: true, openStatus: null } });
    renderAt('/vouchers/v-r/refund');
    const submit = await screen.findByRole('button', { name: '환불 신청' });
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('은행'), { target: { value: '국민은행' } });
    fireEvent.change(screen.getByLabelText('계좌번호'), { target: { value: '123-456-7890' } });
    fireEvent.change(screen.getByLabelText('예금주'), { target: { value: '김하늘' } });
    fireEvent.click(submit);
    await expectAt('/gifts?tab=vouchers');
    const body = __fake.calls.filter((c) => c.method === 'POST' && c.path === '/customer/vouchers/v-r/refund').at(-1).body;
    expect(body).toEqual({ bankName: '국민은행', accountNumber: '1234567890', accountHolder: '김하늘' });
  });

  it('환불할 수 없으면 이유만 보이고 신청 버튼이 없다', async () => {
    renderAt('/vouchers/v-2/refund');
    expect(await screen.findByText(/유효기간이 끝난 뒤 남은 금액이 5만 원을 넘을 때 95%/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '환불 신청' })).not.toBeInTheDocument();
    expect(within(screen.getByRole('list')).getAllByRole('listitem')).toHaveLength(3); // 환불 기준
  });
});
