// GiftBanner 테스트: 기본(home) 문구와 CTA 가 보이고 CTA 가 onCta 를 부르는지, 일러스트에 3x srcset 이 있는지,
// onCta 가 없으면 버튼이 아예 없는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import GiftBanner from './GiftBanner.jsx';

describe('GiftBanner', () => {
  it('Figma 문구와 CTA 를 렌더하고 CTA 가 onCta 를 부른다', () => {
    const onCta = vi.fn();
    render(<GiftBanner onCta={onCta} />);
    expect(screen.getByText('깨끗함을 넘어')).toBeInTheDocument();
    expect(screen.getByText('안심')).toBeInTheDocument();
    const cta = screen.getByRole('button', { name: '청소 선물하러 가기 >' });
    fireEvent.click(cta);
    expect(onCta).toHaveBeenCalledTimes(1);
    // aria-label 이 있는 <section> 은 region 역할이 된다. 그 안의 img 는 alt="" 라 역할로 못 찾아 querySelector 로 찾는다.
    expect(screen.getByRole('region', { name: '청소 선물하기' }).querySelector('img').getAttribute('srcset')).toMatch(/3x/);
  });

  it('onCta 가 없으면 버튼을 그리지 않는다 — 선물하기 화면이 아직 없다 (사용성 검토 2026-09-22)', () => {
    render(<GiftBanner />);
    expect(screen.queryByRole('button')).toBeNull();
  });
});
