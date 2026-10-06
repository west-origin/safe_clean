import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import StatusBar from './StatusBar.jsx';

// StatusBar 테스트: 글자 없는 빈 요소이고 aria-hidden 인지 본다. 높이(safe-area)는 CSS 가 정하므로 여기서는 보지 않는다.

describe('StatusBar', () => {
  it('보조기기에서 숨겨진 빈 여백만 렌더한다 (시계·배터리 없음)', () => {
    const { container } = render(<StatusBar />);
    expect(container.firstChild).toHaveAttribute('aria-hidden', 'true');
    expect(container.textContent).toBe('');
  });
});
