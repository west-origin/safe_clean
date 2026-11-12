import { it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import KeyboardAwareFooter from './KeyboardAwareFooter.jsx';

it('키보드로 줄어든 화면에서는 푸터를 숨기고 닫히면 다시 표시한다', () => {
  const original = window.innerHeight;
  vi.stubGlobal('innerHeight', 800);
  render(<KeyboardAwareFooter><button>다음</button></KeyboardAwareFooter>);
  expect(screen.getByRole('button', { name: '다음' })).toBeVisible();
  vi.stubGlobal('innerHeight', 480);
  fireEvent(window, new Event('resize'));
  expect(screen.queryByRole('button', { name: '다음' })).toBeNull();
  vi.stubGlobal('innerHeight', 800);
  fireEvent(window, new Event('resize'));
  expect(screen.getByRole('button', { name: '다음' })).toBeVisible();
  vi.stubGlobal('innerHeight', original);
  vi.unstubAllGlobals();
});
