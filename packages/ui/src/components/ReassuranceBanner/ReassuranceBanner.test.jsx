// ReassuranceBanner 테스트: 제목·부제가 role="note" 안에 보이는지, 마스코트가 없을 때/있을 때의 그림 처리를 본다.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ReassuranceBanner from './ReassuranceBanner.jsx';

describe('ReassuranceBanner', () => {
  it('제목·설명을 note 로 그리고, 마스코트가 없으면 자리표시만 둔다', () => {
    const { container } = render(<ReassuranceBanner title="안심하고 지켜보세요!" description="항상 최선을 다해 청소하고 있어요 💙" />);
    const note = screen.getByRole('note');
    expect(note).toHaveTextContent('안심하고 지켜보세요!');
    expect(note).toHaveTextContent('항상 최선을 다해 청소하고 있어요');
    expect(container.querySelector('img')).toBeNull();
  });

  it('마스코트 이미지는 장식(alt="")', () => {
    const { container } = render(<ReassuranceBanner title="t" description="d" mascot={{ src: 'm.webp', srcSet: 'm.webp 1x' }} />);
    expect(container.querySelector('img')).toHaveAttribute('alt', '');
  });
});
