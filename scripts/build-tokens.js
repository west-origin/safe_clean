#!/usr/bin/env node
// 토큰 생성기
//   입력: design/figma-tokens.json  (design/figma/dump-tokens.figma.js 로 Figma에서 읽은 원본)
//   출력: packages/ui/src/tokens/tokens.css      (변수 32개 → CSS 변수)
//         packages/ui/src/tokens/typography.css  (텍스트 스타일 23개 → --font-size-* 변수 + .text-* 클래스)
//         packages/ui/src/tokens/tokens.js       (JS 에서 참조용)
// 실행: npm run tokens
//
// 값을 여기서 고치지 마세요. Figma를 고치고 → 덤프 → 이 스크립트 순서로만 바꿉니다.
// CI(.github/workflows/ci.yml)는 npm run tokens 를 다시 돌린 뒤 git diff 로 생성물이 최신인지 본다.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'); // 저장소 루트 (이 파일 폴더의 한 단계 위)
const SRC = path.join(ROOT, 'design', 'figma-tokens.json');
const OUT_DIR = path.join(ROOT, 'packages', 'ui', 'src', 'tokens');

// Figma 모드 이름 → 코드의 테마 이름.
// 다크 모드가 생기면 여기에 { 'Dark': 'dark' } 한 줄만 추가하면
// tokens.css 에 :root[data-theme="dark"] 블록이 따라 생깁니다.
const MODE_TO_THEME = {
  'Mode 1': 'default',
};
const DEFAULT_THEME = 'default';

// ── 타이포그래피 설정 (Figma에 없어서 코드에서 정한 값. 바꾸면 PLAN.md §5 도 갱신) ──
// Figma 텍스트 스타일의 line-height 가 전부 AUTO 다.
// Figma 에서 텍스트 박스 높이를 실측하면 모든 크기에서 폰트 크기 × 1.2 (14→17, 16→19, 18→22, 20→24).
// Noto Sans KR OTF 메트릭(880+120+200)/1000 = 1.2 와 일치. 브라우저 normal(1.45) 을 쓰면 카드가 Figma 보다 커진다.
const LINE_HEIGHT_AUTO = 1.2;
// 시스템 한글 폰트 fallback. 첫 항목은 Figma 에서 읽은 family 로 자동 채움.
const FONT_FALLBACK = ['Apple SD Gothic Neo', 'Malgun Gothic', 'system-ui', 'sans-serif'];
// Figma 스타일 이름 → CSS font-weight
const FONT_WEIGHTS = { Regular: 400, Medium: 500, Bold: 700 };

// Figma 변수명 → CSS 변수명.  color/text/primary → --color-text-primary
export const toCssVarName = (figmaName) => '--' + figmaName.replace(/\//g, '-');

// 값 → CSS 문자열.  FLOAT 는 px, alias 는 var(--…)
function toCssValue(variable, raw) {
  if (raw && typeof raw === 'object' && raw.alias) return `var(${toCssVarName(raw.alias)})`;
  if (variable.type === 'FLOAT') return `${raw}px`;
  if (variable.type === 'BOOLEAN') return raw ? '1' : '0';
  return String(raw);
}

// tokens.css 내용을 만든다. Figma 모드마다 :root 블록 하나(기본 테마는 :root 와 data-theme="default" 둘 다에 건다),
// 변수는 이름 첫 부분(color, spacing …)이 바뀔 때마다 그룹 주석을 달고, Figma scopes 가 있으면 줄 끝 주석으로 남긴다.
// MODE_TO_THEME 에 없는 모드가 덤프에 있으면 오류로 멈춘다.
function buildCss(data) {
  const themes = data.modes.map((mode) => {
    const theme = MODE_TO_THEME[mode];
    if (!theme) {
      throw new Error(
        `Figma 모드 "${mode}" 에 대응하는 테마 이름이 없습니다. scripts/build-tokens.js 의 MODE_TO_THEME 에 추가하세요.`
      );
    }
    return { mode, theme };
  });

  const lines = [];
  lines.push('/* 자동 생성 파일 — 직접 수정 금지.');
  lines.push(' * 원본: design/figma-tokens.json  (Figma 컬렉션 "' + data.source.collection + '")');
  lines.push(' * 생성: npm run tokens  (scripts/build-tokens.js)');
  lines.push(' */');
  lines.push('');

  for (const { mode, theme } of themes) {
    const selector =
      theme === DEFAULT_THEME
        ? `:root,\n:root[data-theme="${theme}"]`
        : `:root[data-theme="${theme}"]`;
    lines.push(`${selector} {`);
    let currentGroup = null;
    for (const v of data.variables) {
      const group = v.name.split('/')[0];
      if (group !== currentGroup) {
        if (currentGroup !== null) lines.push('');
        lines.push(`  /* ${group} */`);
        currentGroup = group;
      }
      const scopes = v.scopes && v.scopes.length ? ` /* ${v.scopes.join(', ')} */` : '';
      lines.push(`  ${toCssVarName(v.name)}: ${toCssValue(v, v.values[mode])};${scopes}`);
    }
    lines.push('}');
    lines.push('');
  }
  return lines.join('\n');
}

// 텍스트 스타일 이름 → 클래스 접미사.  Heading/XL → heading-xl, Caption/L-Bold → caption-l-bold
export const toTypoSlug = (name) => name.toLowerCase().replace(/\//g, '-');

// Figma lineHeight → CSS 값. AUTO(또는 없음)는 --line-height-auto 변수, 픽셀은 px, 퍼센트는 배수(150 → 1.5). 그 밖의 단위는 오류
function toLineHeight(lh) {
  if (!lh || lh.unit === 'AUTO') return 'var(--line-height-auto)';
  if (lh.unit === 'PIXELS') return `${lh.value}px`;
  if (lh.unit === 'PERCENT') return String(lh.value / 100);
  throw new Error(`알 수 없는 lineHeight 단위: ${lh.unit}`);
}

// Figma letterSpacing → CSS 값. 퍼센트는 글자 크기 대비라 em 으로 바꾼다(5 → 0.05em). 없거나 0 이면 '0'
function toLetterSpacing(ls) {
  if (!ls || ls.value === 0) return '0';
  if (ls.unit === 'PIXELS') return `${ls.value}px`;
  if (ls.unit === 'PERCENT') return `${ls.value / 100}em`;
  throw new Error(`알 수 없는 letterSpacing 단위: ${ls.unit}`);
}

// Figma 스타일 이름(Regular 등) → var(--font-weight-regular) 참조. FONT_WEIGHTS 에 없는 이름이면 오류로 멈춘다
function weightVar(style) {
  const w = FONT_WEIGHTS[style];
  if (!w) throw new Error(`font-weight 매핑 없음: "${style}". scripts/build-tokens.js 의 FONT_WEIGHTS 에 추가하세요.`);
  return `var(--font-weight-${style.toLowerCase()})`;
}

// typography.css 내용을 만든다: :root 에 글꼴 스택·굵기·줄 높이·크기 변수, 그리고 텍스트 스타일마다 .text-<이름> 클래스.
// 글꼴 스택 변수를 하나만 만들기 때문에 family 가 정확히 하나가 아니면(없거나 둘 이상) 오류로 멈춘다.
function buildTypographyCss(data) {
  const styles = data.textStyles || [];
  const families = [...new Set(styles.map((s) => s.family))];
  if (families.length !== 1) throw new Error(`폰트 family 가 1개여야 합니다. 발견: ${families.join(', ')}`);
  // 첫 글꼴(Figma family, 늘 따옴표) 뒤에 대체 글꼴을 잇는다. 대체 글꼴은 이름에 공백이 있을 때만 따옴표로 감싼다
  const familyStack = [`"${families[0]}"`, ...FONT_FALLBACK.map((f) => (f.includes(' ') ? `"${f}"` : f))].join(', ');

  const lines = [];
  lines.push('/* 자동 생성 파일 — 직접 수정 금지.');
  lines.push(' * 원본: design/figma-tokens.json 의 textStyles (Figma 텍스트 스타일 ' + styles.length + '개)');
  lines.push(' * 생성: npm run tokens  (scripts/build-tokens.js)');
  lines.push(' * 사용: <h1 className="text-heading-xl">, <p className="text-body-default"> …');
  lines.push(' */');
  lines.push('');
  lines.push(':root {');
  lines.push(`  --font-family-sans: ${familyStack};`);
  for (const [style, w] of Object.entries(FONT_WEIGHTS)) lines.push(`  --font-weight-${style.toLowerCase()}: ${w};`);
  lines.push(`  --line-height-auto: ${LINE_HEIGHT_AUTO}; /* Figma line-height AUTO 대응. 코드에서 정한 값 (PLAN.md §5) */`);
  lines.push('');
  lines.push('  /* font-size */');
  for (const st of styles) lines.push(`  --font-size-${toTypoSlug(st.name)}: ${st.size}px;`);
  lines.push('}');
  lines.push('');
  for (const st of styles) {
    const slug = toTypoSlug(st.name);
    lines.push(`/* ${st.name} — ${st.family} ${st.style} ${st.size} */`);
    lines.push(`.text-${slug} {`);
    lines.push('  font-family: var(--font-family-sans);');
    lines.push(`  font-size: var(--font-size-${slug});`);
    lines.push(`  font-weight: ${weightVar(st.style)};`);
    lines.push(`  line-height: ${toLineHeight(st.lineHeight)};`);
    lines.push(`  letter-spacing: ${toLetterSpacing(st.letterSpacing)};`);
    lines.push('  word-break: keep-all; /* 한글: 어절 단위 줄바꿈 */');
    lines.push('  overflow-wrap: break-word;');
    lines.push('}');
    lines.push('');
  }
  return lines.join('\n');
}

// tokens.js 내용을 만든다: tokens(CSS 변수 참조 중첩 객체)·tokenValues(원본 값)·cssVar·tokenNames·textStyles
function buildJs(data) {
  // tokens: 중첩 객체. tokens.color.brand.primary === 'var(--color-brand-primary)'
  const tokens = {};
  for (const v of data.variables) {
    const parts = v.name.split('/');
    let node = tokens;
    for (let i = 0; i < parts.length - 1; i++) {
      node[parts[i]] = node[parts[i]] || {};
      node = node[parts[i]];
    }
    node[parts[parts.length - 1]] = `var(${toCssVarName(v.name)})`;
  }

  // tokenValues: 테마별 실제 값. JS에서 숫자/색 자체가 필요할 때(캔버스 등)만 사용.
  const tokenValues = {};
  for (const mode of data.modes) {
    const theme = MODE_TO_THEME[mode];
    tokenValues[theme] = {};
    for (const v of data.variables) {
      const raw = v.values[mode];
      tokenValues[theme][v.name] = raw;
    }
  }

  return [
    '// 자동 생성 파일 — 직접 수정 금지.',
    '// 원본: design/figma-tokens.json / 생성: npm run tokens (scripts/build-tokens.js)',
    '',
    '/** CSS 변수 참조. 예: tokens.color.brand.primary → "var(--color-brand-primary)" */',
    `export const tokens = ${JSON.stringify(tokens, null, 2)};`,
    '',
    '/** 테마별 원본 값. 예: tokenValues.default["color/brand/primary"] → "#2284fc" */',
    `export const tokenValues = ${JSON.stringify(tokenValues, null, 2)};`,
    '',
    '/** Figma 변수명으로 CSS 변수 참조 만들기. cssVar("color/text/primary") → "var(--color-text-primary)" */',
    "export const cssVar = (figmaName) => `var(--${figmaName.replace(/\\//g, '-')})`;",
    '',
    `export const tokenNames = ${JSON.stringify(data.variables.map((v) => v.name))};`,
    '',
    '/** Figma 텍스트 스타일 → 클래스/변수. 예: textStyles["Heading/XL"].className → "text-heading-xl" */',
    `export const textStyles = ${JSON.stringify(
      Object.fromEntries(
        (data.textStyles || []).map((st) => [
          st.name,
          {
            className: `text-${toTypoSlug(st.name)}`,
            fontSize: `var(--font-size-${toTypoSlug(st.name)})`,
            fontWeight: FONT_WEIGHTS[st.style],
            size: st.size,
          },
        ])
      ),
      null,
      2
    )};`,
    '',
  ].join('\n');
}

// 원본 JSON 을 읽어 세 파일을 쓰고 개수를 돌려준다. src·outDir 를 넘기면 다른 원본·다른 폴더로 만들 수 있다
export function build({ src = SRC, outDir = OUT_DIR } = {}) {
  const data = JSON.parse(fs.readFileSync(src, 'utf8'));
  fs.mkdirSync(outDir, { recursive: true });
  const css = buildCss(data);
  const typo = buildTypographyCss(data);
  const js = buildJs(data);
  fs.writeFileSync(path.join(outDir, 'tokens.css'), css);
  fs.writeFileSync(path.join(outDir, 'typography.css'), typo);
  fs.writeFileSync(path.join(outDir, 'tokens.js'), js);
  return { count: data.variables.length, textStyleCount: (data.textStyles || []).length, modes: data.modes, outDir };
}

// 직접 실행했을 때만 파일을 씁니다 (테스트에서 import 할 때는 실행 안 됨).
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const r = build();
  console.log(
    `토큰 ${r.count}개, 텍스트 스타일 ${r.textStyleCount}개, 모드 ${r.modes.join(', ')} → ${path.relative(ROOT, r.outDir)}/tokens.css, typography.css, tokens.js`
  );
}
