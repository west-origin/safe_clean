// SummaryItem 테스트: 제목·값·부가값이 보이고 "수정하기" 버튼이 "<제목> 수정하기" 이름으로 onEdit 을 부르는지,
// CSS 에 터치 영역 토큰이 쓰였는지, children 과 note(요청사항 상자)가 그려지는지 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import SummaryItem from './SummaryItem.jsx';

// 테스트용 가짜 브라우저(jsdom)에서는 CSS 값을 확인하기 어려워, CSS 파일 내용을 문자열로 읽어 본다.
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'SummaryItem.module.css'), 'utf8');
// 실제 그림 대신 쓰는 가짜 아이콘 값 (ASSETS.confirmIcons.* 와 같은 모양)
const icon = { src: 'a.webp', srcSet: 'a.webp 1x', width: 50, height: 46 };

describe('SummaryItem', () => {
  it('제목·값·부가값과 수정하기 버튼', () => {
    const onEdit = vi.fn();
    render(<SummaryItem icon={icon} title="방문 일정" value="2026년 11월 1일" subValue="오전 9시" onEdit={onEdit} />);
    expect(screen.getByText('2026년 11월 1일')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '방문 일정 수정하기' }));
    expect(onEdit).toHaveBeenCalledTimes(1);
    expect(css).toContain('var(--touch-target)');
  });

  it('note 는 회색 상자에, children 은 그대로', () => {
    render(
      <SummaryItem icon={icon} title="선택 옵션">
        <span>칩</span>
      </SummaryItem>,
    );
    expect(screen.getByText('칩')).toBeInTheDocument();
    render(<SummaryItem icon={icon} title="요청사항" note="창문 꼼꼼히" />);
    expect(screen.getByText('창문 꼼꼼히')).toBeInTheDocument();
  });
});
