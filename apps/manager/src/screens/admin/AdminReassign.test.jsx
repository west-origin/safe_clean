// 관리자 › 방문 취소 요청 · 배정 대기 (코드에서 정함 — backend/docs/domain.md '매니저 방문 취소 승인·재배정', 2026-11-13).
// 서버 대신 test/fakeHttp.js — 테스트마다 요청·대기 목록을 mock.js 예시(ADMIN_CANCEL_REQUESTS 1건 · ADMIN_REASSIGNMENTS 1건)로 되돌린다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import AdminHome from './AdminHome.jsx';
import AdminCancelRequests from './AdminCancelRequests.jsx';
import AdminReassignments from './AdminReassignments.jsx';
import AdminAssign from './AdminAssign.jsx';
import { session } from '../../api/session.js';
import { ADMIN_CANCEL_REQUESTS, ADMIN_REASSIGNMENTS, ADMIN_ASSIGNABLE_MANAGERS } from '../../api/mock.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
const { __fake } = await import('../../api/http.js');

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin" element={<AdminHome />} />
        <Route path="/admin/cancel-requests" element={<AdminCancelRequests />} />
        <Route path="/admin/reassignments" element={<AdminReassignments />} />
        <Route path="/admin/reassignments/:id" element={<AdminAssign />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('관리자 방문 취소 요청 · 배정 대기', () => {
  beforeEach(() => {
    session.set({ accessToken: 'e30.eyJhYWwiOiJhYWwyIn0.sig', refreshToken: 'r', expiresIn: 3600, user: { id: 'a1', role: 'admin', name: '운영자', profileComplete: true, signupStep: null } });
    __fake.adminCancelRequests = ADMIN_CANCEL_REQUESTS.map((r) => ({ ...r }));
    __fake.adminReassignments = ADMIN_REASSIGNMENTS.map((r) => ({ ...r }));
    __fake.adminAssignable = ADMIN_ASSIGNABLE_MANAGERS.map((m) => ({ ...m }));
    __fake.approveNext = 'calling';
    __fake.calls.length = 0;
  });

  it('관리자 홈에서 방문 취소 요청 목록으로 가고, 카드에 매니저·고객·주소·사유·요청 시각이 보인다', async () => {
    renderAt('/admin');
    fireEvent.click(await screen.findByRole('button', { name: '방문 취소 요청' }));
    expect(await screen.findByText('2026년 11월 20일 오전 10:00')).toBeInTheDocument();
    expect(screen.getByText('김매니저')).toBeInTheDocument();
    expect(screen.getByText('이고객')).toBeInTheDocument();
    expect(screen.getByText('서울특별시 강남구 테헤란로 1')).toBeInTheDocument();
    expect(screen.getByText('질병·부상 — 독감으로 이틀 동안 움직이기 어려워요')).toBeInTheDocument();
    expect(screen.getByText('11월 13일 오후 2:00')).toBeInTheDocument();
  });

  it('승인은 확인 시트를 거쳐 보내고, 목록에서 빠진다', async () => {
    renderAt('/admin/cancel-requests');
    fireEvent.click(await screen.findByRole('button', { name: '김매니저 2026년 11월 20일 오전 10:00 승인' }));
    const sheet = screen.getByRole('dialog');
    expect(within(sheet).getByText('매니저의 배정이 풀리고 고객에게 다른 매니저를 찾는다고 알려요. 점수는 매기지 않아요.')).toBeInTheDocument();
    fireEvent.click(within(sheet).getByRole('button', { name: '승인하기' }));
    expect(await screen.findByText('기다리는 취소 요청이 없어요')).toBeInTheDocument();
    expect(__fake.calls.some((c) => c.method === 'POST' && c.path === '/admin/cancel-requests/cancel-1/approve')).toBe(true);
  });

  it('거절은 메모(선택)를 함께 보내고, 이미 처리된 요청이면 서버 문구를 시트 안에 보인다', async () => {
    renderAt('/admin/cancel-requests');
    fireEvent.click(await screen.findByRole('button', { name: '김매니저 2026년 11월 20일 오전 10:00 거절' }));
    const sheet = screen.getByRole('dialog');
    const note = within(sheet).getByRole('textbox', { name: '메모 (선택)' });
    expect(note).toHaveAttribute('maxlength', '500');
    fireEvent.change(note, { target: { value: '  통화했어요  ' } });
    __fake.adminCancelRequests = []; // 다른 어드민이 먼저 처리했다
    fireEvent.click(within(sheet).getByRole('button', { name: '거절하기' }));
    expect(await within(sheet).findByRole('alert')).toHaveTextContent('이미 처리된 취소 요청이에요');

    __fake.adminCancelRequests = ADMIN_CANCEL_REQUESTS.map((r) => ({ ...r }));
    fireEvent.click(within(sheet).getByRole('button', { name: '거절하기' }));
    expect(await screen.findByText('기다리는 취소 요청이 없어요')).toBeInTheDocument();
    const sent = __fake.calls.filter((c) => c.path === '/admin/cancel-requests/cancel-1/reject').at(-1);
    expect(sent.body).toEqual({ note: '통화했어요' });
  });

  it('배정 대기 카드에는 예약 ID·고객·연락처·주소·취소한 매니저·요청/승인 시각과 사유가 보인다', async () => {
    renderAt('/admin');
    fireEvent.click(await screen.findByRole('button', { name: '배정 대기' }));
    expect(await screen.findByText('2026년 11월 18일 오전 10:00')).toBeInTheDocument();
    expect(screen.getByText('재배정 콜을 수락한 매니저 없음')).toBeInTheDocument();
    expect(screen.getByText('job-2')).toBeInTheDocument();
    expect(screen.getByText('박고객')).toBeInTheDocument();
    expect(screen.getByText('010-1234-5678')).toBeInTheDocument();
    expect(screen.getByText('서울특별시 강남구 역삼로 10 101동 1203호')).toBeInTheDocument();
    expect(screen.getByText('최매니저')).toBeInTheDocument();
    expect(screen.getByText('개인 사정')).toBeInTheDocument();
    expect(screen.getByText('11월 13일 오후 1:00')).toBeInTheDocument(); // 요청 04:00Z = 서울 13:00 (어댑터가 글자로)
    expect(screen.getByText('11월 13일 오후 2:00')).toBeInTheDocument(); // 승인
  });

  it('매니저를 골라야 배정할 수 있고, 근무시간 밖 매니저는 표시된다 — 배정하면 대기 목록에서 빠진다', async () => {
    renderAt('/admin/reassignments');
    fireEvent.click(await screen.findByRole('button', { name: '2026년 11월 18일 오전 10:00 매니저 배정' }));
    const submit = await screen.findByRole('button', { name: '배정하기' });
    expect(submit).toBeDisabled();
    expect(screen.getByText('근무시간 밖')).toBeInTheDocument(); // 한매니저
    fireEvent.click(screen.getByRole('radio', { name: /정매니저/ }));
    fireEvent.click(submit);
    expect(await screen.findByText('배정을 기다리는 예약이 없어요')).toBeInTheDocument();
    const sent = __fake.calls.find((c) => c.method === 'POST' && c.path === '/admin/bookings/job-2/assign');
    expect(sent.body).toEqual({ managerId: 'mgr-b' });
  });

  it('배정할 수 있는 매니저가 없으면 안내만 보이고 배정 버튼이 없다', async () => {
    __fake.adminAssignable = [];
    renderAt('/admin/reassignments/job-2');
    expect(await screen.findByText('배정할 수 있는 매니저가 없어요')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '배정하기' })).toBeNull();
  });
});
