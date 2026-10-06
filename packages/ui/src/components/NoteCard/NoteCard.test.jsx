// NoteCard 검증: 안내문이 role="note" 안에 보이고, 아이콘은 장식(alt="")이라 읽히지 않는지.
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import NoteCard from './NoteCard.jsx';

// 가짜 아이콘 데이터 (실제 ASSETS 와 같은 모양 { src, srcSet, width, height }). 이미지 파일은 없어도 되고 img 태그의 속성만 본다.
const icon = { src: 'a.webp', srcSet: 'a.webp 1x', width: 50, height: 48 };

describe('NoteCard', () => {
  it('장식 아이콘(alt="") 과 안내문을 note 로 렌더한다', () => {
    render(<NoteCard icon={icon}>{'수집된 개인정보는 안전하게 보호되며, \n서비스 제공 목적 외로 사용되지 않습니다.'}</NoteCard>);
    const note = screen.getByRole('note');
    expect(note).toHaveTextContent('수집된 개인정보는 안전하게 보호되며');
    expect(note.querySelector('img')).toHaveAttribute('alt', '');
  });
});
