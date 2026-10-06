import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ServiceCategoryList from './ServiceCategoryList.jsx';

// ServiceCategoryList 테스트: 버튼 4개의 문구·순서, 아이콘의 3x 이미지 후보, 누르면 onSelect 에 키가 오는지.

describe('ServiceCategoryList', () => {
  it('카테고리 4개가 Figma 문구·순서대로 있고 누르면 onSelect(key)', () => {
    const onSelect = vi.fn();
    render(<ServiceCategoryList onSelect={onSelect} />);
    const items = screen.getAllByRole('button');
    // <br /> 는 글자가 없어 두 줄 라벨도 textContent 에서는 붙어 나온다 (예: '가정/원룸' + '청소')
    expect(items.map((b) => b.textContent)).toEqual(['가정/원룸청소', '입주(이사)청소', '가전청소', '사무실 청소']);
    expect(items[0].querySelector('img').getAttribute('srcset')).toMatch(/3x/);
    // 세 번째 버튼(가전청소)을 누르면 키 'appliance' 로 불려야 한다
    fireEvent.click(items[2]);
    expect(onSelect).toHaveBeenCalledWith('appliance');
  });
});
