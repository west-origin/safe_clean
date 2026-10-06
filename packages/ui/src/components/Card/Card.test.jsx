// Card 검증: 모든 tone 이 각각 클래스 이름으로 붙고 자식 내용을 그대로 그리는지.
import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import Card from './Card.jsx';

describe('Card', () => {
  it('tone 8종이 클래스로 드러나고 자식을 렌더한다', () => {
    for (const tone of ['default', 'live', 'brand', 'subtle', 'muted', 'danger', 'warning', 'warningMuted']) {
      const { container, unmount } = render(<Card tone={tone}>내용</Card>);
      expect(container.firstChild.className).toContain(tone);
      expect(container.textContent).toBe('내용');
      unmount(); // 다음 tone 을 그리기 전에 지워서 화면에 카드가 하나만 있게 한다
    }
  });
});
