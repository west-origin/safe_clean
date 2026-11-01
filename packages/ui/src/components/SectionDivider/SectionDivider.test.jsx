// SectionDivider 테스트: 구분선(role=separator)으로 드러나는지 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import SectionDivider from './SectionDivider.jsx';

describe('SectionDivider', () => {
  it('separator 로 렌더된다', () => {
    render(<SectionDivider />);
    expect(screen.getByRole('separator')).toBeInTheDocument();
  });
});
