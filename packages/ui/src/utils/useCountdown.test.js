import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCountdown, mmss } from './useCountdown.js';
// useCountdown·mmss 검증. 가짜 시계(vi.useFakeTimers)로 시간을 직접 움직인다:
// setSystemTime 은 Date.now 가 돌려줄 시각만 바꾸고(타이머는 불리지 않는다), advanceTimersByTime 은 시계를 그만큼
// 앞으로 돌려 그 사이의 setInterval 을 부른다(Date.now 도 같이 간다).

describe('useCountdown', () => {
  afterEach(() => vi.useRealTimers()); // 가짜 시계가 다음 테스트에 남지 않게

  it('남은 초를 1초마다 줄이고 0 에서 멈춘다', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-22T00:00:00Z'));
    const until = Date.now() + 3000; // 화면에서는 state 라 렌더마다 바뀌지 않는다
    const { result } = renderHook(() => useCountdown(until));
    expect(result.current).toBe(3);
    act(() => vi.advanceTimersByTime(1000));
    expect(result.current).toBe(2);
    act(() => vi.advanceTimersByTime(5000));
    expect(result.current).toBe(0);
  });

  it('값이 없거나 지난 시각이면 0, ISO 문자열도 받는다', () => {
    expect(renderHook(() => useCountdown(null)).result.current).toBe(0);
    expect(renderHook(() => useCountdown(Date.now() - 1000)).result.current).toBe(0);
    // 이 테스트는 실제 시계다 — 계산하는 사이 시간이 흐를 수 있어 90 이 아니라 89 이상으로 본다
    expect(renderHook(() => useCountdown(new Date(Date.now() + 90_000).toISOString())).result.current).toBeGreaterThanOrEqual(89);
    expect(mmss(59)).toBe('0:59');
    expect(mmss(125)).toBe('2:05');
  });
});

describe('useCountdown — 스냅샷', () => {
  afterEach(() => vi.useRealTimers());
  // 0.9초 시점에서 2.1초 뒤가 목표(올림 3초). 틱 없이 Date.now 만 옮겨도 값이 그대로인지, 틱이 돌면 새 값이 되는지 본다
  it('구독 중에는 같은 렌더 안에서 초가 바뀌어도 같은 값을 돌려주고, 틱이 값을 바꾼다 (검수 4차: getSnapshot 미캐시 경고)', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-22T00:00:00.900Z'));
    const until = Date.now() + 2100; // 3초 남음
    const { result } = renderHook(() => useCountdown(until));
    expect(result.current).toBe(3);
    vi.setSystemTime(new Date('2026-09-22T00:00:01.100Z')); // 틱 없이 시간만 지남 → 스냅샷은 그대로
    expect(result.current).toBe(3);
    act(() => vi.advanceTimersByTime(1000)); // 02.100 — 남은 0.9초 → 1
    expect(result.current).toBe(1);
  });
});
