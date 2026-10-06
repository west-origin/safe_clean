// 정산 화면(Settlement.jsx)의 페이지 나눔 테스트: 첫 응답에 nextBefore 가 있으면 '더 보기' 가 보이고,
// 누르면 ?before= 로 다음 페이지를 받아 목록 뒤에 붙이며, 마지막 페이지(nextBefore null)에서는 버튼이 사라지는지.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Settlement from './Settlement.jsx';

// api/http.js 를 가짜 서버(test/fakeHttp.js)로 바꾼다 — 실제 네트워크 없이 mock.js 값으로 응답한다
vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

describe('정산 목록 페이지네이션', () => {
  it('nextBefore 가 있으면 "더 보기" 가 보이고, 누르면 다음 페이지를 이어 붙인다', async () => {
    const { __fake, http } = await import('../../api/http.js');
    const first = await http.get('/manager/settlements'); // 가짜 서버의 평소 첫 페이지 응답 — 아래에서 모양만 바꿔 쓴다
    let calls = 0; // 정산 목록 요청 횟수 (첫 페이지 + 다음 페이지 = 2 이상이어야 한다)
    // override: 이 테스트만 가짜 서버 응답을 바꾼다. before= 가 붙은 요청(다음 페이지)은 한 건 + 끝(null),
    // 첫 요청은 nextBefore 를 넣어 '더 보기' 가 뜨게 한다. undefined 를 돌려주면 평소 응답
    __fake.override = (method, path) => {
      if (method === 'GET' && path.startsWith('/manager/settlements')) {
        calls += 1;
        if (path.includes('before=')) return { ...first, items: [{ id: 'st-more', date: '8월 1일', status: 'complete', title: '강서구 기본청소', amount: '70,000원' }], nextBefore: null };
        return { ...first, nextBefore: '2026-09-01T00:00:00Z' };
      }
      return undefined;
    };
    render(
      <MemoryRouter initialEntries={['/settlement']}>
        <Routes>
          <Route path="/settlement" element={<Settlement />} />
        </Routes>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '더 보기' }));
    expect(await screen.findByText('강서구 기본청소')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('button', { name: '더 보기' })).toBeNull());
    expect(calls).toBeGreaterThanOrEqual(2);
    __fake.override = null; // 다음 테스트가 평소 응답을 받게 되돌린다
  });
});
