// 서비스 주기 화면(BookingFrequency) 테스트: 두 조회 중 하나(주기 설명 문구)만 실패했을 때
// 오류 화면의 '다시 시도' 가 실패한 쪽을 다시 불러 화면이 뜨는지, 정기청소 회차 안내가 서버 회차 수를 쓰는지 본다. 서버는 가짜(test/fakeHttp.js)다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingFrequency from './BookingFrequency.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Booking/Frequency', () => {
  beforeEach(() => sessionStorage.clear());

  it('확인 문구 조회만 실패해도 다시 시도하면 화면이 뜬다 (자체 점검 #35)', async () => {
    const { __fake } = await import('../../api/http.js');
    // failOnce: 가짜 서버가 이 요청만 딱 한 번 네트워크 오류로 실패시킨다 — 다시 시도하면 성공한다
    __fake.failOnce.add('GET /catalog/categories/home/confirm-copy');
    render(
      <MemoryRouter initialEntries={['/booking/home/frequency']}>
        <Routes>
          <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '다시 시도' }));
    expect(await screen.findByRole('heading', { name: '서비스 주기' })).toBeInTheDocument();
  });

  // 운영자가 정기청소 회차 수를 3으로 바꿨다(서버 confirm-copy 의 seriesTermMonths = 3) →
  // 주기 안내가 앱 고정 '6개월 동안 6회' 가 아니라 '3개월 동안 3회' 를 말한다 (문제 목록 25번)
  it('정기청소 회차 안내는 서버가 내려 준 회차 수를 쓴다', async () => {
    const { __fake } = await import('../../api/http.js');
    __fake.seriesTermMonths = 3;
    render(
      <MemoryRouter initialEntries={['/booking/home/frequency']}>
        <Routes>
          <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText(/3개월 동안 3회 방문해요/)).toBeInTheDocument();
    expect(screen.getByText(/주말에도 선택한 날짜를 유지해요/)).toBeInTheDocument();
    expect(screen.queryByText(/주말이면 다음 평일로/)).toBeNull();
    expect(screen.queryByText(/6개월 동안 6회/)).toBeNull();
    __fake.seriesTermMonths = 6; // 이 파일은 가짜 서버 상태를 이어 쓰므로 되돌린다
  });

  // 2026-10-31 시안: '기본 청소' 카드형 주기 ('정기 서비스는?' 카드는 2026-11-03 뺐다). 날짜/시간 선택 섹션은 지우지 않고 hidden 으로 숨긴다
  it('시안 화면을 그리고 날짜/시간 선택 섹션은 숨긴다', async () => {
    render(
      <MemoryRouter initialEntries={['/booking/home/frequency']}>
        <Routes>
          <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByRole('heading', { name: '기본 청소' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /정기/ })).toHaveAttribute('aria-checked', 'true');
    // 요금표 전환(2026-11-13): '10%할인' 은 요금표와 맞지 않아 가정·원룸에서 뺐다 (매장 등은 그대로 — 아래 테스트)
    expect(screen.queryByRole('radio', { name: /10%할인/ })).toBeNull();
    expect(screen.queryByText(/청연 VIP 라운지/)).toBeNull(); // 2026-11-03: '10%할인' 만 남김
    expect(screen.queryByRole('heading', { name: '정기 서비스는?' })).toBeNull();
    expect(screen.queryByRole('heading', { name: '날짜/시간 선택' })).toBeNull(); // hidden 안이라 접근성 트리에서 빠진다
    expect(screen.getByText('날짜/시간 선택').closest('[hidden]')).not.toBeNull();
  });

  // 2026-11-08 요금·견적 안내: 홈에서 고른 종류(가정/원룸)·분해청소에 맞는 안내, 매장은 없음
  const at = (path) => render(
    <MemoryRouter initialEntries={[path]}><Routes>
      <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
    </Routes></MemoryRouter>,
  );
  it('카테고리에 맞는 요금 안내를 보인다', async () => {
    at('/booking/home/frequency');
    expect(await screen.findByRole('heading', { name: '가정 정기청소' })).toBeInTheDocument();
    expect(screen.getByText('329,000')).toBeInTheDocument();
  });
  it('원룸을 고르고 왔으면 원룸 요금을 보인다', async () => {
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ homeService: 'studio' }));
    at('/booking/home/frequency');
    expect(await screen.findByRole('heading', { name: '원룸 정기청소' })).toBeInTheDocument();
    expect(screen.getByText('첫 달 199,000원')).toBeInTheDocument();
  });
  it('분해청소는 후드 크기별 요금, 매장은 안내 없음', async () => {
    const { unmount } = at('/booking/appliance/frequency');
    expect(await screen.findByRole('heading', { name: '후드·덕트 분해청소' })).toBeInTheDocument();
    expect(screen.getByText('200,000')).toBeInTheDocument();
    unmount();
    at('/booking/store/frequency');
    await screen.findByRole('heading', { name: '서비스 주기' });
    expect(screen.queryByText(/요금 안내|견적 안내/)).toBeNull();
  });
});

describe('요금표 요금제 고르기 (2026-11-13)', () => {
  beforeEach(() => sessionStorage.clear());
  const at = (category) => render(
    <MemoryRouter initialEntries={[`/booking/${category}/frequency`]}><Routes>
      <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
      <Route path="/booking/:category/address" element={<p>주소 화면</p>} />
    </Routes></MemoryRouter>,
  );
  const draftOf = (category) => JSON.parse(sessionStorage.getItem(`ansimclean.booking.${category}`));

  it('가정 정기는 주 1·2·3회와 한 달 금액을 고르고, 4회 이상은 상담 안내', async () => {
    at('home');
    expect(await screen.findByRole('radio', { name: /주 1회.*329,000원/ })).toHaveAttribute('aria-checked', 'true'); // 기본 주 1회
    expect(screen.getByText('주 4회 이상은 상담으로 안내해 드려요')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: /주 3회.*한 달 12회 방문 · 889,000원/ }));
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('주소 화면')).toBeInTheDocument();
    expect(draftOf('home')).toMatchObject({ frequency: 'recurring', visitsPerWeek: 3 });
  });

  it('원룸 주 1회는 첫 달 금액을 함께 보이고, 1회를 고르면 주 N회 칸이 사라진다', async () => {
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ homeService: 'studio' }));
    at('home');
    expect(await screen.findByRole('radio', { name: /주 1회.*229,000원 · 첫 달 199,000원/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: '1회' }));
    expect(screen.queryByRole('radio', { name: /주 1회/ })).toBeNull();
    expect(screen.queryByRole('dialog')).toBeNull(); // 원룸 1회는 체험이 아니다 — 휴대폰 인증 안내 없음
  });

  it('원룸 1회는 체험 이력·인증과 상관없이 늘 고를 수 있다', async () => {
    const api = await import('../../api/customer.js');
    const spy = vi.spyOn(api, 'getFirstCleaningEligibility').mockResolvedValue({ verified: false, eligible: false, reason: 'already_used', completedOneTime: true });
    sessionStorage.setItem('ansimclean.booking.home', JSON.stringify({ homeService: 'studio', frequency: 'oneTime' }));
    at('home');
    expect(await screen.findByRole('radio', { name: '1회' })).toHaveAttribute('aria-checked', 'true');
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('주소 화면')).toBeInTheDocument();
    spy.mockRestore();
  });

  it('분해청소는 주기 대신 후드 크기를 고르고, 고르기 전에는 다음이 잠긴다', async () => {
    sessionStorage.setItem('ansimclean.booking.appliance', JSON.stringify({ frequency: 'recurring' }));
    at('appliance');
    expect(await screen.findByRole('radiogroup', { name: '후드 크기' })).toBeInTheDocument();
    expect(screen.queryByRole('radiogroup', { name: '서비스 주기' })).toBeNull();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.click(screen.getByRole('radio', { name: /900mm.*150,000원/ }));
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('주소 화면')).toBeInTheDocument();
    expect(draftOf('appliance')).toMatchObject({ frequency: 'oneTime', hoodSize: '900' });
  });

  it('매장 정기는 예전처럼 10%할인 문구', async () => {
    at('store');
    expect(await screen.findByRole('radio', { name: /10%할인/ })).toBeInTheDocument();
    expect(screen.queryByRole('radiogroup', { name: '주 몇 회 방문할까요?' })).toBeNull();
  });
});

describe('첫 1회 청소 안내', () => {
  beforeEach(() => { sessionStorage.clear(); vi.restoreAllMocks(); });
  const renderFrequency = (category = 'home') => render(<MemoryRouter initialEntries={[`/booking/${category}/frequency`]}><Routes><Route path="/booking/:category/frequency" element={<BookingFrequency />} /></Routes></MemoryRouter>);
  it('미인증 신규 고객은 1회 선택 때 안내를 열고, 인증 버튼은 휴대폰 인증 화면으로 간다', async () => {
    const api = await import('../../api/customer.js');
    vi.spyOn(api, 'getFirstCleaningEligibility').mockResolvedValue({ verified: false, eligible: false, reason: 'phone_required', completedOneTime: false });
    render(<MemoryRouter initialEntries={['/booking/home/frequency']}><Routes>
      <Route path="/booking/:category/frequency" element={<BookingFrequency />} />
      <Route path="/booking/:category/phone-verify" element={<p>인증 화면</p>} />
    </Routes></MemoryRouter>);
    fireEvent.click(await screen.findByRole('radio', { name: '1회' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '닫기' }).at(-1));
    expect(screen.queryByRole('dialog')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '다음' })); // 미인증이면 다음 단계 대신 안내가 다시 뜬다
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '휴대폰 인증하기' }));
    expect(await screen.findByText('인증 화면')).toBeInTheDocument();
  });
  it.each(['home', 'store'])('완료 이력이 있으면 %s의 1회 카드를 숨긴다', async category => {
    const api = await import('../../api/customer.js');
    vi.spyOn(api, 'getFirstCleaningEligibility').mockResolvedValue({ verified: false, eligible: false, reason: 'already_used', completedOneTime: true });
    renderFrequency(category);
    await screen.findByRole('radio', { name: /정기/ });
    expect(screen.queryByRole('radio', { name: '1회' })).toBeNull();
  });
});
