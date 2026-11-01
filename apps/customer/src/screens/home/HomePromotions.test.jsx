import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import HomePromotions from './HomePromotions.jsx';

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});
const current = (n) =>
  expect(
    screen.getByRole('button', { name: new RegExp('^진행 중인 이벤트 전체 보기, ' + n + ' / 3') })
  ).toHaveTextContent(n + '/3');
const openEvents = () =>
  fireEvent.click(screen.getByRole('button', { name: /^진행 중인 이벤트 전체 보기/ }));

describe('홈 자동 롤링 배너', () => {
  it('5초마다 옆으로 이동하고 마지막 장에서 복제된 첫 장을 통해 순환한다', () => {
    vi.useFakeTimers();
    const { container } = render(<HomePromotions />);
    current(1);
    act(() => vi.advanceTimersByTime(4999));
    current(1);
    act(() => vi.advanceTimersByTime(1));
    current(2);
    act(() => vi.advanceTimersByTime(5000));
    current(3);
    act(() => vi.advanceTimersByTime(5000));
    current(1);
    const track = container.querySelector('[style]');
    expect(track.style.transform).toBe('translateX(-300%)');
    fireEvent.transitionEnd(track);
    expect(track.style.transform).toBe('translateX(-0%)');
  });
  it('일시정지·재생과 키보드 이동을 지원한다', () => {
    vi.useFakeTimers();
    render(<HomePromotions />);
    fireEvent.click(screen.getByRole('button', { name: '배너 자동 넘김 일시정지' }));
    act(() => vi.advanceTimersByTime(15000));
    current(1);
    fireEvent.click(screen.getByRole('button', { name: '배너 자동 넘김 재생' }));
    act(() => vi.advanceTimersByTime(5000));
    current(2);
    fireEvent.keyDown(screen.getByRole('group', { name: /혜택 배너/ }), { key: 'ArrowLeft' });
    current(1);
  });
  it('동작 줄이기 설정에서는 자동으로 넘기지 않는다', () => {
    vi.useFakeTimers();
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    render(<HomePromotions />);
    act(() => vi.advanceTimersByTime(15000));
    current(1);
    openEvents();
    fireEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: /^3번 배너 보기/ })
    );
    current(3);
  });
  it('번호 버튼으로 전체 배너를 열고 선택한 배너로 돌아온다', () => {
    vi.useFakeTimers();
    render(<HomePromotions />);
    openEvents();
    const dialog = screen.getByRole('dialog', { name: '진행 중인 이벤트' });
    expect(within(dialog).getAllByRole('listitem')).toHaveLength(3);
    // 배너는 이미지라 문구를 alt 로 확인한다
    expect(within(dialog).getByRole('img', { name: /^첫 청소 15,000원 쿠폰팩/ })).toBeInTheDocument();
    expect(within(dialog).getByRole('img', { name: /^청소는 맡기고, 일상은 여유롭게/ })).toBeInTheDocument();
    expect(within(dialog).getByRole('img', { name: /^소중한 사람에게 깨끗한 하루/ })).toBeInTheDocument();
    act(() => vi.advanceTimersByTime(15000));
    current(1);
    fireEvent.click(within(dialog).getByRole('button', { name: /^3번 배너 보기/ }));
    expect(screen.queryByRole('dialog')).toBeNull();
    current(3);
  });
  it('전체 목록을 닫아도 사용자가 선택한 일시정지를 유지한다', () => {
    vi.useFakeTimers();
    render(<HomePromotions />);
    fireEvent.click(screen.getByRole('button', { name: '배너 자동 넘김 일시정지' }));
    openEvents();
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '닫기' }));
    act(() => vi.advanceTimersByTime(10000));
    current(1);
    expect(screen.getByRole('button', { name: '배너 자동 넘김 재생' })).toHaveAttribute(
      'aria-pressed',
      'true'
    );
  });
});
