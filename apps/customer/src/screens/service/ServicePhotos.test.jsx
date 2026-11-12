import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import ServicePhotos from './ServicePhotos.jsx';
import { getCleaningPhotos } from '../../api/customer.js';

vi.mock('../../api/customer.js', () => ({ getCleaningPhotos: vi.fn() }));
const photos = [{ id: 'p1', location: '거실', beforeUrl: '/before.jpg', afterUrl: '/after.jpg' }];
function renderPhotos() {
  return render(<MemoryRouter initialEntries={['/service/photos?booking=completed-without-recording']}><Routes>
    <Route path="/service/photos" element={<ServicePhotos />} />
  </Routes></MemoryRouter>);
}

describe('녹화와 독립적인 고객 사진 비교', () => {
  beforeEach(() => { vi.clearAllMocks(); getCleaningPhotos.mockResolvedValue({ ready: true, photos }); });

  it('녹화·라이브 연결 없이 완료 예약의 사진을 비교한다', async () => {
    const { container } = renderPhotos();
    expect(await screen.findByRole('slider', { name: '거실 청소 전후 비교' })).toBeInTheDocument();
    expect(getCleaningPhotos).toHaveBeenCalledWith('completed-without-recording');
    expect(screen.getByAltText('거실 청소 전')).toHaveAttribute('src', '/before.jpg');
    expect(container.querySelector('video')).toBeNull();
  });

  it('비교 안내는 모든 사진 카드 밖에서 첫 카드 위에 한 번만 표시한다', async () => {
    getCleaningPhotos.mockResolvedValue({ ready: true, photos: [...photos, { ...photos[0], id: 'p2', location: '주방' }] });
    const { container } = renderPhotos();
    const guide = await screen.findByText('선을 좌우로 움직여 청소 전후를 비교해보세요');
    const cards = container.querySelectorAll('figure');
    expect(cards).toHaveLength(2);
    expect(cards[0]).not.toContainElement(guide);
    expect(cards[1]).not.toContainElement(guide);
    expect(guide.compareDocumentPosition(cards[0].querySelector('img')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('이미지 만료·실패 시 새 사진 URL을 받아 다시 표시한다', async () => {
    renderPhotos();
    fireEvent.error(await screen.findByAltText('거실 청소 전'));
    expect(screen.getByRole('alert')).toHaveTextContent('거실 사진을 불러오지 못했어요');
    getCleaningPhotos.mockResolvedValue({ ready: true, photos: [{ ...photos[0], beforeUrl: '/renewed-before.jpg' }] });
    fireEvent.click(screen.getByRole('button', { name: '사진 다시 불러오기' }));
    await waitFor(() => expect(screen.getByAltText('거실 청소 전')).toHaveAttribute('src', '/renewed-before.jpg'));
  });

  it('서명 URL 누락도 빈 사진 대신 재시도를 안내한다', async () => {
    getCleaningPhotos.mockResolvedValue({ ready: true, photos: [{ ...photos[0], beforeUrl: null }] });
    renderPhotos();
    expect(await screen.findByRole('alert')).toHaveTextContent('사진을 불러오지 못했어요');
    expect(screen.queryByRole('slider')).toBeNull();
  });

  it('완료 전에는 사진을 공개하지 않고 준비 안내를 표시한다', async () => {
    getCleaningPhotos.mockResolvedValue({ ready: false, photos: [] });
    renderPhotos();
    expect(await screen.findByText('청소가 완료되면 전·후 사진을 볼 수 있어요')).toBeInTheDocument();
    expect(screen.queryByRole('slider')).toBeNull();
  });
});
