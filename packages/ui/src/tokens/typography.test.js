// 타이포그래피 생성물 검증: JSON(textStyles) ↔ typography.css / tokens.js
// design/figma-tokens.json 의 텍스트 스타일이 scripts/build-tokens.js 로 빠짐없이 .text-* 클래스와
// --font-size-* 변수가 되었는지, 그리고 글꼴 파일 등록(fonts.css)이 앱 용량을 줄인 구성 그대로인지 본다.
import { describe, it, expect } from 'vitest';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { textStyles } from './tokens.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const source = JSON.parse(fs.readFileSync(path.resolve(here, '../../../../design/figma-tokens.json'), 'utf8'));
const css = fs.readFileSync(path.join(here, 'typography.css'), 'utf8');
// Figma 글꼴 스타일 이름 → CSS 굵기 숫자
const WEIGHT = { Regular: 400, Medium: 500, Bold: 700 };

describe('타이포그래피 생성물', () => {
  it('텍스트 스타일 23개가 전부 클래스로 생성된다', () => {
    expect(source.textStyles.length).toBe(23);
    expect(Object.keys(textStyles).length).toBe(23);
    for (const st of source.textStyles) {
      // Figma 이름 "Heading/XL" → 클래스 이름에 쓰는 "heading-xl" (소문자, 슬래시를 하이픈으로)
      const slug = st.name.toLowerCase().replace(/\//g, '-');
      expect(css, `.text-${slug} 누락`).toContain(`.text-${slug} {`);
      expect(css).toContain(`--font-size-${slug}: ${st.size}px;`);
      expect(textStyles[st.name].className).toBe(`text-${slug}`);
    }
  });

  it('굵기는 Regular/Medium/Bold(400/500/700)만 쓴다', () => {
    for (const st of source.textStyles) {
      expect(Object.keys(WEIGHT)).toContain(st.style);
      expect(textStyles[st.name].fontWeight).toBe(WEIGHT[st.style]);
    }
    expect(css).toContain('--font-weight-regular: 400;');
    expect(css).toContain('--font-weight-medium: 500;');
    expect(css).toContain('--font-weight-bold: 700;');
  });

  it('폰트는 Noto Sans KR + 시스템 한글 fallback', () => {
    expect(css).toMatch(/--font-family-sans: "Noto Sans KR", "Apple SD Gothic Neo", "Malgun Gothic", system-ui, sans-serif;/);
  });

  it('모든 클래스가 한글 줄바꿈(keep-all)과 line-height 변수를 쓴다', () => {
    // 줄 맨 앞의 ".text-" 로 잘라 클래스 규칙마다 한 조각씩 만든다. 첫 조각은 클래스 앞의 변수 선언부라 버린다.
    const blocks = css.split('\n.text-').slice(1);
    expect(blocks.length).toBe(23);
    for (const b of blocks) {
      expect(b).toContain('word-break: keep-all;');
      expect(b).toContain('overflow-wrap: break-word;');
      expect(b).toContain('line-height: var(--line-height-auto);');
      expect(b).toContain('font-family: var(--font-family-sans);');
    }
  });

  it('폰트 파일은 400/500/700 세 굵기 · korean/latin · woff2 만 쓴다 (단계 6 번들 축소)', () => {
    // fonts.css 의 @font-face 에서 굵기 숫자를 모아 중복을 뺀다 → 400·500·700 세 가지만 있어야 한다.
    // 파일 이름은 noto-sans-kr-(korean|latin)-(굵기)-normal.woff2 형태로 6개(2 글자군 × 3 굵기)여야 한다.
    const fonts = fs.readFileSync(path.resolve(here, '../fonts.css'), 'utf8');
    const weights = [...new Set((fonts.match(/font-weight: (\d+);/g) || []).map((m) => m.match(/\d+/)[0]))];
    expect(weights).toEqual(['400', '500', '700']);
    const files = fonts.match(/noto-sans-kr-[a-z]+-\d+-normal\.woff2?/g) || [];
    expect(files).toHaveLength(6);
    expect(files.every((f) => f.endsWith('.woff2'))).toBe(true);
    expect(files.filter((f) => f.includes('korean'))).toHaveLength(3);
  });
});
