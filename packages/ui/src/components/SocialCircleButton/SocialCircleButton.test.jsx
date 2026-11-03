// SocialCircleButton 테스트: 세 회사 모두 이름 있는 버튼으로 그려지고 누르면 onClick. 터치 영역 토큰을 쓴다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import SocialCircleButton from './SocialCircleButton.jsx';

const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'SocialCircleButton.module.css'), 'utf8');

describe('SocialCircleButton', () => {
  it('kakao·google·naver', () => {
    const onClick = vi.fn();
    render(
      <>
        <SocialCircleButton provider="kakao" label="카카오톡으로 로그인" onClick={onClick} />
        <SocialCircleButton provider="google" label="구글로 로그인" />
        <SocialCircleButton provider="naver" label="네이버로 로그인" />
      </>,
    );
    expect(screen.getAllByRole('button')).toHaveLength(3);
    fireEvent.click(screen.getByRole('button', { name: '카카오톡으로 로그인' }));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('터치 영역 토큰을 쓴다', () => {
    expect(css).toMatch(/min-height:\s*var\(--touch-target\)/);
  });
});
