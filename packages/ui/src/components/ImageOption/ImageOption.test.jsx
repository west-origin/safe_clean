// ImageOption 테스트: 선택 상태가 radio 역할의 aria-checked·data-state 로 드러나는지, 누르면 onSelect 를 부르는지,
// disabled 속성과 터치 영역(--touch-target)을 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ImageOption from './ImageOption.jsx';

// CSS 파일을 글자 그대로 읽는다. jsdom 은 크기를 계산하지 않아 44px 터치 영역은 스타일에 var(--touch-target) 이 쓰였는지로만 본다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'ImageOption.module.css'), 'utf8');
// 실제 에셋 대신 쓰는 가짜 이미지 항목 (ASSETS 항목과 같은 모양).
const image = { src: 'a.webp', srcSet: 'a.webp 1x', width: 79, height: 65 };

describe('ImageOption', () => {
  it('radio 로 선택 상태가 드러나고 클릭하면 onSelect 를 부른다', () => {
    const onSelect = vi.fn();
    // 선택 상태는 부모가 바꾸므로, 누른 뒤 rerender 로 selected 를 true 로 내려 준 상황을 흉내 낸다.
    const { rerender } = render(<ImageOption image={image} label="아파트·빌라" selected={false} onSelect={onSelect} />);
    const radio = screen.getByRole('radio', { name: '아파트·빌라' });
    expect(radio).toHaveAttribute('aria-checked', 'false');
    fireEvent.click(radio);
    expect(onSelect).toHaveBeenCalledTimes(1);
    rerender(<ImageOption image={image} label="아파트·빌라" selected onSelect={onSelect} />);
    expect(screen.getByRole('radio')).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio')).toHaveAttribute('data-state', 'selected');
  });

  it('size sm/lg 가 렌더되고 disabled 는 속성으로 드러난다', () => {
    render(<ImageOption image={image} label="있음" selected={false} size="lg" disabled />);
    expect(screen.getByRole('radio')).toBeDisabled();
    expect(css).toContain('var(--touch-target)');
  });
});
