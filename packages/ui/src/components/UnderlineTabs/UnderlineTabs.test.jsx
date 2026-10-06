// UnderlineTabs 테스트: 활성 탭이 aria-selected 로 드러나고, 다른 탭을 누르면 onChange(key) 를 부르며,
// CSS 에 터치 영역(--touch-target)이 쓰였는지 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import UnderlineTabs from './UnderlineTabs.jsx';

// 터치 영역 규칙을 확인하려고 이 폴더의 CSS 파일을 글자 그대로 읽어 둔다 (아래에서 문자열에 포함됐는지만 본다)
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'UnderlineTabs.module.css'), 'utf8');
const options = [
  { key: 'upcoming', label: '예정 2' },
  { key: 'done', label: '완료' },
  { key: 'cancelled', label: '취소' },
];

describe('UnderlineTabs', () => {
  it('활성 탭은 aria-selected, 다른 탭을 누르면 onChange(key)', () => {
    const onChange = vi.fn();
    render(<UnderlineTabs options={options} value="upcoming" onChange={onChange} ariaLabel="예약 상태" />);
    expect(screen.getByRole('tab', { name: '예정 2' })).toHaveAttribute('aria-selected', 'true');
    fireEvent.click(screen.getByRole('tab', { name: '완료' }));
    expect(onChange).toHaveBeenCalledWith('done');
    expect(css).toContain('var(--touch-target)');
  });
});
