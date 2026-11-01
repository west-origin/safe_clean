// 주의사항·요청사항 화면(BookingNotesRequests) 테스트 (2026-11-01 시안):
// 처음엔 아무것도 안 골라져 "다음" 이 꺼지고 빨간 안내가 보인다 → 세 문항을 다 고르면 켜지고, "다음" 에서 서버 칩 key·답 한 줄·요청사항이 초안에 남는다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingNotesRequests from './BookingNotesRequests.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('Booking/NotesRequests', () => {
  beforeEach(() => sessionStorage.clear());

  it('세 문항을 모두 골라야 다음으로 가고, 답이 초안에 남는다', async () => {
    render(
      <MemoryRouter initialEntries={['/booking/home/notes']}>
        <Routes>
          <Route path="/booking/:category/notes" element={<BookingNotesRequests />} />
          <Route path="/booking/:category/confirm" element={<p>확인 화면</p>} />
        </Routes>
      </MemoryRouter>,
    );
    const pet = await screen.findByRole('radiogroup', { name: '반려동물' });
    expect(screen.getAllByRole('radio').every((r) => r.getAttribute('aria-checked') === 'false')).toBe(true);
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    expect(screen.getByText('반려동물·영유아·무료주차를 선택해 주세요.')).toBeInTheDocument();

    fireEvent.click(pet.querySelector('[role=radio]')); // 강아지
    fireEvent.click(screen.getAllByRole('radio', { name: '없음' })[1]); // 영유아 없음
    expect(screen.getByText('무료주차를 선택해 주세요.')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: '불가능' }));
    fireEvent.change(screen.getByRole('textbox', { name: '주의사항/요청사항' }), { target: { value: '안방 제외' } });
    expect(screen.queryByRole('status')).toBeNull(); // 빨간 안내가 사라졌다

    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('확인 화면')).toBeInTheDocument();
    const draft = JSON.parse(sessionStorage.getItem('ansimclean.booking.home'));
    expect(draft.notices).toEqual(['pet']);
    expect(draft.noticeSummary).toBe('[방문 참고] 반려동물: 강아지 · 영유아: 없음 · 무료주차: 불가능');
    expect(draft.request).toBe('안방 제외');
  });
});
