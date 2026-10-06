// ToastHost 테스트: showToast 로 넣은 문구가 role=status 영역에 나타나고, 3초 뒤 사라지는지 본다.
// vi.useFakeTimers() 는 setTimeout 을 가짜 시계로 바꿔, vi.advanceTimersByTime 으로 시간을 바로 흘려보낼 수 있게 한다.
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import ToastHost from './ToastHost.jsx';
import { showToast, hideToast } from '../../utils/toast.js';

describe('ToastHost', () => {
  // 토스트 저장소는 모듈 안의 전역 값이라 테스트마다 비우고, 가짜 시계를 진짜로 되돌린다
  afterEach(() => {
    hideToast();
    vi.useRealTimers();
  });

  it('showToast 문구를 status 영역에 3초 보여준다', () => {
    vi.useFakeTimers();
    render(<ToastHost />);
    // act: 상태를 바꾸는 호출을 감싸, React 가 다시 그린 뒤에 확인하게 한다
    act(() => showToast('저장했어요'));
    expect(screen.getByRole('status')).toHaveTextContent('저장했어요');
    act(() => vi.advanceTimersByTime(3100)); // 3000ms 보다 조금 더 흘려 사라지는 타이머가 확실히 돌게 한다
    expect(screen.getByRole('status')).toHaveTextContent('');
  });
});
