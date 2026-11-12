// 관리자 › 배너 관리 (코드에서 정함, rbac.md §15-22): 목록(순서·상태·기간) → 위로·아래로 / 새 배너 올리기 / 수정·이미지 교체 / 삭제.
// 서버 대신 test/fakeHttp.js — 처음 목록은 배너 3장(게시 중 2 · 게시 예정 1). 검사 문구는 서버와 같다.
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import AdminHome from './AdminHome.jsx';
import AdminBanners from './AdminBanners.jsx';
import AdminBannerEdit from './AdminBannerEdit.jsx';
import { session } from '../../api/session.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());
const { __fake } = await import('../../api/http.js');
const initial = __fake.adminBanners.map((b) => ({ ...b }));

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin" element={<AdminHome />} />
        <Route path="/admin/banners" element={<AdminBanners />} />
        <Route path="/admin/banners/:id" element={<AdminBannerEdit />} />
      </Routes>
    </MemoryRouter>,
  );
}

const titles = () => screen.getAllByRole('listitem').map((li) => within(li).getByText(/^\d+\. /).textContent);
const png = () => new File([new Uint8Array([137, 80, 78, 71])], 'banner.png', { type: 'image/png' });

describe('관리자 배너 관리', () => {
  beforeEach(() => {
    session.set({ accessToken: 'e30.eyJhYWwiOiJhYWwyIn0.sig', refreshToken: 'r', expiresIn: 3600, user: { id: 'a1', role: 'admin', name: '운영자', profileComplete: true, signupStep: null } });
    __fake.adminBanners = initial.map((b) => ({ ...b }));
    __fake.calls.length = 0;
    globalThis.URL.createObjectURL ??= () => 'blob:preview';
    globalThis.URL.revokeObjectURL ??= () => {};
  });

  it('관리자 홈 "배너 관리" → 고객에게 보이는 순서대로 이미지·상태·기간이 보인다', async () => {
    renderAt('/admin');
    fireEvent.click(await screen.findByRole('button', { name: '배너 관리' }));
    expect(await screen.findByRole('img', { name: '첫 청소 15,000원 쿠폰팩. 즉시 지급 · 신규 고객 웰컴' })).toBeInTheDocument();
    expect(titles()).toEqual(['1. 첫 청소 15,000원 쿠폰팩', '2. 청소는 맡기고, 일상은 여유롭게', '3. 소중한 사람에게 깨끗한 하루']);
    expect(screen.getByText('게시 예정 · 12월 1일 ~ 12월 31일')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '첫 청소 15,000원 쿠폰팩 위로' })).toBeDisabled(); // 맨 위는 더 못 올린다
    expect(screen.getByRole('button', { name: '소중한 사람에게 깨끗한 하루 아래로' })).toBeDisabled();
  });

  it('"아래로" 는 옆 배너와 자리를 바꿔 전체 순서를 보낸다', async () => {
    renderAt('/admin/banners');
    fireEvent.click(await screen.findByRole('button', { name: '첫 청소 15,000원 쿠폰팩 아래로' }));
    await screen.findByText('1. 청소는 맡기고, 일상은 여유롭게');
    expect(titles()[1]).toBe('2. 첫 청소 15,000원 쿠폰팩');
    expect(__fake.calls.find((c) => c.method === 'PUT' && c.path === '/admin/banners/order').body).toEqual({ ids: ['banner-2', 'banner-1', 'banner-3'] });
  });

  it('새 배너는 이미지와 제목이 있어야 올릴 수 있고, 올리면 맨 뒤에 붙는다', async () => {
    renderAt('/admin/banners');
    fireEvent.click(await screen.findByRole('button', { name: '배너 올리기' }));
    const submit = await screen.findByRole('button', { name: '올리기' });
    expect(submit).toBeDisabled();
    fireEvent.change(screen.getByLabelText('제목'), { target: { value: '겨울 대청소 이벤트' } });
    expect(submit).toBeDisabled(); // 이미지가 아직 없다
    fireEvent.change(screen.getByLabelText('사진 파일 선택'), { target: { files: [png()] } });
    fireEvent.change(screen.getByLabelText('게시 종료일 · 선택'), { target: { value: '2026-12-31' } });
    fireEvent.click(submit);
    await screen.findByText('4. 겨울 대청소 이벤트');
    expect(screen.getByText('게시 중 · 12월 31일까지')).toBeInTheDocument();
    const form = __fake.calls.find((c) => c.method === 'POST' && c.path === '/admin/banners').body;
    expect(form.get('file').name).toBe('banner.png');
    expect(form.get('title')).toBe('겨울 대청소 이벤트');
    expect(form.get('endsOn')).toBe('2026-12-31');
    expect(form.has('startsOn')).toBe(false); // 비운 날짜는 보내지 않는다(제한 없음)
  });

  it('수정: 문구·기간을 저장하고, 이미지를 새로 골랐을 때만 이미지도 바꾼다', async () => {
    renderAt('/admin/banners/banner-3');
    expect(await screen.findByLabelText('제목')).toHaveValue('소중한 사람에게 깨끗한 하루');
    expect(screen.getByLabelText('게시 시작일 · 선택')).toHaveValue('2026-12-01');
    fireEvent.change(screen.getByLabelText('게시 시작일 · 선택'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
    await screen.findByText('게시 예정 · 12월 31일까지');
    expect(__fake.calls.find((c) => c.method === 'PUT' && c.path === '/admin/banners/banner-3').body).toEqual({
      title: '소중한 사람에게 깨끗한 하루', description: '깨끗함을 넘어, 안심을 전해요', startsOn: null, endsOn: '2026-12-31',
    });
    expect(__fake.calls.some((c) => c.path === '/admin/banners/banner-3/image')).toBe(false);
  });

  it('수정에서 사진을 지우고 새로 고르면 저장 때 이미지도 바꾼다', async () => {
    renderAt('/admin/banners/banner-1');
    fireEvent.click(await screen.findByRole('button', { name: '사진 1 삭제' }));
    expect(screen.getByRole('button', { name: '저장하기' })).toBeDisabled(); // 이미지 없이 저장하지 않는다
    fireEvent.change(screen.getByLabelText('사진 파일 선택'), { target: { files: [png()] } });
    fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
    await screen.findByText('1. 첫 청소 15,000원 쿠폰팩');
    expect(__fake.calls.find((c) => c.path === '/admin/banners/banner-1/image').body.get('file').name).toBe('banner.png');
  });

  it('삭제는 확인 시트를 거쳐 목록에서 사라진다, 서버 검사 문구는 버튼 위에', async () => {
    renderAt('/admin/banners/banner-2');
    fireEvent.change(await screen.findByLabelText('게시 종료일 · 선택'), { target: { value: '2020-01-01' } });
    fireEvent.change(screen.getByLabelText('게시 시작일 · 선택'), { target: { value: '2020-02-01' } });
    fireEvent.click(screen.getByRole('button', { name: '저장하기' }));
    expect(await screen.findByText('게시 종료일은 시작일과 같거나 뒤여야 해요')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '삭제하기' }));
    const sheet = await screen.findByRole('dialog', { name: '이 배너를 삭제할까요?' });
    fireEvent.click(within(sheet).getByRole('button', { name: '삭제' }));
    await screen.findByText('1. 첫 청소 15,000원 쿠폰팩');
    expect(screen.queryByText(/청소는 맡기고/)).not.toBeInTheDocument();
  });
});
