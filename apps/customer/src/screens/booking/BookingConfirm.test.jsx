// 예약 확인 화면(BookingConfirm) 테스트: "완료" 를 막는 조건(일정·주소 없음), 요금 행(견적) 표시,
// 예약 생성 요청(본문·멱등 키)과 초안 삭제, "수정하기" 로 한 단계만 고치고 돌아오는 흐름, 실패 문구 모양(.error)을 본다.
// 서버는 가짜(test/fakeHttp.js)다. 초안은 sessionStorage 에 직접 넣어 앞 단계를 지나온 상태를 만든다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingConfirm from './BookingConfirm.jsx';
import BookingOptions from './BookingOptions.jsx';
import styles from './BookingConfirm.module.css';

// 가짜 서버는 멱등 키를 늘 'key-1' 로 만든다 — 아래 예약 생성 테스트의 키 확인이 이 값에 기댄다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const DRAFT_KEY = 'ansimclean.booking.home'; // bookingDraft.js 가 쓰는 home 카테고리 초안 키
// 확인 화면을 연다. 옵션 화면만 진짜 화면이고(수정하기 → 다음 → 돌아오기 확인용), 나머지 이동 대상은 글자만 있는 가짜 화면이다
function renderConfirm() {
  return render(
    <MemoryRouter initialEntries={['/booking/home/confirm']}>
      <Routes>
        <Route path="/booking/:category/confirm" element={<BookingConfirm />} />
        <Route path="/booking/:category/complete" element={<p>완료 화면</p>} />
        <Route path="/booking/:category/address" element={<p>주소 입력 화면</p>} />
        <Route path="/booking/:category/options" element={<BookingOptions />} />
        <Route path="/booking/:category/notes" element={<p>요청사항 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Booking/Confirm 검증', () => {
  beforeEach(() => sessionStorage.clear()); // 테스트마다 빈 초안에서 시작

  it('일정이 없으면 "완료" 가 비활성이고 행에 안내가 보이며 요금 행은 없다 (완료 후 뒤로 돌아온 빈 초안)', async () => {
    renderConfirm();
    expect(await screen.findByRole('button', { name: '예약 확정하기' })).toBeDisabled();
    expect(screen.getByText('날짜와 시간을 선택해주세요')).toBeInTheDocument();
    expect(screen.queryByText('요금')).toBeNull();
    // 고르지 않은 옵션·주의사항·요청사항은 빈 칸 대신 "없어요" (전체 점검 G6)
    expect(screen.getByText('선택한 옵션이 없어요')).toBeInTheDocument();
    expect(screen.getByText('해당하는 주의사항이 없어요')).toBeInTheDocument();
    expect(screen.getByText('남긴 요청사항이 없어요')).toBeInTheDocument();
  });

  // 일정은 있고 reuseAddress 'no' 인데 새 주소가 없는 초안 → 기존 주소(SAVED_ADDRESS)가 보이지 않고,
  // 주소 "수정하기" 가 주소 검색 화면으로 가는지 본다
  it('"아니요(새 주소)" 를 골랐는데 주소가 없으면 기존 주소를 쓰지 않고 완료를 막는다', async () => {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ date: '2026-12-01', time: '10:00', reuseAddress: 'no' }));
    renderConfirm();
    expect(await screen.findByText('주소를 입력해주세요')).toBeInTheDocument();
    expect(screen.queryByText('부산광역시 중앙대로 2127-23')).toBeNull();
    expect(screen.getByRole('button', { name: '예약 확정하기' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '방문 장소 및 일정 수정하기' }));
    fireEvent.click(screen.getByRole('button', { name: '주소 수정하기' }));
    expect(await screen.findByText('주소 입력 화면')).toBeInTheDocument();
  });

  // 정기청소 + 유료 옵션(pet)이 든 초안 → 요금·회차 날짜를 확인하고 "완료" 를 누른다.
  // 그 뒤 완료 화면으로 갔는지, 초안이 지워졌는지, 가짜 서버가 받은 요청(__fake.calls)의 키·본문을 본다.
  // 청소 예약은 수리 종류(repairType)를 보내지 않는다 — 수리는 따로 수리 예약 플로우(/repair/booking)가 만든다
  // 요금표 전환(2026-11-13): 가정 주 1회(월) = 4주 4회 329,000원 + 반려동물 배변 청소 3,000원 × 4회
  it('가정 정기면 주 N회·요일·회차 날짜와 한 달 결제 금액이 보이고, 완료는 서버에 초안과 멱등 키를 보내 완료 화면으로 간다', async () => {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ frequency: 'recurring', visitsPerWeek: 1, weekdays: [0], date: '2026-11-02', time: '10:00', reuseAddress: 'no', roadAddress: '서울 강남구 테헤란로 1', detailAddress: '3층', paidOptions: ['pet'] }));
    renderConfirm();
    expect(await screen.findByText('341,000원')).toBeInTheDocument();
    expect(screen.getByText('총 결제금액')).toBeInTheDocument();
    expect(screen.getByText('주 1회 · 4주 4회')).toBeInTheDocument();
    expect(screen.getByText('↻ 매주 월 · 4주')).toBeInTheDocument();
    expect(screen.getByText('한 달 4회를 한 번에 결제해요')).toBeInTheDocument();
    expect(screen.getByText('+3,000원 × 4회')).toBeInTheDocument(); // 유료 옵션은 회차마다
    expect(screen.queryByText('수리 종류')).toBeNull();
    expect(screen.getByText('2026년 11월 23일 (월)')).toBeInTheDocument(); // 4회차
    expect(screen.queryByText('2026년 11월 30일 (월)')).toBeNull();
    expect(screen.queryByText('결제는 청소 완료 후 진행돼요')).toBeNull();
    const done = screen.getByRole('button', { name: '예약 확정하기' });
    expect(done).toBeEnabled();
    fireEvent.click(done);
    expect(await screen.findByText('완료 화면')).toBeInTheDocument();
    expect(sessionStorage.getItem(DRAFT_KEY)).toBeNull();
    const { __fake } = await import('../../api/http.js');
    const create = __fake.calls.find((c) => c.method === 'POST' && c.path === '/customer/bookings');
    expect(create.idempotencyKey).toBe('key-1');
    expect(create.body).toMatchObject({ category: 'home', frequency: 'recurring', homeType: 'house', visitsPerWeek: 1, weekdays: [0], roadAddress: '서울 강남구 테헤란로 1', paidOptions: ['pet'], saveAddress: true });
    expect(create.body).not.toHaveProperty('repairType');
  });

  // 가짜 서버가 기본 주소 조회에 404 를 주게 하고, 1회 청소 초안으로 연다.
  // __fake 의 state 는 이 파일 안에서 이어지므로 이 설정은 뒤 테스트에도 남는다 (마지막 테스트는 주소를 확인하지 않는다)
  it('기존 주소가 없어도(404) 화면은 열리고 1회 청소 요금은 방문 1회로 보인다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.hasDefaultAddress = false; // GET /customer/addresses/default → 404
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ frequency: 'oneTime', date: '2026-11-02', time: '10:00', reuseAddress: 'yes' }));
    renderConfirm();
    expect(await screen.findByText('주소를 입력해주세요')).toBeInTheDocument();
    expect(await screen.findByText('50,000원')).toBeInTheDocument(); // 가정 첫 1회 체험
    expect(screen.queryByText(/회 총/)).toBeNull();
    expect(screen.getByRole('button', { name: '예약 확정하기' })).toBeDisabled();
  });

  // 선택 옵션 "수정하기" → 옵션 화면(?returnTo=확인 화면) → "다음" 을 누르면 요청사항 화면이 아니라 확인 화면으로 오는지 본다
  it('"수정하기" 로 한 단계만 고치면 뒤 단계를 지나지 않고 확인 화면으로 돌아온다', async () => {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ frequency: 'recurring', weekdays: [0], date: '2026-11-02', time: '10:00', reuseAddress: 'yes' }));
    renderConfirm();
    fireEvent.click(await screen.findByRole('button', { name: '선택 옵션 수정하기' }));
    fireEvent.click(await screen.findByRole('button', { name: '다음' })); // 옵션 화면의 "다음"
    expect(await screen.findByRole('button', { name: '예약 확정하기' })).toBeInTheDocument(); // notes 가 아니라 confirm
    expect(screen.queryByText('요청사항 화면')).toBeNull();
  });

  // 예약 생성이 통신 오류로 한 번 실패하면 버튼 아래 실패 문구가 이 화면의 .error(위 여백·가운데 정렬)로 그려진다
  it('"완료" 가 실패하면 실패 문구를 화면의 .error 모양(위 여백·가운데 정렬)으로 보인다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.failOnce.add('POST /customer/bookings');
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ frequency: 'oneTime', date: '2026-11-02', time: '10:00', reuseAddress: 'no', roadAddress: '서울 강남구 테헤란로 1', detailAddress: '3층' }));
    renderConfirm();
    const done = await screen.findByRole('button', { name: '예약 확정하기' });
    await waitFor(() => expect(done).toBeEnabled()); // 견적을 받은 뒤에야 켜진다
    fireEvent.click(done);
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('네트워크 연결을 확인해주세요');
    expect(alert).toHaveClass(styles.error);
  });
  // 가정 정기청소 첫 결제 혜택 (V075, 서버 견적이 판정): 분해청소 확인 화면에 할인 줄과 0원
  it('첫 결제 혜택이 있으면 분해청소 요금에 할인 줄이 보이고 총 결제금액은 0원', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.firstPaymentBenefit = true;
    sessionStorage.setItem('ansimclean.booking.appliance', JSON.stringify({ hoodSize: '600', paidOptions: ['panel'], date: '2026-11-02', time: '10:00', reuseAddress: 'no', roadAddress: '서울 강남구 테헤란로 1' }));
    render(
      <MemoryRouter initialEntries={['/booking/appliance/confirm']}>
        <Routes><Route path="/booking/:category/confirm" element={<BookingConfirm />} /></Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText('가정 정기청소 첫 결제 혜택')).toBeInTheDocument();
    expect(screen.getByText('-150,000원')).toBeInTheDocument(); // 130,000 + 판넬 20,000
    expect(screen.getByText('0원')).toBeInTheDocument();
    expect(screen.getByText('후드 600mm')).toBeInTheDocument();
    __fake.firstPaymentBenefit = false;
  });
  it('새 주소의 상세주소가 비어 있어도 완료할 수 있다 (단독주택, 2026-11-11)', async () => {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify({ frequency: 'oneTime', date: '2026-11-02', time: '10:00', reuseAddress: 'no', roadAddress: '서울 강남구 테헤란로 1', detailAddress: '   ' }));
    renderConfirm();
    expect(await screen.findByRole('button', { name: '예약 확정하기' })).toBeEnabled();
  });
});
