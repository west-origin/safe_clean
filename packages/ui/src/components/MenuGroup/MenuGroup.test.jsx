import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import MenuGroup from './MenuGroup.jsx';
import MenuRow from '../MenuRow/MenuRow.jsx';

// MenuGroup 테스트: 제목 이름의 region(section + aria-label)으로 찾히고, 안에 넣은 MenuRow 버튼 2개가 들어 있는지 본다.
describe('MenuGroup', () => {
  it('제목과 행들을 section 으로 묶는다', () => {
    render(
      <MenuGroup title="계정">
        <MenuRow label="내 정보 수정" onClick={() => {}} />
        <MenuRow label="주소 관리" onClick={() => {}} />
      </MenuGroup>,
    );
    const group = screen.getByRole('region', { name: '계정' });
    expect(group.querySelectorAll('button')).toHaveLength(2);
  });
});
