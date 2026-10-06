// ConfirmSummaryRow 테스트: 값이 보이고, "수정 ›" 버튼이 "<항목> 수정" 이름으로 찾아지며 누르면 onEdit 을 부르는지 확인한다.
import { test, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import ConfirmSummaryRow from './ConfirmSummaryRow.jsx';

test('항목·값·보조 정보와 수정 버튼', () => {
  const onEdit = vi.fn();
  render(<ConfirmSummaryRow icon="🧰" label="서비스" value="가전수리 · 냉장고" sub="냉장이 잘 안 돼요" onEdit={onEdit} />);
  expect(screen.getByText('가전수리 · 냉장고')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '서비스 수정' }));
  expect(onEdit).toHaveBeenCalled();
});
