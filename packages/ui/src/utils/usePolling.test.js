import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePolling } from './usePolling.js';
// usePolling 검증: 간격 갱신·외부 신호(푸시)·숨김 중 멈춤·복귀 즉시 갱신·해제 때 구독 풀기,
// invalidate 뒤 옛 응답 무시, 마지막 렌더의 load 사용. 가짜 시계(vi.useFakeTimers)로 간격을 움직이고
// document.visibilityState 를 직접 바꿔 앱이 숨겨진 상황을 흉내 낸다.

// 갱신은 Promise 의 then 이 몇 단계 이어진 뒤에 반영되므로, act 안에서 여러 번 넘겨 상태 반영까지 끝낸다
const flush = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
};

describe('usePolling', () => {
  // 가짜 시계와 숨김 상태를 원래대로 — 다음 테스트에 남지 않게
  afterEach(() => {
    vi.useRealTimers();
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
  });

  it('간격마다 다시 받고, 외부 신호로도 받고, 숨겨진 동안은 받지 않다가 돌아오면 바로 받는다', async () => {
    vi.useFakeTimers();
    let n = 0;
    const load = vi.fn(async () => ({ n: ++n })); // 부를 때마다 n 이 1씩 올라 몇 번째 응답인지 보인다
    // 푸시 구독 흉내: 등록된 함수를 테스트가 직접 부르면 "푸시가 왔다" 가 된다
    const listeners = new Set();
    const subscribe = (fn) => {
      listeners.add(fn);
      return () => listeners.delete(fn);
    };
    const { result, unmount } = renderHook(() => usePolling(load, [], { interval: 1000, subscribe }));
    await flush();
    expect(result.current.data).toEqual({ n: 1 });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    await flush();
    expect(load).toHaveBeenCalledTimes(2);
    expect(result.current.data).toEqual({ n: 2 });
    await act(async () => {
      listeners.forEach((fn) => fn()); // 푸시
    });
    await flush();
    expect(load).toHaveBeenCalledTimes(3);
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(3000);
    });
    expect(load).toHaveBeenCalledTimes(3); // 숨겨진 동안은 없음
    Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange')); // 돌아오면 바로
    });
    await flush();
    expect(load).toHaveBeenCalledTimes(4);
    unmount();
    expect(listeners.size).toBe(0); // 화면이 사라지면 푸시 구독도 풀린다
  });

  it('invalidate 뒤에 도착한 옛 폴링 응답은 버린다', async () => {
    vi.useFakeTimers();
    // 첫 조회는 바로 끝나고, 두 번째(폴링) 조회는 테스트가 release 를 부를 때까지 끝나지 않게 만든다
    let release;
    const load = vi
      .fn()
      .mockImplementationOnce(async () => ({ v: 'first' }))
      .mockImplementationOnce(() => new Promise((r) => (release = r)));
    const { result } = renderHook(() => usePolling(load, [], { interval: 1000 }));
    await flush();
    expect(result.current.data).toEqual({ v: 'first' });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000); // 느린 폴링 시작
    });
    act(() => result.current.invalidate()); // 사용자 동작(취소 등)이 그 사이에 끝났다
    await act(async () => {
      release({ v: 'stale' });
    });
    await flush();
    expect(result.current.data).toEqual({ v: 'first' }); // 옛 응답으로 덮이지 않는다
  });

  it('폴링은 마지막 렌더의 load 를 쓴다 (deps 에 없는 값을 잡아도 옛 함수로 돌지 않게)', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(({ tag }) => usePolling(async () => ({ tag }), [], { interval: 1000 }), { initialProps: { tag: 'a' } });
    await flush();
    expect(result.current.data).toEqual({ tag: 'a' });
    rerender({ tag: 'b' }); // 새 렌더 = 새 load 클로저 (deps 는 그대로)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    await flush();
    expect(result.current.data).toEqual({ tag: 'b' });
  });

  it('대상(deps)이 바뀌면 옛 대상으로 나가 있던 폴링 응답은 도착해도 버린다', async () => {
    // /job/A 를 보다가 /job/B 로 바뀌는 경우 — A 의 늦은 폴링 응답이 B 화면을 덮으면 안 된다
    const pending = {}; // id → 그 id 폴링의 resolve (테스트가 원하는 때 도착시킨다)
    let polling = false;
    const load = vi.fn((id) => (polling ? new Promise((r) => (pending[id] = r)) : Promise.resolve({ id, n: 0 })));
    const listeners = new Set();
    const subscribe = (fn) => {
      listeners.add(fn);
      return () => listeners.delete(fn);
    };
    const { result, rerender } = renderHook(({ id }) => usePolling(() => load(id), [id], { interval: 0, subscribe }), { initialProps: { id: 'A' } });
    await flush();
    expect(result.current.data).toEqual({ id: 'A', n: 0 });

    polling = true;
    act(() => listeners.forEach((fn) => fn())); // A 로 폴링이 나간다
    await flush();
    polling = false;
    rerender({ id: 'B' }); // 대상이 B 로 바뀐다 → B 첫 조회
    await flush();
    expect(result.current.data).toEqual({ id: 'B', n: 0 });

    await act(async () => pending.A({ id: 'A', n: 1 })); // A 의 폴링 응답이 늦게 도착
    await flush();
    expect(result.current.data).toEqual({ id: 'B', n: 0 });
  });

  it('조용한 갱신이 실패하면 onError 로 알린다 (주지 않으면 무시)', async () => {
    let fail = false;
    const load = vi.fn(async () => {
      if (fail) throw Object.assign(new Error('취소된 예약이에요'), { status: 409 });
      return { ok: true };
    });
    const onError = vi.fn();
    const listeners = new Set();
    const subscribe = (fn) => {
      listeners.add(fn);
      return () => listeners.delete(fn);
    };
    const { result } = renderHook(() => usePolling(load, [], { interval: 0, subscribe, onError: (e) => onError(e) })); // 매 렌더 새 함수여도 된다
    await flush();
    fail = true;
    act(() => listeners.forEach((fn) => fn()));
    await flush();
    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0].status).toBe(409);
    expect(result.current.data).toEqual({ ok: true }); // 화면 값은 그대로 — 무엇을 할지는 화면이 정한다
  });
});

