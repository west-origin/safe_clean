// 관리자 › 공지 관리 (코드에서 정함, rbac.md §15-23): 목록(대상별 묶음) → 새 공지 올리기 / 기존 공지 고치기·삭제.
// 서버 대신 test/fakeHttp.js — 테스트마다 목록을 mock.js ADMIN_NOTICES(매니저 대상 2개)로 되돌린다. 검사 문구는 서버와 같다.
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import AdminHome from './AdminHome.jsx';
import AdminNotices from './AdminNotices.jsx';
import AdminNoticeEdit from './AdminNoticeEdit.jsx';
import { session } from '../../api/session.js';
import { ADMIN_NOTICES } from '../../api/mock.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
const { __fake, ApiError } = await import('../../api/http.js');

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin" element={<AdminHome />} />
        <Route path="/admin/notices" element={<AdminNotices />} />
        <Route path="/admin/notices/:id" element={<AdminNoticeEdit />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('관리자 공지 관리', () => {
  beforeEach(() => {
    session.set({ accessToken: 'e30.eyJhYWwiOiJhYWwyIn0.sig', refreshToken: 'r', expiresIn: 3600, user: { id: 'a1', role: 'admin', name: '운영자', profileComplete: true, signupStep: null } });
    __fake.adminNotices = ADMIN_NOTICES.map((n) => ({ ...n }));
    __fake.calls.length = 0;
  });
  afterEach(() => {
    __fake.override = null;
  });

  it('관리자 홈의 "공지 관리" 에서 목록으로 가고, 목록은 대상별 묶음으로 보인다', async () => {
    renderAt('/admin');
    fireEvent.click(await screen.findByRole('button', { name: '공지 관리' }));
    const managers = await screen.findByRole('region', { name: '매니저' });
    expect(within(managers).getByRole('button', { name: '추석 연휴 정산 일정 안내 · 2026.09.10' })).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: '고객' })).not.toBeInTheDocument(); // 공지가 없는 대상은 묶음을 그리지 않는다
  });

  it('새 공지는 제목·내용을 다 써야 올릴 수 있고, 올리면 고객 묶음에 생긴다 — 멱등 키도 함께 간다', async () => {
    renderAt('/admin/notices');
    fireEvent.click(await screen.findByRole('button', { name: '공지 올리기' }));
    const submit = await screen.findByRole('button', { name: '올리기' });
    expect(submit).toBeDisabled();
    expect(screen.getByRole('tab', { name: '고객' })).toHaveAttribute('aria-selected', 'true'); // 처음 대상은 고객
    fireEvent.change(screen.getByLabelText('제목'), { target: { value: '설 연휴 안내' } });
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('내용'), { target: { value: '연휴에도 예약할 수 있어요' } });
    fireEvent.click(submit);
    const customers = await screen.findByRole('region', { name: '고객' });
    expect(within(customers).getByRole('button', { name: '설 연휴 안내 · 2026.11.12' })).toBeInTheDocument();
    const call = __fake.calls.find((c) => c.method === 'POST' && c.path === '/admin/notices');
    expect(call.body).toEqual({ audience: 'customer', title: '설 연휴 안내', body: '연휴에도 예약할 수 있어요' });
    expect(call.idempotencyKey).toBe('key-1');
  });

  it('기존 공지를 열면 값이 채워져 있고, 대상을 바꿔 저장하면 그 묶음으로 옮겨진다', async () => {
    renderAt('/admin/notices/mn2');
    expect(await screen.findByLabelText('제목')).toHaveValue('실시간 촬영 장비 점검 안내');
    expect(screen.getByLabelText('내용')).toHaveValue('9월 5일까지 촬영 장비 배터리와 네트워크 상태를 앱에서 확인해주세요.');
    fireEvent.click(screen.getByRole('tab', { name: '전체' }));
    fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
    const all = await screen.findByRole('region', { name: '전체' });
    expect(within(all).getByRole('button', { name: '실시간 촬영 장비 점검 안내 · 2026.09.01' })).toBeInTheDocument(); // 날짜는 그대로
    expect(__fake.calls.find((c) => c.method === 'PUT').body.audience).toBe('all');
  });

  it('삭제는 확인 시트를 거쳐 목록에서 사라진다', async () => {
    renderAt('/admin/notices/mn1');
    fireEvent.click(await screen.findByRole('button', { name: '삭제하기' }));
    const sheet = await screen.findByRole('dialog', { name: '이 공지를 삭제할까요?' });
    fireEvent.click(within(sheet).getByRole('button', { name: '삭제' }));
    await screen.findByRole('region', { name: '매니저' });
    expect(screen.queryByRole('button', { name: /추석 연휴 정산 일정 안내/ })).not.toBeInTheDocument();
  });

  it('서버 검사 문구는 버튼 위에 그대로 보이고 화면에 남는다', async () => {
    __fake.override = (method, path) => {
      if (method === 'POST' && path === '/admin/notices') throw new ApiError(400, 'VALIDATION', '공지 대상을 골라주세요');
      return undefined;
    };
    renderAt('/admin/notices/new');
    fireEvent.change(await screen.findByLabelText('제목'), { target: { value: '제목' } });
    fireEvent.change(screen.getByLabelText('내용'), { target: { value: '본문' } });
    fireEvent.click(screen.getByRole('button', { name: '올리기' }));
    expect(await screen.findByText('공지 대상을 골라주세요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '올리기' })).toBeInTheDocument();
  });
});
