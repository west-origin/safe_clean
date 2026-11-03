// 주의사항·요청사항 화면(BookingNotesRequests) 테스트 (2026-11-01 시안, 2026-11-03 카드형·비밀번호 칸):
// 처음엔 아무것도 안 골라져 "다음" 이 꺼지고 빨간 안내가 보인다 → 세 문항 + 공동현관 있음/없음을 다 고르고 비밀번호를 넣으면 켜지고,
// "다음" 에서 서버 칩 key·답 한 줄·요청사항이 초안에 남는다. 비밀번호는 답 한 줄(요청사항으로 서버에 가는 글)에 들어가지 않는다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import BookingNotesRequests from './BookingNotesRequests.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const renderScreen = () =>
  render(
    <MemoryRouter initialEntries={['/booking/home/notes']}>
      <Routes>
        <Route path="/booking/:category/notes" element={<BookingNotesRequests />} />
        <Route path="/booking/:category/confirm" element={<p>확인 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
const radio = (group, name) => screen.getByRole('radiogroup', { name: group }).querySelector(`[role=radio]:nth-child(${name === '있음' ? 1 : 2})`);

describe('Booking/NotesRequests', () => {
  beforeEach(() => sessionStorage.clear());

  it('문항을 모두 고르고 비밀번호를 넣어야 다음으로 가고, 답이 초안에 남는다', async () => {
    renderScreen();
    await screen.findByRole('radiogroup', { name: '반려동물' });
    expect(screen.getAllByRole('radio').every((r) => r.getAttribute('aria-checked') === 'false')).toBe(true);
    expect(screen.queryByRole('radio', { name: '강아지' })).toBeNull(); // 2026-11-03: 있음/없음 두 가지
    expect(screen.queryByPlaceholderText(/예\)/)).toBeNull(); // 요청사항 예시 삭제
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    expect(screen.getByText('반려동물·영유아·무료주차·공동현관 비밀번호를 선택해 주세요.')).toBeInTheDocument();

    fireEvent.click(radio('반려동물', '있음'));
    fireEvent.click(radio('영유아(7세 이하)', '없음'));
    fireEvent.click(screen.getByRole('radio', { name: '불가능' }));
    expect(screen.queryByRole('textbox', { name: '공동현관 비밀번호' })).toBeNull(); // 있음을 골라야 칸이 생긴다
    fireEvent.click(radio('공동현관 비밀번호', '있음'));
    expect(screen.getByText('공동현관 비밀번호·현관 비밀번호를 입력해 주세요.')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '공동현관 비밀번호' }), { target: { value: '*1234#' } });
    expect(screen.getByText('현관 비밀번호를 입력해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: '현관 비밀번호' }), { target: { value: '5678' } });
    fireEvent.change(screen.getByRole('textbox', { name: '주의사항/요청사항' }), { target: { value: '안방 제외' } });
    expect(screen.queryByRole('status')).toBeNull(); // 빨간 안내가 사라졌다

    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    expect(await screen.findByText('확인 화면')).toBeInTheDocument();
    const draft = JSON.parse(sessionStorage.getItem('ansimclean.booking.home'));
    expect(draft.notices).toEqual(['pet']);
    expect(draft.noticeSummary).toBe('[방문 참고] 반려동물: 있음 · 영유아: 없음 · 무료주차: 불가능');
    expect(draft.doorCodes).toEqual({ building: 'yes', buildingCode: '*1234#', frontCode: '5678' });
    expect(draft.request).toBe('안방 제외');
  });

  it('공동현관 없음이면 현관 비밀번호만 있으면 된다', async () => {
    renderScreen();
    await screen.findByRole('radiogroup', { name: '반려동물' });
    fireEvent.click(radio('반려동물', '없음'));
    fireEvent.click(radio('영유아(7세 이하)', '없음'));
    fireEvent.click(screen.getByRole('radio', { name: '가능' }));
    fireEvent.click(radio('공동현관 비밀번호', '없음'));
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: '현관 비밀번호' }), { target: { value: '   ' } }); // 빈칸만은 안 된다
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();
    fireEvent.change(screen.getByRole('textbox', { name: '현관 비밀번호' }), { target: { value: '5678' } });
    expect(screen.getByRole('button', { name: '다음' })).toBeEnabled();
  });
});
