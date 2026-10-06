// ImageButton 테스트: 이미지뿐인 버튼이 label 로 이름을 갖는지(getByRole 로 찾아지는지), 누르면 onClick 이 불리는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ImageButton from './ImageButton.jsx';

describe('ImageButton', () => {
  it('이미지 버튼이 aria-label 로 이름을 갖고 클릭이 전달된다', () => {
    const onClick = vi.fn();
    render(<ImageButton image={{ src: 'a.webp', srcSet: 'a.webp 1x', width: 300, height: 100 }} label="카카오톡으로 시작하기" onClick={onClick} />);
    fireEvent.click(screen.getByRole('button', { name: '카카오톡으로 시작하기' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
