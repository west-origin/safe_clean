// 토큰 파이프라인 검증: JSON(원본) ↔ 생성물(tokens.css, tokens.js)이 일치하는지
// 원본 design/figma-tokens.json 은 Figma 에서 뽑은 변수 목록이고, scripts/build-tokens.js(npm run tokens)가
// 이것으로 tokens.css·tokens.js 를 만든다. 생성물을 손으로 고쳤거나, JSON 을 바꾸고 다시 만들지 않았으면 여기서 실패한다.
import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { tokens, tokenValues, tokenNames, cssVar } from './tokens.js';

// 이 테스트 파일이 있는 폴더(packages/ui/src/tokens). 원본 JSON 은 저장소 루트의 design/ 에 있어 네 단계 위로 올라간다.
const here = path.dirname(fileURLToPath(import.meta.url));
const source = JSON.parse(fs.readFileSync(path.resolve(here, '../../../../design/figma-tokens.json'), 'utf8'));
// 줄바꿈은 \n 으로 맞춘다 — Windows 에서 git(core.autocrlf)이 CRLF 로 내려받으면 아래 줄 단위 정규식이 맞지 않았다
const css = fs.readFileSync(path.join(here, 'tokens.css'), 'utf8').replace(/\r\n/g, '\n');

describe('토큰 생성물', () => {
  it('Figma 변수 수와 생성된 토큰 수가 같다 (32개)', () => {
    expect(source.variables.length).toBe(32);
    expect(tokenNames.length).toBe(source.variables.length);
  });

  it('모든 변수가 tokens.css 에 CSS 변수로 들어있다', () => {
    for (const v of source.variables) {
      // Figma 이름 "color/brand/primary" → CSS 변수 이름 "--color-brand-primary" (슬래시를 하이픈으로)
      const name = '--' + v.name.replace(/\//g, '-');
      expect(css, `${name} 누락`).toContain(`${name}:`);
    }
  });

  it('값이 원본과 같다 (색은 hex, FLOAT 는 px)', () => {
    for (const v of source.variables) {
      // 'Mode 1' 은 Figma 의 기본 모드 이름이다 (build-tokens.js 가 이것을 default 테마로 바꾼다).
      // 간격·radius 같은 숫자(FLOAT) 변수는 CSS 에 px 를 붙여 쓰므로 기대값에도 px 를 붙인다.
      const raw = v.values['Mode 1'];
      const expected = v.type === 'FLOAT' ? `${raw}px` : raw;
      const name = '--' + v.name.replace(/\//g, '-');
      expect(css).toContain(`${name}: ${expected};`);
      expect(tokenValues.default[v.name]).toBe(raw);
    }
  });

  it('중첩 객체와 cssVar 가 같은 var() 문자열을 만든다', () => {
    expect(tokens.color.brand.primary).toBe('var(--color-brand-primary)');
    expect(cssVar('color/brand/primary')).toBe(tokens.color.brand.primary);
    expect(tokens.spacing['2xs']).toBe('var(--spacing-2xs)');
    expect(tokens.radius.pill).toBe('var(--radius-pill)');
  });

  // tokens.css 에 ":root," 다음 줄 ":root[data-theme="default"] {" 로 시작하는 선택자 묶음이 있는지 본다.
  // data-theme 를 달지 않은 페이지도 :root 쪽으로 기본 값이 적용된다.
  it('기본 테마 블록은 :root 와 data-theme="default" 둘 다에 적용된다', () => {
    expect(css).toMatch(/:root,\n:root\[data-theme="default"\] \{/);
  });
});
