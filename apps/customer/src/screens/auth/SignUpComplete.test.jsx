// 가입 완료 화면(SignUpComplete) 테스트: '지금 시작하기'(이미지 한 장 버튼)가 통신 오류로 실패하면
// 고정 실패 문구가 화면 CSS 의 .error(화면 아래쪽에 겹쳐 보이기)로 그려지는지 본다.
// jsdom 은 위치를 계산하지 않으므로 겹침은 클래스와 CSS 글자로 확인하고, 실제 모양은 브라우저 393×852·360×800 캡처로 따로 본다.
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import SignUpComplete from './SignUpComplete.jsx';
import styles from './SignUpComplete.module.css';
import { __fake } from '../../api/http.js';
import { SIGNUP_COMPLETE_ERROR } from '../../api/mock.js';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const css = fs.readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), 'SignUpComplete.module.css'), 'utf8');

describe('Auth/SignUpComplete', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('ansimclean.signup', JSON.stringify({ agreedTermIds: ['service', 'privacy'] })); // 약관 동의가 있는 가입 초안
  });

  it('가입 완료 요청이 실패하면 실패 문구를 화면 아래쪽에 겹쳐(.error) 보인다', async () => {
    __fake.failOnce.add('POST /auth/signup/complete'); // 한 번만 통신 오류
    render(
      <MemoryRouter initialEntries={['/auth/complete']}>
        <SignUpComplete />
      </MemoryRouter>,
    );
    fireEvent.click(screen.getByRole('button', { name: '회원가입이 완료되었어요! 지금 시작하기' }));
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(SIGNUP_COMPLETE_ERROR);
    expect(alert).toHaveClass(styles.error);
    // 글자색은 공용 ActionError 의 빨강을 따른다 — 화면 CSS 의 .error 에는 색이 없다
    const rule = css.match(/\n\.error\s*\{([^}]*)\}/)?.[1] ?? '';
    expect(rule).not.toMatch(/(^|\s)color:/);
    expect(rule).toMatch(/position:\s*fixed/); // 이미지(852px)보다 짧은 화면에서도 보이는 영역 아래쪽에
  });
});
