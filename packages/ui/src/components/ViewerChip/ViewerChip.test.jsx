// ViewerChip 테스트: 칩 전체가 '시청자 N명' 이라는 이름의 img 로 찾아지고, 안의 숫자는 aria-hidden 으로 가려지는지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ViewerChip from './ViewerChip.jsx';

describe('ViewerChip', () => {
  it('시청자 수를 보조기기에 "시청자 N명" 으로 알린다 (role 이 있어야 aria-label 이 읽힌다)', () => {
    render(<ViewerChip count={3} />);
    expect(screen.getByRole('img', { name: '시청자 3명' })).toBeInTheDocument();
    expect(screen.getByText('3')).toHaveAttribute('aria-hidden', 'true');
  });
});
