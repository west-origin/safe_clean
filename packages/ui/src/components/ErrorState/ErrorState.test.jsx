// ErrorState 테스트: error 없이 그리면 기본 문구가 alert 역할로 읽히고, "다시 시도" 를 누르면 onRetry 가 한 번 불리는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ErrorState from './ErrorState.jsx';

describe('ErrorState', () => {
  it('alert 로 읽히고 다시 시도 버튼이 onRetry 를 부른다', () => {
    const onRetry = vi.fn();
    render(<ErrorState onRetry={onRetry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('잠시 문제가 생겼어요');
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});
