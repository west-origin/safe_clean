// Chip 테스트: 라벨이 누를 수 없는 <span> 으로 렌더되는지(버튼이 아닌지) 확인한다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Chip from './Chip.jsx';

describe('Chip', () => {
  it('라벨을 읽기 전용으로 렌더한다', () => {
    render(<Chip>라벨</Chip>);
    expect(screen.getByText('라벨').tagName).toBe('SPAN');
  });
});
