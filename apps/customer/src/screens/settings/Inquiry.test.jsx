// 1:1 문의 화면(Inquiry.jsx) 테스트: 문의를 보내면 완료 시트가 뜨고, 위쪽 '내 문의' 목록(MyInquiries)에 방금 쓴 내용과 답변이 보이는지.
// 서버는 test/fakeHttp.js 의 가짜 서버다 — 접수한 문의를 기억해 두고, 목록에서는 첫 문의를 답변 완료로 돌려준다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Inquiry from './Inquiry.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('1:1 문의', () => {
  it('접수하면 위쪽 "내 문의" 목록에 나타나고, 답변된 문의는 답변 본문을 보여준다', async () => {
    render(
      <MemoryRouter initialEntries={['/settings/inquiry']}>
        <Routes>
          <Route path="/settings/inquiry" element={<Inquiry />} />
        </Routes>
      </MemoryRouter>,
    );
    // 입력칸은 aria-label '문의 내용' 으로 찾는다. 보내기 뒤 dialog(완료 시트)가 뜨는지 먼저 본다
    fireEvent.change(await screen.findByLabelText('문의 내용'), { target: { value: '정기청소 일정을 바꾸고 싶어요' } });
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    // 목록 안에서 찾는다 — 제어되는 <textarea> 는 입력값이 글자 내용에도 들어가 화면 전체에서 찾으면 입력칸이 먼저 잡혀, 목록이 새로 오기 전에 다음 확인이 돌아 간헐 실패했다
    const list = await screen.findByRole('region', { name: '내 문의' });
    expect(await within(list).findByText('정기청소 일정을 바꾸고 싶어요')).toBeInTheDocument();
    expect(await within(list).findByText('확인했어요. 곧 도와드릴게요')).toBeInTheDocument(); // 첫 문의는 가짜 서버가 답변 완료로 준다
  });
});
