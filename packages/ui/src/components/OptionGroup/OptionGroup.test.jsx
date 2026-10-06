// OptionGroup 검증: 이름 붙은 radiogroup 으로 그려지고, value 와 같은 key 만 aria-checked="true" 이며,
// 다른 줄을 누르면 onChange 에 그 key 가 오는지.
import { test, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import OptionGroup from './OptionGroup.jsx';

const OPTIONS = [{ key: 'fridge', label: '냉장고' }, { key: 'washer', label: '세탁기 · 건조기' }];

test('라디오 그룹으로 그리고 고른 것만 checked', () => {
  const onChange = vi.fn();
  render(<OptionGroup ariaLabel="가전 종류" options={OPTIONS} value="fridge" onChange={onChange} />);
  expect(screen.getByRole('radiogroup', { name: '가전 종류' })).toBeInTheDocument();
  expect(screen.getByRole('radio', { name: '냉장고' })).toHaveAttribute('aria-checked', 'true');
  expect(screen.getByRole('radio', { name: '세탁기 · 건조기' })).toHaveAttribute('aria-checked', 'false');
  fireEvent.click(screen.getByRole('radio', { name: '세탁기 · 건조기' }));
  expect(onChange).toHaveBeenCalledWith('washer');
});
