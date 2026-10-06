// BackButton 테스트: 기본 접근성 라벨('뒤로가기')로 찾을 수 있는 버튼인지, 누르면 onClick 이 한 번 불리는지,
// CSS 가 44px 터치 영역 변수를 쓰는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import BackButton from './BackButton.jsx';

// jsdom 은 실제 크기를 계산하지 않으므로, 같은 폴더의 CSS 파일을 글자로 읽어 var(--touch-target) 이 있는지만 본다.
// (import.meta.url 은 이 테스트 파일의 주소 → fileURLToPath 로 디스크 경로로 바꾼다)
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'BackButton.module.css'), 'utf8');

describe('BackButton', () => {
  it('접근성 라벨이 있는 버튼이고 클릭하면 onClick 을 부른다', () => {
    const onClick = vi.fn();
    render(<BackButton onClick={onClick} />);
    fireEvent.click(screen.getByRole('button', { name: '뒤로가기' }));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(css).toContain('var(--touch-target)');
  });
});
