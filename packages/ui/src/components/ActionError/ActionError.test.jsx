// ActionError 테스트: error 가 없으면 아무것도 그리지 않고, 있으면 role=alert 문구를 그리며, 메시지가 비면 fallback 을 쓰는지.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ActionError from './ActionError.jsx';

describe('ActionError', () => {
  it('error 가 있을 때만 role=alert 로 문구를 그리고, 메시지가 없으면 fallback', () => {
    // rerender 로 같은 자리에 props 만 바꿔 다시 그린다: null → 없음, Error(메시지) → 그 메시지, 빈 객체 → fallback
    const { rerender } = render(<ActionError error={null} />);
    expect(screen.queryByRole('alert')).toBeNull();
    rerender(<ActionError error={new Error('서버가 거절했어요')} />);
    expect(screen.getByRole('alert')).toHaveTextContent('서버가 거절했어요');
    rerender(<ActionError error={{}} fallback="저장하지 못했어요" />);
    expect(screen.getByRole('alert')).toHaveTextContent('저장하지 못했어요');
  });
});
