// 예약 플로우 7단계 '주의사항·요청사항' 화면(BookingNotesRequests.jsx) 테스트.
// 가입 때 받은 정보로 미리 켜 둔 주의사항 칩이 고객이 건드리지 않아도 예약 초안에 남는지 확인한다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingNotesRequests from './BookingNotesRequests.jsx';

// 목: 실제 서버 대신 가짜 서버(test/fakeHttp.js)가 api/http.js 자리에 들어간다. 이 화면이 부르는 칩 목록·추천 주의사항도 거기서 온다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Booking/NotesRequests', () => {
  // 예약 초안은 sessionStorage 에 저장된다(bookingDraft.js) — 앞 테스트의 초안이 다음 테스트로 넘어가지 않게 비운다
  beforeEach(() => sessionStorage.clear());

  // 준비: 가짜 서버의 GET /customer/profile/suggested-notices 가 { notices: ['pet'] } 을 돌려준다.
  // MemoryRouter 는 주소창 없이 메모리 안에서 경로를 바꾸는 라우터이고, '다음' 뒤 도착할 확인 화면은 문구 한 줄(<p>)로 대신한다.
  // 확인: 반려동물 칩이 켜진 채(aria-pressed=true) 시작하고, 그대로 '다음' 을 누르면 초안 notices 에 'pet' 이 저장된다.
  it('가입 때 고른 반려동물 칩은 미리 켜져 있고, 건드리지 않고 다음을 눌러도 초안에 남는다 (자체 점검 #14)', async () => {
    render(
      <MemoryRouter initialEntries={['/booking/home/notes']}>
        <Routes>
          <Route path="/booking/:category/notes" element={<BookingNotesRequests />} />
          <Route path="/booking/:category/confirm" element={<p>확인 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const pet = await screen.findByRole('button', { name: /반려동물이 있어요/ });
    expect(pet).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('확인 화면')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('ansimclean.booking.home')).notices).toEqual(['pet']);
  });
});
