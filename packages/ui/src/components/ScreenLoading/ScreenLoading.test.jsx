// ScreenLoading 테스트: 로딩 영역이 role="status" + aria-busy/aria-live 로 드러나는지, navBar·tabBar 슬롯이 그려지는지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ScreenLoading from './ScreenLoading.jsx';

describe('ScreenLoading', () => {
  it('status 영역이 aria-busy 로 로딩을 알리고 navBar·tabBar 슬롯을 그린다', () => {
    render(<ScreenLoading navBar={<nav>상단</nav>} tabBar={<footer>하단</footer>} />);
    const status = screen.getByRole('status');
    expect(status).toHaveAttribute('aria-busy', 'true');
    expect(status).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByText('상단')).toBeInTheDocument();
    expect(screen.getByText('하단')).toBeInTheDocument();
  });
});
