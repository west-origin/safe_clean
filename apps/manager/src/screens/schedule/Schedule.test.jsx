// 수락한 작업을 다시 여는 경로 테스트: 일정 화면·홈의 일정 카드를 누르면 /job/:id(업무 상세)로 가는지.
// /job/:id 자리에는 진짜 화면 대신 '업무 상세 화면' 글자만 그려, 이동했는지만 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Schedule from './Schedule.jsx';
import Home from '../home/Home.jsx';

// api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — 실제 네트워크 없이 mock.js 값으로 응답한다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

/** path 에 element 를 띄운다. MemoryRouter: 브라우저 주소창 없이 메모리 안에서 도는 라우터 */
function renderAt(path, element) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={path} element={element} />
        <Route path="/job/:id" element={<p>업무 상세 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('수락한 작업을 다시 여는 경로', () => {
  it('일정 화면의 카드를 누르면 업무 상세로 간다', async () => {
    // 가짜 서버 GET /manager/schedule 의 오늘 첫 카드(김○○ 고객)를 이름 일부(정규식)로 찾아 누른다
    renderAt('/schedule', <Schedule />);
    fireEvent.click(await screen.findByRole('button', { name: /김○○ 고객/ }));
    expect(await screen.findByText('업무 상세 화면')).toBeInTheDocument();
  });

  it('홈의 나머지 일정 카드를 누르면 업무 상세로 간다', async () => {
    // 가짜 서버 GET /manager/home 의 '오늘의 나머지 일정' 카드(박○○ 고객)를 누른다
    renderAt('/', <Home />);
    fireEvent.click(await screen.findByRole('button', { name: /박○○ 고객/ }));
    expect(await screen.findByText('업무 상세 화면')).toBeInTheDocument();
  });
});

// 고객이 예약을 취소하면 서버가 매니저에게 푸시를 보낸다. 화면이 열려 있어도 푸시가 오면 다시 받아 옛 카드가 사라져야 한다
describe('일정 화면 갱신', () => {
  it('열려 있는 동안 푸시가 오면 다시 받아 취소된 일정을 지운다', async () => {
    const { __fake } = await import('../../api/http.js');
    renderAt('/schedule', <Schedule />);
    expect(await screen.findByRole('button', { name: /김○○ 고객/ })).toBeInTheDocument();
    try {
      __fake.scheduleItems = (await import('../../api/mock.js')).SCHEDULE.items.filter((i) => !i.customer.startsWith('김○○'));
      fireEvent(window, new CustomEvent('ansimclean:push', { detail: {} })); // 앱이 떠 있을 때 온 푸시 (pushClient.js)
      await waitFor(() => expect(screen.queryByRole('button', { name: /김○○ 고객/ })).not.toBeInTheDocument());
      expect(screen.getByRole('button', { name: /이○○ 고객/ })).toBeInTheDocument(); // 나머지 일정은 그대로
    } finally {
      __fake.scheduleItems = null;
    }
  });
});
