// Checkbox 테스트: 체크 상태가 접근성 트리(checkbox 역할)에 드러나는지, 클릭 시 onChange 값,
// bold 에 따른 라벨 글꼴 클래스, disabled 속성, 터치 영역(--touch-target)을 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Checkbox from './Checkbox.jsx';

// CSS 파일을 글자 그대로 읽는다. jsdom 은 크기를 계산하지 않아 44px 터치 영역을 렌더 결과로 확인할 수 없어서
// 스타일 파일에 var(--touch-target) 이 쓰였는지만 본다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'Checkbox.module.css'), 'utf8');

describe('Checkbox', () => {
  it('Checked/Unchecked 가 checkbox 상태로 드러나고 클릭하면 onChange(반대값) 를 부른다', () => {
    // 제어 컴포넌트라 클릭만으로는 체크가 바뀌지 않는다. rerender 로 부모가 checked 를 바꿔 내려 준 상황을 흉내 낸다.
    const onChange = vi.fn();
    const { rerender } = render(<Checkbox label="(필수) 서비스 이용약관" checked={false} onChange={onChange} />);
    const box = screen.getByRole('checkbox', { name: '(필수) 서비스 이용약관' });
    expect(box).not.toBeChecked();
    fireEvent.click(box);
    expect(onChange).toHaveBeenCalledWith(true);
    rerender(<Checkbox label="(필수) 서비스 이용약관" checked onChange={onChange} />);
    expect(screen.getByRole('checkbox')).toBeChecked();
  });

  it('bold 면 라벨이 Title/Small, 아니면 Body/S', () => {
    const { rerender } = render(<Checkbox label="전체 동의하기" checked={false} bold />);
    expect(screen.getByText('전체 동의하기').className).toContain('text-title-small');
    rerender(<Checkbox label="전체 동의하기" checked={false} />);
    expect(screen.getByText('전체 동의하기').className).toContain('text-body-s');
  });

  it('disabled 면 비활성화되고, 터치 영역은 --touch-target 으로 확보한다', () => {
    render(<Checkbox label="라벨" checked={false} disabled />);
    expect(screen.getByRole('checkbox')).toBeDisabled();
    expect(css).toContain('var(--touch-target)');
  });
});
