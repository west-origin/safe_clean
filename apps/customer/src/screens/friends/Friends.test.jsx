// 친구 화면 테스트 (코드에서 정한 화면, 2026-11-13). 서버는 test/fakeFriends.js — 친구 f-1·f-2, 받은 요청 f-3(q-1), 관계 없는 f-4.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import Friends from './Friends.jsx';
import FriendSearch from './FriendSearch.jsx';
import FriendInvite from './FriendInvite.jsx';
import { __fake } from '../../api/http.js';
import { friendState, FAKE_INVITE_CODE, EXPIRED_INVITE_CODE } from '../../test/fakeFriends.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
vi.mock('./shareInvite.js', () => ({ shareInvite: vi.fn(async () => 'copied') }));

const renderAt = (path) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/friends" element={<Friends />} />
        <Route path="/friends/search" element={<FriendSearch />} />
        <Route path="/friends/invite/:code" element={<FriendInvite />} />
        <Route path="/friends/blocks" element={<p>차단 목록</p>} />
        <Route path="/gifts/new" element={<p>선물하기 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  __fake.friend = friendState();
  localStorage.clear();
});

describe('친구', () => {
  it('내 친구 코드와 탭별 목록, 받은 요청 수락', async () => {
    renderAt('/friends');
    expect(await screen.findByText('하늘#0123')).toBeInTheDocument();
    expect(screen.getByText('청소왕#0420')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '받은 요청 1' }));
    fireEvent.click(await screen.findByRole('button', { name: '깔끔쟁이#7777 수락' }));
    expect(await screen.findByRole('tab', { name: '친구 3' })).toBeInTheDocument();
    expect(__fake.friend.friends).toContain('f-3');
  });

  it('친구 줄의 선물 버튼은 그 친구를 고른 선물하기로 간다 (5회차)', async () => {
    renderAt('/friends');
    fireEvent.click(await screen.findByRole('button', { name: '청소왕#0420 선물' }));
    expect(await screen.findByText('선물하기 화면')).toBeInTheDocument();
  });

  it('관리 시트에서 차단하면 목록에서 빠진다', async () => {
    renderAt('/friends');
    fireEvent.click(await screen.findByRole('button', { name: '청소왕#0420 관리' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '차단하기' }));
    expect(await screen.findByRole('tab', { name: '친구 1' })).toBeInTheDocument();
    expect(__fake.friend.blocks).toEqual(['f-1']);
  });

  it('닉네임이 없으면 먼저 정하게 한다', async () => {
    __fake.friend.me = { nickname: null, tag: null, handle: null, needsNickname: true };
    renderAt('/friends');
    const save = await screen.findByRole('button', { name: '닉네임 정하기' });
    expect(save).toBeDisabled();
    fireEvent.change(screen.getByLabelText('닉네임'), { target: { value: '새닉#' } });
    expect(save).toBeDisabled(); // # 금지
    fireEvent.change(screen.getByLabelText('닉네임'), { target: { value: '새닉' } });
    fireEvent.click(save);
    expect(await screen.findByText('새닉#0123')).toBeInTheDocument();
  });

  it('초대하기는 링크를 공유(웹은 복사)한다', async () => {
    const { shareInvite } = await import('./shareInvite.js');
    renderAt('/friends');
    fireEvent.click(await screen.findByRole('button', { name: '카카오톡으로 초대하기' }));
    await vi.waitFor(() => expect(shareInvite).toHaveBeenCalledWith(expect.objectContaining({ url: 'https://api.test/i/MyCode0001' })));
  });
});

describe('친구 찾기', () => {
  it('닉네임#태그로 찾아 요청하고, 없으면 안내한다', async () => {
    renderAt('/friends/search');
    fireEvent.change(screen.getByLabelText('친구 코드'), { target: { value: '새친구#2026' } });
    fireEvent.click(screen.getByRole('button', { name: '찾기' }));
    fireEvent.click(await screen.findByRole('button', { name: '새친구#2026 친구 요청' }));
    expect(await screen.findByRole('button', { name: '새친구#2026 요청 보냄' })).toBeDisabled();
    const sent = __fake.calls.find((c) => c.method === 'POST' && c.path === '/customer/friends/requests');
    expect(sent.body).toEqual({ userId: 'f-4' });
    expect(sent.idempotencyKey).toBeTruthy();

    fireEvent.change(screen.getByLabelText('친구 코드'), { target: { value: '없는사람#0000' } });
    fireEvent.click(screen.getByRole('button', { name: '찾기' }));
    expect(await screen.findByText('찾는 친구가 없어요')).toBeInTheDocument();
  });

  it('나에게 요청을 보낸 사람은 찾아서 바로 수락된다', async () => {
    renderAt('/friends/search');
    fireEvent.change(screen.getByLabelText('친구 코드'), { target: { value: '깔끔쟁이#7777' } });
    fireEvent.click(screen.getByRole('button', { name: '찾기' }));
    fireEvent.click(await screen.findByRole('button', { name: '깔끔쟁이#7777 수락' }));
    expect(await screen.findByRole('button', { name: '깔끔쟁이#7777 친구' })).toBeDisabled();
  });

  it('초대 코드를 넣으면 초대 화면으로 간다', async () => {
    renderAt('/friends/search');
    fireEvent.change(screen.getByLabelText('초대 코드'), { target: { value: '123' } });
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    expect(screen.getByText('초대 코드 10자리를 확인해주세요')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('초대 코드'), { target: { value: `https://api.test/i/${FAKE_INVITE_CODE}` } });
    fireEvent.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByRole('button', { name: '친구 수락' })).toBeInTheDocument();
  });
});

describe('초대 받기', () => {
  it('수락하면 친구가 되고 기억해 둔 초대를 지운다', async () => {
    localStorage.setItem('ansimclean.pendingFriendInvite', FAKE_INVITE_CODE);
    renderAt(`/friends/invite/${FAKE_INVITE_CODE}`);
    expect(await screen.findByText(/새친구 님이/)).toBeInTheDocument();
    expect(localStorage.getItem('ansimclean.pendingFriendInvite')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: '친구 수락' }));
    expect(await screen.findByText('하늘#0123')).toBeInTheDocument(); // 친구 목록으로
    expect(__fake.friend.friends).toContain('f-4');
  });

  it('만료된 초대는 안내만 한다', async () => {
    renderAt(`/friends/invite/${EXPIRED_INVITE_CODE}`);
    expect(await screen.findByText('초대 링크를 찾을 수 없어요')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '친구 수락' })).toBeNull();
  });
});
