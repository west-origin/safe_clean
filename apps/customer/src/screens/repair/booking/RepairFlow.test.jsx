// 수리 예약 플로우 화면 테스트: 가전 → 증상 이동, 가전수리 외 카테고리의 6단계 진행, 사진 업로드와 초안 저장,
// 24시간 지난 사진(만료) 처리, 현장 상황 선택 → 확인 → 예약 생성 요청 본문 → 완료 화면 → 초안 삭제까지를 실제 화면 컴포넌트로 확인한다.
// 서버는 test/fakeHttp.js(메모리 안의 가짜 서버)로 바꿔, 보낸 요청은 __fake.calls 로 읽는다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import RepairAppliance from './RepairAppliance.jsx';
import RepairSymptom from './RepairSymptom.jsx';
import RepairDetail from './RepairDetail.jsx';
import RepairNotes from './RepairNotes.jsx';
import RepairConfirm from './RepairConfirm.jsx';
import RepairComplete from './RepairComplete.jsx';
import { __fake } from '../../../api/http.js';
import { loginWithPhone, cancelReservation } from '../../../api/customer.js';
import { setBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, PHOTO_TTL_MS } from './repairFlow.js';

// vi.mock 은 import 보다 먼저 적용되므로, 위에서 http.js 로부터 가져온 __fake 도 이 가짜 서버의 기록이다
vi.mock('../../../api/http.js', async () => (await import('../../../test/fakeHttp.js')).fakeHttpModule());

// entry 주소에서 시작하는 메모리 라우터로 수리 예약 화면들을 띄운다. 주소 화면·수리 탭·내 예약은 글자만 있는 대역이다
function renderAt(entry) {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/repair" element={<p>수리 탭</p>} />
        <Route path="/reservations" element={<p>내 예약</p>} />
        <Route path="/repair/booking/:category/appliance" element={<RepairAppliance />} />
        <Route path="/repair/booking/:category/symptom" element={<RepairSymptom />} />
        <Route path="/repair/booking/:category/detail" element={<RepairDetail />} />
        <Route path="/repair/booking/:category/address" element={<p>주소 화면</p>} />
        <Route path="/repair/booking/:category/notes" element={<RepairNotes />} />
        <Route path="/repair/booking/:category/confirm" element={<RepairConfirm />} />
        <Route path="/repair/booking/:category/complete" element={<RepairComplete />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('수리 예약 플로우 (Figma rGCjx3zWMBk18fXgNZ9UYX)', () => {
  // 테스트마다 초안(sessionStorage)·세션(localStorage)·요청 기록을 비우고 가짜 서버에 다시 로그인한다
  beforeEach(async () => {
    sessionStorage.clear();
    localStorage.clear();
    __fake.calls.length = 0;
    await loginWithPhone({ phone: '12345678', password: 'password1' });
  });

  it('가전 종류 → 증상: 고르기 전엔 다음이 막히고, 고르면 증상 화면에 "가전수리 · 냉장고"', async () => {
    renderAt('/repair/booking/appliance/appliance');
    expect(await screen.findByRole('heading', { name: '어떤 가전을 수리할까요?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.click(screen.getByRole('radio', { name: '냉장고' }));
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByRole('heading', { name: '어떤 증상이 있나요?' })).toBeInTheDocument();
    expect(screen.getByText('가전수리 · 냉장고')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: '냉장이 잘 안 돼요' })).toBeInTheDocument();
  });

  it('가전수리 외 카테고리는 증상 상세부터 시작하고 진행 표시는 6단계', async () => {
    renderAt('/repair/booking/plumbing/detail');
    expect(await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' })).toBeInTheDocument();
    expect(screen.getAllByText('배관·누수').length).toBeGreaterThan(0); // 머리글 + 요약 카드
    expect(screen.getByRole('progressbar', { name: '6단계 중 1단계' })).toBeInTheDocument();
  });

  // 가전·증상을 고른 초안을 넣고 → 파일 입력에 사진 한 장을 넣으면 → 업로드 요청, 요약 카드 문구, 초안의 photos 1개를 확인한다
  it('사진을 고르면 서버에 올리고 요약 카드가 "1장 첨부됨" 이 된다', async () => {
    setBookingDraft(draftKey('appliance'), { appliance: 'fridge', symptom: 'cooling' });
    renderAt('/repair/booking/appliance/detail');
    await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' });
    expect(screen.getByText('없음')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('사진 파일 선택'), { target: { files: [new File(['x'], 'a.jpg', { type: 'image/jpeg' })] } });
    expect(await screen.findByText('1장 첨부됨')).toBeInTheDocument();
    expect(__fake.calls.some((c) => c.method === 'POST' && c.path === '/customer/repair-photos')).toBe(true);
    const saved = JSON.parse(sessionStorage.getItem('ansimclean.booking.repair:appliance')).photos;
    expect(saved).toHaveLength(1);
    expect(typeof saved[0].uploadedAt).toBe('number'); // 올린 시각(밀리초) — 24시간이 지나면 초안에서 뺀다
  });

  // 올린 지 24시간이 지난 사진(미리보기 주소가 만료됐고 서버 정리가 곧 지운다)은 초안에서 빼고 서버와 같은 만료 문구를 보인다
  it('증상 상세: 24시간 지난 사진은 초안에서 빼고 만료 문구를 보인다', async () => {
    const now = Date.now();
    setBookingDraft(draftKey('appliance'), {
      appliance: 'fridge', symptom: 'cooling',
      photos: [
        { id: 'p/old.jpg', path: 'p/old.jpg', src: 'blob:old', uploadedAt: now - 25 * 60 * 60 * 1000 },
        { id: 'p/new.jpg', path: 'p/new.jpg', src: 'blob:new', uploadedAt: now - 60 * 1000 },
      ],
    });
    renderAt('/repair/booking/appliance/detail');
    await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' });
    expect(screen.getByText('1장 첨부됨')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^사진 \d+ 삭제$/ })).toHaveLength(1);
    expect(await screen.findByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.repair:appliance')).photos.map((p) => p.path)).toEqual(['p/new.jpg']);
    // 새 사진을 올리면 안내를 거둔다
    fireEvent.change(screen.getByLabelText('사진 파일 선택'), { target: { files: [new File(['x'], 'a.jpg', { type: 'image/jpeg' })] } });
    expect(await screen.findByText('2장 첨부됨')).toBeInTheDocument();
    expect(screen.queryByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeNull();
  });

  // 서버가 예약할 때 사진이 이미 정리됐다고(만료) 답하면: 초안 사진을 비우고 증상 상세로 보내 다시 올리게 한다. 다시 '다음' 을 누르면 확인 화면으로 돌아온다
  it('확인 화면: 서버가 사진 만료로 막으면 초안 사진을 비우고 증상 상세로 안내한다', async () => {
    __fake.expiredPhotos.add('p/gone.jpg');
    setBookingDraft(draftKey('appliance'), {
      appliance: 'fridge', symptom: 'cooling', roadAddress: '서울특별시 강남구 테헤란로 123', date: '2026-11-04', time: '11:00',
      photos: [{ id: 'p/gone.jpg', path: 'p/gone.jpg', src: 'blob:gone', uploadedAt: Date.now() - 60 * 1000 }],
      submitKey: 'photo-expired-key', // 가짜 서버의 고정 멱등 키('key-1')가 다른 테스트의 예약과 겹치지 않게
    });
    renderAt('/repair/booking/appliance/confirm');
    await screen.findByRole('heading', { name: '예약내용을 확인해주세요' });
    expect(screen.getByText('사진 1장 첨부')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '예약하기' }));
    expect(await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' })).toBeInTheDocument();
    expect(screen.getByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeInTheDocument();
    expect(screen.getByText('없음')).toBeInTheDocument(); // 요약 카드의 사진: 없음
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.repair:appliance')).photos).toEqual([]);
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByRole('heading', { name: '예약내용을 확인해주세요' })).toBeInTheDocument();
    expect(screen.getByText('사진 없음')).toBeInTheDocument();
  });

  // 24시간 지난 사진이 든 초안으로 확인 화면을 열면: 요약에 'N장 첨부' 로 세지 않고, 곧바로 그 사진만 빼고 증상 상세로 보내 만료 안내를 보인다.
  // 예전에는 요약이 만료된 사진까지 세다가 '예약하기' 를 눌러야 되돌아왔다 (합친 38건 검토 R10)
  it('확인 화면: 24시간 지난 사진이 있으면 열자마자 증상 상세로 보내 안내하고 예약은 보내지 않는다', async () => {
    setBookingDraft(draftKey('appliance'), {
      appliance: 'fridge', symptom: 'cooling', roadAddress: '서울특별시 강남구 테헤란로 123', date: '2026-11-04', time: '11:00',
      photos: [
        { id: 'p/old.jpg', path: 'p/old.jpg', src: 'blob:old', uploadedAt: Date.now() - 25 * 60 * 60 * 1000 },
        { id: 'p/new.jpg', path: 'p/new.jpg', src: 'blob:new', uploadedAt: Date.now() - 60 * 1000 },
      ],
      submitKey: 'stale-photo-key', // 잘못 보내더라도 다른 테스트의 예약('key-1')과 멱등 키가 겹치지 않게
    });
    renderAt('/repair/booking/appliance/confirm');
    expect(await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' })).toBeInTheDocument();
    expect(screen.getByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeInTheDocument();
    expect(screen.getByText('1장 첨부됨')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.repair:appliance')).photos.map((p) => p.path)).toEqual(['p/new.jpg']);
    expect(__fake.calls.some((c) => c.method === 'POST' && c.path === '/customer/bookings')).toBe(false);
    // '다음' 을 누르면 확인 화면으로 돌아오고, 요약은 남은 사진만 센다
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByRole('heading', { name: '예약내용을 확인해주세요' })).toBeInTheDocument();
    expect(screen.getByText('사진 1장 첨부')).toBeInTheDocument();
  });

  // 증상 상세를 연 채로 24시간 경계를 넘기면: 사진을 조용히 감추지 않고 초안에서 빼며 만료 안내를 켠다 (합친 38건 검토 R10).
  // 올린 지 '24시간 - 0.3초' 인 사진을 넣어, 화면이 떠 있는 동안 만료되게 한다
  it('증상 상세: 화면을 연 채 24시간이 지나면 그 사진을 초안에서 빼고 만료 안내를 켠다', async () => {
    setBookingDraft(draftKey('appliance'), {
      appliance: 'fridge', symptom: 'cooling',
      photos: [
        { id: 'p/soon.jpg', path: 'p/soon.jpg', src: 'blob:soon', uploadedAt: Date.now() - PHOTO_TTL_MS + 300 },
        { id: 'p/new.jpg', path: 'p/new.jpg', src: 'blob:new', uploadedAt: Date.now() - 60 * 1000 },
      ],
    });
    renderAt('/repair/booking/appliance/detail');
    await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' });
    expect(screen.getByText('2장 첨부됨')).toBeInTheDocument();
    expect(screen.queryByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeNull();
    expect(await screen.findByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeInTheDocument();
    expect(screen.getByText('1장 첨부됨')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.repair:appliance')).photos.map((p) => p.path)).toEqual(['p/new.jpg']);
  });

  // 확인 화면을 연 채 앱을 내려 두었다가 25시간 뒤에 돌아오면(visibilitychange): 요약을 'N장 첨부' 로 두지 않고 증상 상세로 보내 안내한다.
  // 백그라운드에서는 타이머가 멈출 수 있어, 돌아온 순간 다시 잰다 (합친 38건 검토 R10)
  it('확인 화면: 앱을 내려 둔 사이 24시간이 지나면 돌아왔을 때 증상 상세로 보내 안내한다', async () => {
    setBookingDraft(draftKey('appliance'), {
      appliance: 'fridge', symptom: 'cooling', roadAddress: '서울특별시 강남구 테헤란로 123', date: '2026-11-04', time: '11:00',
      photos: [{ id: 'p/a.jpg', path: 'p/a.jpg', src: 'blob:a', uploadedAt: Date.now() - 60 * 1000 }],
      submitKey: 'background-photo-key',
    });
    renderAt('/repair/booking/appliance/confirm');
    await screen.findByRole('heading', { name: '예약내용을 확인해주세요' });
    expect(screen.getByText('사진 1장 첨부')).toBeInTheDocument();
    const later = Date.now() + 25 * 60 * 60 * 1000;
    const clock = vi.spyOn(Date, 'now').mockImplementation(() => later); // 25시간 뒤로 (기기 시계)
    try {
      document.dispatchEvent(new Event('visibilitychange')); // 앱이 다시 앞으로 왔다
      expect(await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' })).toBeInTheDocument();
      expect(screen.getByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeInTheDocument();
      expect(screen.getByText('없음')).toBeInTheDocument();
      expect(__fake.calls.some((c) => c.method === 'POST' && c.path === '/customer/bookings')).toBe(false);
    } finally {
      clock.mockRestore();
    }
  });

  // 화면이 아직 다시 그리지 못한 채(타이머가 늦음) '예약하기' 를 눌러도, 누른 순간 다시 재서 만료된 사진은 보내지 않는다
  it('확인 화면: 누른 순간 24시간이 지났으면 보내지 않고 증상 상세로 안내한다', async () => {
    setBookingDraft(draftKey('appliance'), {
      appliance: 'fridge', symptom: 'cooling', roadAddress: '서울특별시 강남구 테헤란로 123', date: '2026-11-04', time: '11:00',
      photos: [{ id: 'p/a.jpg', path: 'p/a.jpg', src: 'blob:a', uploadedAt: Date.now() - 60 * 1000 }],
      submitKey: 'late-photo-key',
    });
    renderAt('/repair/booking/appliance/confirm');
    await screen.findByRole('heading', { name: '예약내용을 확인해주세요' });
    const later = Date.now() + 25 * 60 * 60 * 1000;
    const clock = vi.spyOn(Date, 'now').mockImplementation(() => later);
    try {
      fireEvent.click(screen.getByRole('button', { name: '예약하기' }));
      expect(await screen.findByRole('heading', { name: '증상을 자세히 알려주세요' })).toBeInTheDocument();
      expect(screen.getByText('첨부한 사진이 만료됐어요. 사진을 다시 올려주세요')).toBeInTheDocument();
      expect(__fake.calls.some((c) => c.method === 'POST' && c.path === '/customer/bookings')).toBe(false);
    } finally {
      clock.mockRestore();
    }
  });

  // 현장 상황 전 단계까지 채운 초안에서 시작 → 칩 두 개 선택 → 확인 화면 요약 확인 → 예약하기 →
  // 보낸 요청 본문·멱등 키, 완료 화면 값, 초안 삭제, '예약 확인' 으로 내 예약 이동까지 본다
  it('현장 상황은 중복 선택, 확인 화면은 요약과 "예약하기" → 서버에 category=repair 로 만들고 완료 화면', async () => {
    setBookingDraft(draftKey('appliance'), { appliance: 'fridge', symptom: 'cooling', detail: '냉장실 온도가 올라가요', roadAddress: '서울특별시 강남구 테헤란로 123', detailAddress: '604동 1002호', date: '2026-11-04', time: '11:00' });
    renderAt('/repair/booking/appliance/notes');
    await screen.findByRole('heading', { name: '기사님께 알려주세요' });
    fireEvent.click(screen.getByRole('button', { name: '주차 가능해요' }));
    fireEvent.click(screen.getByRole('button', { name: '엘리베이터 있어요' }));
    expect(screen.getByRole('button', { name: '주차 가능해요' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '다음' }));

    await screen.findByRole('heading', { name: '예약내용을 확인해주세요' });
    expect(screen.getByText('주차 가능 · 엘리베이터 있음')).toBeInTheDocument();
    expect(screen.getByText('2026년 11월 4일(수)')).toBeInTheDocument();
    expect(screen.getByText('오전 11시')).toBeInTheDocument();
    expect(screen.getByText('수리비는 현장 진단 후 확정돼요')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '예약하기' }));

    expect(await screen.findByRole('heading', { name: '예약이 완료되었어요!' })).toBeInTheDocument();
    const call = __fake.calls.find((c) => c.method === 'POST' && c.path === '/customer/bookings');
    expect(call.body).toMatchObject({ category: 'repair', repairType: 'appliance', notices: ['parking', 'elevator'], repair: { appliance: '냉장고', symptom: '냉장이 잘 안 돼요', detail: '냉장실 온도가 올라가요', photos: [] } }); // 기사에게는 표시 문구로
    expect(call.idempotencyKey).toBeTruthy();
    expect(screen.getByText('11월 4일(수)')).toBeInTheDocument();
    expect(screen.getByText('배정 중')).toBeInTheDocument();
    expect(sessionStorage.getItem('ansimclean.booking.repair:appliance')).toBeNull(); // 초안은 지운다
    fireEvent.click(screen.getByRole('button', { name: '예약 확인' }));
    expect(await screen.findByText('내 예약')).toBeInTheDocument();
  });

  // 가짜 서버에 '후보가 없어 예약 직후 매칭에 실패한' 수리 예약을 넣고 완료 화면을 연다 →
  // 기사 배정 행은 Figma '배정 중' 이 아니라 서버의 실패 안내이고, '배정이 완료되면 알려드릴게요' 안내는 없다.
  // 머리글 설명도 배정 알림을 약속하는 뒷문장을 빼고 Figma 문구의 첫 문장만 보인다 (합친 38건 검토 R8).
  // 배정 전(실패 아님) 예약은 지금처럼 '배정 중' 과 그 안내, Figma 설명 전체가 보인다 (문제 목록 8번)
  it('매칭에 실패한 예약이면 완료 화면이 서버의 실패 안내를 보이고 배정 완료 알림 안내·머리글 뒷문장은 숨긴다', async () => {
    const base = { category: 'repair', frequency: 'oneTime', date: '2026-11-04', time: '11:00', roadAddress: '서울특별시 강남구 테헤란로 123', detailAddress: '604동 1002호', serviceLabel: '수리 서비스', managerAssigned: false, seriesId: null, visits: [], perVisitLabel: '0원', totalAmount: 0, totalLabel: '0원' };
    __fake.bookings['b-failed'] = { ...base, id: 'b-failed', cleaner: '매니저를 찾지 못했어요 · 가능한 매니저가 생기면 다시 찾아요', matchingFailed: true };
    __fake.bookings['b-pending'] = { ...base, id: 'b-pending', cleaner: '배정 후 알려드려요', matchingFailed: false };

    const failed = renderAt('/repair/booking/appliance/complete?id=b-failed');
    expect(await screen.findByText('매니저를 찾지 못했어요 · 가능한 매니저가 생기면 다시 찾아요')).toBeInTheDocument();
    expect(screen.queryByText('배정 중')).not.toBeInTheDocument();
    expect(screen.queryByText('기사 배정이 완료되면 알림으로 바로 알려드릴게요.')).not.toBeInTheDocument();
    expect(screen.getByText('방문 일정과 주소를 확인해 주세요.')).toBeInTheDocument(); // 머리글 설명은 첫 문장만
    expect(screen.queryByText(/기사 배정 완료 시 알림으로 바로 알려드릴게요/)).not.toBeInTheDocument();
    failed.unmount();

    renderAt('/repair/booking/appliance/complete?id=b-pending');
    expect(await screen.findByText('배정 중')).toBeInTheDocument();
    expect(screen.getByText('기사 배정이 완료되면 알림으로 바로 알려드릴게요.')).toBeInTheDocument();
    expect(screen.getByText('방문 일정과 주소를 확인해 주세요. 기사 배정 완료 시 알림으로 바로 알려드릴게요.')).toBeInTheDocument(); // Figma 설명 전체
  });

  // 매칭 실패 표시 없이(콜이 나가 있거나 밤 보류 중) 취소된 수리 예약의 완료 화면을 다시 열면: 서버 매니저 칸('배정 전 취소')을 보이고
  // '배정 중'·배정 완료 알림 안내·머리글 뒷문장은 없다 — 취소 전에 매칭이 실패했는지와 관계없이 같은 화면이다 (합친 38건 검토 R16)
  it('취소된 예약이면 완료 화면이 서버의 "배정 전 취소" 를 보이고 배정 안내는 숨긴다', async () => {
    __fake.bookings['b-cancelled'] = {
      id: 'b-cancelled', category: 'repair', frequency: 'oneTime', date: '2026-11-04', time: '11:00', roadAddress: '서울특별시 강남구 테헤란로 123', detailAddress: '604동 1002호',
      serviceLabel: '수리 서비스', cleaner: '배정 전 취소', managerAssigned: false, matchingFailed: false, cancelled: true, seriesId: null, visits: [], perVisitLabel: '0원', totalAmount: 0, totalLabel: '0원',
    };
    renderAt('/repair/booking/appliance/complete?id=b-cancelled');
    expect(await screen.findByText('배정 전 취소')).toBeInTheDocument();
    expect(screen.queryByText('배정 중')).not.toBeInTheDocument();
    expect(screen.queryByText('기사 배정이 완료되면 알림으로 바로 알려드릴게요.')).not.toBeInTheDocument();
    expect(screen.getByText('방문 일정과 주소를 확인해 주세요.')).toBeInTheDocument();
  });

  // 가짜 서버로 만든 수리 예약을 '내 예약' 에서 취소한 뒤 완료 화면을 다시 연다 — 생성 응답은 cancelled=false, 취소 뒤 상세는 true (api.md getBooking)
  it('예약을 만든 뒤 취소하고 완료 화면을 다시 열면 "배정 전 취소" 가 보인다', async () => {
    setBookingDraft(draftKey('appliance'), { appliance: 'fridge', symptom: 'cooling', roadAddress: '서울특별시 강남구 테헤란로 123', date: '2026-11-05', time: '11:00', submitKey: 'cancel-then-reopen-key' });
    const created = renderAt('/repair/booking/appliance/confirm');
    await screen.findByRole('heading', { name: '예약내용을 확인해주세요' });
    fireEvent.click(screen.getByRole('button', { name: '예약하기' }));
    expect(await screen.findByText('배정 중')).toBeInTheDocument();
    const { id, cancelled } = __fake.bookings['cancel-then-reopen-key'];
    expect(cancelled).toBe(false);
    created.unmount();

    await cancelReservation(id);
    renderAt(`/repair/booking/appliance/complete?id=${id}`);
    expect(await screen.findByText('배정 전 취소')).toBeInTheDocument();
    expect(screen.queryByText('기사 배정이 완료되면 알림으로 바로 알려드릴게요.')).not.toBeInTheDocument();
  });
});
