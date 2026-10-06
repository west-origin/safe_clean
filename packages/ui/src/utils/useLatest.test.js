import { test, expect } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useLatest } from './useLatest.js';
// useLatest 검증: 반환 객체가 다시 렌더해도 같은 객체인지(화면이 useEffect deps 에 넣어도 재구독이 없게),
// next 로 새 번호를 받으면 앞 번호는 is 가 false 가 되는지. renderHook 은 컴포넌트 없이 훅만 돌린다.

test('반환 객체는 렌더가 바뀌어도 같다 (useEffect deps 용)', () => {
  const { result, rerender } = renderHook(() => useLatest());
  const first = result.current;
  rerender();
  expect(result.current).toBe(first);
});

test('먼저 발급한 번호는 뒤에 발급하면 더 이상 최신이 아니다', () => {
  const { result } = renderHook(() => useLatest());
  const a = result.current.next();
  expect(result.current.is(a)).toBe(true);
  const b = result.current.next();
  expect(result.current.is(a)).toBe(false);
  expect(result.current.is(b)).toBe(true);
});
