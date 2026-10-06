// ProfileCard 검증: 카드 버튼의 이름이 "이름 전화번호" 로 읽히고, 누르면 onClick 이 한 번 불리는지.
// 버튼의 이름은 안의 글자를 이어 붙인 것이라 "김하늘 님 010-1234-5678" 한 줄로 찾는다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ProfileCard from './ProfileCard.jsx';

describe('ProfileCard', () => {
  it('이름·전화번호가 보이고 누르면 onClick', () => {
    const onClick = vi.fn();
    render(<ProfileCard name="김하늘 님" phone="010-1234-5678" onClick={onClick} />);
    const btn = screen.getByRole('button', { name: '김하늘 님 010-1234-5678' });
    fireEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
