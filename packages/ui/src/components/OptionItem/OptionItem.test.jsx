// OptionItem 검증: checkbox 역할로 선택 여부(aria-checked)가 드러나고 누르면 onSelect 가 불리는지,
// 유료형은 가격이 이름에 붙어 읽히고 disabled 가 속성으로 드러나는지.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import OptionItem from './OptionItem.jsx';

// 가짜 아이콘 데이터 (ASSETS.bookingOptionIcons 항목과 같은 모양)
const image = { src: 'a.webp', srcSet: 'a.webp 1x', width: 80, height: 89 };

describe('OptionItem', () => {
  it('무료 옵션: checkbox 로 선택 상태가 드러나고 누르면 onSelect', () => {
    const onSelect = vi.fn();
    render(<OptionItem image={image} label="설거지" selected={false} onSelect={onSelect} />);
    const box = screen.getByRole('checkbox', { name: '설거지' });
    expect(box).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(box);
    expect(onSelect).toHaveBeenCalledTimes(1);
  });

  // 줄바꿈이 든 라벨 + 가격을 넘기면, 접근성 이름은 줄바꿈이 공백이 된 "라벨 가격" 한 줄이어야 한다.
  it('유료 옵션: 가격이 보이고 disabled 가 속성으로 드러난다', () => {
    render(<OptionItem image={image} label={'반려동물\n배변 청소'} price="+3,000원" selected disabled />);
    const box = screen.getByRole('checkbox', { name: '반려동물 배변 청소 +3,000원' });
    expect(box).toBeDisabled();
    expect(box).toHaveAttribute('aria-checked', 'true');
  });
});
