import { test, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import InfoNote from './InfoNote.jsx';

// InfoNote 테스트: 기본(brand) 상자가 role="note" 로 잡히고 본문이 보이는지,
// warning 톤 제목형(비용 안내)의 제목 문구가 DOM 에 나오는지 확인한다.
test('brand·warning 두 톤과 제목형을 그린다', () => {
  const { rerender } = render(<InfoNote glyph="ⓘ">정확한 방문을 위해 동·호수와 공동현관 정보를 확인해주세요.</InfoNote>);
  expect(screen.getByRole('note')).toHaveTextContent('정확한 방문을 위해');
  // rerender: 같은 자리에 새 props 로 다시 그린다 (brand → warning 제목형)
  rerender(<InfoNote tone="warning" title="수리비는 현장 진단 후 확정돼요">출장 및 진단 후</InfoNote>);
  expect(screen.getByText('수리비는 현장 진단 후 확정돼요')).toBeInTheDocument();
});
