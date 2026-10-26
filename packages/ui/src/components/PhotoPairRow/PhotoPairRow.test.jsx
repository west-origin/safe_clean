// PhotoPairRow 테스트: 보기 전용/버튼 모양, 청소 후 빈 칸, 삭제 버튼의 이름·disabled, 터치 타겟을 확인한다.
import { describe, it, expect, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { render, screen, fireEvent } from '@testing-library/react';
import PhotoPairRow from './PhotoPairRow.jsx';

describe('PhotoPairRow', () => {
  it('onClick 이 없으면 버튼 없이 위치·설명만 보인다', () => {
    render(<PhotoPairRow location="거실 소파 앞" description="청소 전" beforeSrc="/b.jpg" />);
    expect(screen.queryByRole('button')).toBeNull();
    expect(screen.getByText('거실 소파 앞')).toBeInTheDocument();
    expect(screen.getByText('청소 전')).toBeInTheDocument();
  });

  it('onClick 이 있으면 줄이 버튼이 되고, 청소 후 사진이 없으면 빈 칸을 그린다', () => {
    const onClick = vi.fn();
    render(<PhotoPairRow location="주방 싱크대" description="촬영 필요" tone="needed" beforeSrc="/b.jpg" showAfter onClick={onClick} actionLabel="청소 후 사진 찍기" />);
    fireEvent.click(screen.getByRole('button', { name: '주방 싱크대 청소 후 사진 찍기' }));
    expect(onClick).toHaveBeenCalled();
    expect(screen.getByText('후')).toBeInTheDocument();
  });

  it('삭제 버튼은 위치 이름을 붙여 읽히고 disabled 로 잠긴다', () => {
    const onRemove = vi.fn();
    const { rerender } = render(<PhotoPairRow location="욕실" beforeSrc="/b.jpg" onRemove={onRemove} />);
    fireEvent.click(screen.getByRole('button', { name: '욕실 삭제' }));
    expect(onRemove).toHaveBeenCalled();
    rerender(<PhotoPairRow location="욕실" beforeSrc="/b.jpg" onRemove={onRemove} disabled />);
    expect(screen.getByRole('button', { name: '욕실 삭제' })).toBeDisabled();
  });

  it('탭 요소는 --touch-target 높이를 쓴다', () => {
    const css = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'PhotoPairRow.module.css'), 'utf8');
    expect(css.match(/min-height: var\(--touch-target\)/g)).toHaveLength(2);
  });
});
