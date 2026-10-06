// AddressResultList 테스트: 결과 한 건을 버튼으로 그리고, 누르면 onSelect 가 그 결과 객체로 불리는지.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import AddressResultList from './AddressResultList.jsx';

describe('AddressResultList', () => {
  it('결과를 나열하고 누르면 onSelect(result)', () => {
    // 버튼 이름은 안의 글자(주소 + 부가 정보)를 이어 붙인 것이라 정규식으로 주소 일부만 맞춘다
    const onSelect = vi.fn();
    render(<AddressResultList results={[{ id: 'a', main: '서울특별시 강남구 테헤란로 123', sub: '(역삼동, ABC빌딩)' }]} onSelect={onSelect} />);
    fireEvent.click(screen.getByRole('button', { name: /테헤란로 123/ }));
    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ id: 'a' })); // objectContaining: 나머지 필드는 보지 않고 id 만 맞는지
  });
});
