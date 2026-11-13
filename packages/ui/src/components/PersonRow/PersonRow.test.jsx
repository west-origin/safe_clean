import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import PersonRow from './PersonRow.jsx';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// jsdom 은 실제 크기를 계산하지 않으므로 CSS 파일을 글자로 읽어 var(--touch-target) 이 있는지만 본다 (BackButton 테스트와 같은 방식)
const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'PersonRow.module.css'), 'utf8');

// PersonRow: 제목·보조 문구가 보이고, 버튼마다 클릭이 전달되며 disabled 가 속성으로 드러나고, 터치 영역에 --touch-target 을 쓴다
describe('PersonRow', () => {
  it('제목·보조 문구와 버튼을 그린다', () => {
    const accept = vi.fn();
    const reject = vi.fn();
    render(
      <PersonRow
        title="청소왕#0420"
        subtitle="김*수"
        actions={[
          { label: '수락', tone: 'primary', onClick: accept },
          { label: '거절', onClick: reject, disabled: true },
        ]}
      />,
    );
    expect(screen.getByText('청소왕#0420')).toBeInTheDocument();
    expect(screen.getByText('김*수')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '청소왕#0420 수락' }));
    expect(accept).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: '청소왕#0420 거절' })).toBeDisabled();
  });

  it('버튼이 없으면 글자만, 터치 영역은 토큰', () => {
    render(<PersonRow title="비이#0001" />);
    expect(screen.queryByRole('button')).toBeNull();
    expect(css).toContain('var(--touch-target)');
  });
});
