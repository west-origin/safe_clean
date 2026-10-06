// 로그인 화면(LoginPhone) 테스트: 방 일러스트(HeroArt)가 제목·입력칸 **뒤**에 그려지는지 (사용자 결정 2026-09-21, 가입 화면과 같은 방식).
// jsdom 은 겹침 순서를 계산하지 않으므로, 제목·입력칸 묶음(.stack)이 일러스트(.hero)보다 높은 z-index 를 갖는지 CSS 글자로 확인한다.
// 실제 겹침은 브라우저 393px 캡처로 따로 본다.
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import LoginPhone from './LoginPhone.jsx';

vi.mock('../../api/http.js', async () => (await import('../../test/fakeHttp.js')).fakeHttpModule());

const here = path.dirname(fileURLToPath(import.meta.url));
const css = fs.readFileSync(path.join(here, 'LoginPhone.module.css'), 'utf8');
const extra = fs.readFileSync(path.join(here, '../../../../../packages/ui/src/tokens/extra.css'), 'utf8');

/** CSS 글자에서 `선택자 { … }` 한 덩어리의 본문. 없으면 '' */
const ruleBody = (text, selector) => text.match(new RegExp(`(^|\\n)${selector.replace('.', '\\.')}\\s*\\{([^}]*)\\}`))?.[2] ?? '';
/** z-index 값: 숫자면 그대로, var(--이름) 이면 extra.css 에서 찾은 숫자 */
function zIndexOf(body) {
  const raw = body.match(/z-index:\s*([^;]+);/)?.[1]?.trim();
  if (!raw) return undefined;
  const token = raw.match(/^var\((--[\w-]+)\)$/)?.[1];
  return Number(token ? extra.match(new RegExp(`${token}:\\s*(-?\\d+)`))?.[1] : raw);
}

describe('Auth/LoginPhone', () => {
  it('제목·입력칸 묶음(.stack)이 일러스트(.hero)보다 위 층이라 일러스트가 전화번호 이름표를 가리지 않는다', () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/auth/login']}>
        <LoginPhone />
      </MemoryRouter>,
    );
    // 전화번호 칸 이름표가 .stack 안에 있고, 일러스트는 .hero 다 (CSS Modules 는 클래스 이름에 원래 이름을 남긴다)
    const stack = screen.getByRole('heading', { name: '로그인하기' }).parentElement;
    expect(stack.className).toMatch(/stack/);
    expect(stack).toContainElement(screen.getByText('전화번호를 입력해주세요'));
    expect(container.querySelector('img').className).toMatch(/hero/);

    const stackRule = ruleBody(css, '.stack');
    expect(stackRule).toMatch(/position:\s*relative/); // z-index 가 먹으려면 위치 지정이 필요하다 (가입 화면 .header 와 같게)
    expect(zIndexOf(stackRule)).toBeGreaterThan(zIndexOf(ruleBody(css, '.hero')));
  });
});
