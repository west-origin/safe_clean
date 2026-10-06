// 근무 일정 관리(Availability.jsx) 화면 테스트: 근무 불가 카드의 '삭제' 가 서버에 DELETE 를 보내고 카드를 목록에서 지우는지.
// MemoryRouter: 브라우저 주소창 없이 메모리 안에서 도는 라우터 — 시작 주소를 initialEntries 로 정한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Availability from './Availability.jsx';

// api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — 실제 네트워크 없이 mock.js 값으로 응답하고, 받은 요청을 __fake.calls 에 적는다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('근무 일정 관리 — 근무 불가', () => {
  it('근무 불가 카드에 삭제가 있고 누르면 서버에서 지운 뒤 목록에서 사라진다', async () => {
    render(
      <MemoryRouter initialEntries={['/schedule/availability']}>
        <Routes>
          <Route path="/schedule/availability" element={<Availability />} />
        </Routes>
      </MemoryRouter>,
    );
    // 가짜 서버 목록의 첫 근무 불가(9월 18일 개인 일정) 카드의 삭제 버튼을 aria-label 로 찾는다.
    // 삭제 요청이 끝난 뒤에야 카드가 빠지므로, waitFor 로 버튼이 사라질 때까지 되풀이 확인한다
    const remove = await screen.findByRole('button', { name: '9월 18일 개인 일정 삭제' });
    fireEvent.click(remove);
    await waitFor(() => expect(screen.queryByRole('button', { name: '9월 18일 개인 일정 삭제' })).toBeNull());
    // 화면에서 사라진 것만이 아니라 서버에 DELETE 요청이 실제로 갔는지도 본다
    const { __fake } = await import('../../api/http.js');
    expect(__fake.calls.find((c) => c.method === 'DELETE' && c.path.startsWith('/manager/availability/unavailable/'))).toBeTruthy();
  });
});
