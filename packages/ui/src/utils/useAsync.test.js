import { test, expect } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useAsync } from './useAsync.js';
// useAsync 의 setData·refresh 동작 검증. renderHook 은 컴포넌트 없이 훅만 돌려 result.current 로 반환값을 보게 하고,
// act 는 그 안의 상태 변경을 React 에 반영한 뒤 다음 줄로 넘어가게 한다. waitFor 는 조건이 맞을 때까지 다시 확인한다.

test('setData 는 오류 상태도 지운다 — 첫 조회가 실패한 뒤 폴링이 성공하면 데이터가 보여야 한다', async () => {
  const { result } = renderHook(() => useAsync(() => Promise.reject(new Error('down')), []));
  await waitFor(() => expect(result.current.error).toBeTruthy());
  act(() => result.current.setData({ ok: true }));
  expect(result.current.error).toBeNull();
  expect(result.current.data).toEqual({ ok: true });
});

test('refresh 는 받아 둔 데이터를 둔 채 새 값으로 바꾸고, 실패하면 화면은 그대로 두고 reject 한다 (자체 점검 #48)', async () => {
  // load 는 부를 때마다 1, 2 … 를 돌려준다. fail 을 켜면 던진다 — 첫 조회 1 → refresh 성공 2 → refresh 실패 순서로 본다
  let n = 0;
  let fail = false;
  const { result } = renderHook(() => useAsync(async () => {
    if (fail) throw new Error('down');
    n += 1;
    return n;
  }, []));
  await waitFor(() => expect(result.current.data).toBe(1));
  let refreshed;
  await act(async () => { refreshed = await result.current.refresh(); });
  expect(refreshed).toBe(2);
  expect(result.current.data).toBe(2);
  expect(result.current.loading).toBe(false);

  fail = true;
  await act(async () => { await expect(result.current.refresh()).rejects.toThrow('down'); });
  expect(result.current.data).toBe(2); // 오류 화면으로 덮지 않는다
  expect(result.current.error).toBeNull();
});
