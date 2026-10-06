// ScreenError 테스트: 기본 문구·다시 시도 버튼, 문구와 navBar/tabBar 슬롯 바꾸기, error 종류별 문구(네트워크·404·그 밖)를 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ScreenError from './ScreenError.jsx';

describe('ScreenError', () => {
  it('기본 문구와 "다시 시도" 버튼을 그리고, 누르면 onRetry 를 부른다', () => {
    const onRetry = vi.fn();
    render(<ScreenError onRetry={onRetry} />);
    expect(screen.getByText('잠시 문제가 생겼어요')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('문구·버튼 라벨·navBar·tabBar 슬롯을 바꿔 그린다', () => {
    render(<ScreenError onRetry={() => {}} title="이미 마감된 요청이에요" description="설명" retryLabel="확인" navBar={<nav>상단</nav>} tabBar={<footer>하단</footer>} />);
    expect(screen.getByText('이미 마감된 요청이에요')).toBeInTheDocument();
    expect(screen.getByText('설명')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '확인' })).toBeInTheDocument();
    expect(screen.getByText('상단')).toBeInTheDocument();
    expect(screen.getByText('하단')).toBeInTheDocument();
  });

  // rerender 로 error 를 바꿔 가며 다시 그린다. 404 는 onRetry 를 줘도 다시 시도 버튼이 없고,
  // navBar 없이 onBack 만 줬으므로 ScreenError 가 만든 뒤로가기 버튼이 보여야 한다.
  it('error 로 종류를 나눈다: NETWORK 는 연결 안내, 404 는 서버 문구 + 다시 시도 없음, 그 외는 서버 문구', () => {
    const { rerender } = render(<ScreenError error={{ code: 'NETWORK', status: 0 }} onRetry={() => {}} />);
    expect(screen.getByText('네트워크에 연결할 수 없어요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    rerender(<ScreenError error={{ code: 'NOT_FOUND', status: 404, message: '삭제된 공지예요' }} onRetry={() => {}} onBack={() => {}} />);
    expect(screen.getByText('찾을 수 없어요')).toBeInTheDocument();
    expect(screen.getByText('삭제된 공지예요')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 시도' })).toBeNull();
    expect(screen.getByRole('button', { name: '뒤로가기' })).toBeInTheDocument();
    rerender(<ScreenError error={{ code: 'INVALID_STATE', status: 409, message: '끝난 작업이에요' }} onRetry={() => {}} />);
    expect(screen.getByText('잠시 문제가 생겼어요')).toBeInTheDocument();
    expect(screen.getByText('끝난 작업이에요')).toBeInTheDocument();
  });
});
