// StepNavBar 테스트: 단계 점이 progressbar 이름("8단계 중 3단계")으로 드러나는지, 뒤로가기 버튼이 onBack 을 부르는지,
// CSS 에 터치 영역 토큰(--touch-target)이 쓰였는지, sheet 변형도 렌더되는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import StepNavBar from './StepNavBar.jsx';

// 테스트용 가짜 브라우저(jsdom)에서는 CSS 값을 확인하기 어려워, CSS 파일 내용을 문자열로 읽어 토큰 사용 여부를 본다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'StepNavBar.module.css'), 'utf8');

describe('StepNavBar', () => {
  it('단계 진행 표시와 뒤로가기 버튼', () => {
    const onBack = vi.fn();
    render(<StepNavBar step={3} onBack={onBack} />);
    expect(screen.getByRole('progressbar', { name: '8단계 중 3단계' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '뒤로가기' }));
    expect(onBack).toHaveBeenCalledTimes(1);
    expect(css).toContain('var(--touch-target)');
  });

  it('variant sheet 도 렌더된다', () => {
    render(<StepNavBar step={4} variant="sheet" />);
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });
});
