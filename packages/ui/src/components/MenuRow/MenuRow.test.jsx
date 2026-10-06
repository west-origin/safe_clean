import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import MenuRow from './MenuRow.jsx';

// MenuRow 테스트: 세 모양(chevron 버튼·값·토글 switch)을 한 번에 그려 각각의 역할과 동작을 본다.
// CSS 파일 내용을 글자로 읽어 두고, 터치 영역 변수(--touch-target)가 쓰였는지 아래에서 확인한다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'MenuRow.module.css'), 'utf8');

describe('MenuRow', () => {
  it('chevron 행은 버튼, value 행은 값 표시, toggle 행은 switch', () => {
    const onClick = vi.fn();
    const onToggle = vi.fn();
    render(
      <>
        <MenuRow label="내 정보 수정" onClick={onClick} />
        <MenuRow label="버전 정보" value="v1.0.0" />
        <MenuRow label="푸시 알림" checked onToggle={onToggle} />
      </>,
    );
    fireEvent.click(screen.getByRole('button', { name: '내 정보 수정' }));
    expect(onClick).toHaveBeenCalledTimes(1);
    expect(screen.getByText('v1.0.0')).toBeInTheDocument();
    // checked(true) 인 토글을 누르면 반대값 false 로 onToggle 이 불린다
    fireEvent.click(screen.getByRole('switch', { name: '푸시 알림' }));
    expect(onToggle).toHaveBeenCalledWith(false);
    expect(css).toContain('var(--touch-target)');
  });
});
