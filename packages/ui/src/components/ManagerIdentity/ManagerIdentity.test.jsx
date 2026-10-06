import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import ManagerIdentity from './ManagerIdentity.jsx';

// ManagerIdentity 테스트: light 는 이름·우측 슬롯이 보이고 LIVE 배지가 없으며,
// onVideo 로 다시 그리면(rerender) liveBadge 를 안 줘도 LIVE 배지가 나오는지 본다.
// 아바타는 실제 이미지가 필요 없어 가짜 주소를 쓴다.
const avatar = { src: 'a.webp', srcSet: 'a.webp 1x' };

describe('ManagerIdentity', () => {
  it('light 는 이름·소개 + 우측 슬롯, onVideo 는 LIVE 배지', () => {
    const { rerender } = render(<ManagerIdentity avatar={avatar} name="김미영 매니저" description="경력 7년" trailing={<span>실시간</span>} />);
    expect(screen.getByText('김미영 매니저')).toBeInTheDocument();
    expect(screen.getByText('실시간')).toBeInTheDocument();
    expect(screen.queryByText('LIVE')).toBeNull();
    rerender(<ManagerIdentity avatar={avatar} name="김미영 매니저" description="경력 7년" variant="onVideo" />);
    expect(screen.getByText('LIVE')).toBeInTheDocument();
  });
});
