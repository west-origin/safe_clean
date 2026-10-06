// RepairCategoryList 테스트: categories.js 의 순서대로 버튼 8개가 그려지는지, 누르면 그 칸의 key 로 onSelect 를 부르는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import RepairCategoryList from './RepairCategoryList.jsx';
import { REPAIR_CATEGORIES } from './categories.js';

describe('RepairCategoryList', () => {
  // 버튼 글자 목록을 REPAIR_CATEGORIES 의 label 목록과 통째로 비교해 순서까지 확인한다
  it('Figma 순서대로 8개를 그리고 누르면 key 를 넘긴다', () => {
    const onSelect = vi.fn();
    render(<RepairCategoryList onSelect={onSelect} />);
    const items = screen.getAllByRole('button');
    expect(items).toHaveLength(8);
    expect(items.map((i) => i.textContent)).toEqual(REPAIR_CATEGORIES.map((c) => c.label));
    fireEvent.click(screen.getByRole('button', { name: '배관·누수' }));
    expect(onSelect).toHaveBeenCalledWith('plumbing');
  });
});
