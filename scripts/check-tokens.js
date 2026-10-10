#!/usr/bin/env node
// 하드코딩 검사
//   packages/, apps/ 아래 전체(src 밖의 index.html·vite.config.js 포함)의 .css .js .jsx .html 에서
//   hex 색상, rgb()/hsl(), px 폰트 크기를 직접 쓴 줄을 찾으면 실패(exit 1)합니다.
//   CSS 에 숫자로 쓴 굵기·줄 높이와 크기가 든 font 축약 속성도 잡습니다 (아래 RULES).
//   토큰 파일(tokens/), 폰트 파일, 에셋, 아이콘 SVG, 테스트 파일, Capacitor 네이티브 프로젝트(apps/*/ios, android)는 검사하지 않습니다.
//   예외가 꼭 필요하면 그 줄에 `token-ok` 주석을 답니다.  예) color: #fff; /* token-ok: 캔버스 캡처 배경 */
// 실행: npm run check:tokens

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'); // 저장소 루트 (이 파일 폴더의 한 단계 위)
const SCAN_ROOTS = ['packages', 'apps']; // 이 폴더 아래 전부를 돈다 (src 밖의 index.html·vite.config.js 도 포함)
const EXTENSIONS = new Set(['.css', '.js', '.jsx', '.html']);
// 검사에서 뺄 경로(정규식): node_modules·dist·tokens·assets·icons 폴더, fonts.css, 테스트 파일, Capacitor 네이티브 폴더
const SKIP_PATH = [/\/node_modules\//, /\/dist\//, /\/tokens\//, /\/assets\//, /\/icons\//, /fonts\.css$/, /\.test\.jsx?$/, /\/apps\/[^/]+\/(ios|android)\//];

// 규칙마다 이름·정규식·적용할 확장자. 한 줄이 여러 규칙에 걸리면 규칙마다 따로 보고한다.
// hex 규칙은 뒤에 글자·숫자·밑줄·하이픈이 이어지면 색으로 보지 않는다 — 더 긴 이름(id·앵커 등)의 앞부분이
// 우연히 hex 처럼 보여도 잡지 않으려는 것.
// 굵기·줄 높이 규칙은 CSS 에 숫자를 직접 쓴 경우만 잡는다 — var(--…) 값은 통과한다.
const RULES = [
  { name: 'hex 색상', re: /#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{3,4})(?![\w-])/i, ext: ['.css', '.js', '.jsx', '.html'] },
  { name: 'rgb/hsl 색상', re: /\b(?:rgba?|hsla?)\(/i, ext: ['.css', '.js', '.jsx', '.html'] },
  { name: 'font-weight/line-height (CSS — .text-* 클래스로)', re: /(?:font-weight|line-height)\s*:\s*[\d.]+(?:px|%|em|rem)?\s*[;}]/, ext: ['.css'] },
  { name: 'px 폰트 크기 (CSS)', re: /font-size\s*:\s*[\d.]+(?:px|pt|rem|em)/, ext: ['.css', '.html'] },
  { name: 'px 폰트 크기 (JSX)', re: /fontSize\s*:\s*['"]?[\d.]+/, ext: ['.js', '.jsx'] },
  { name: 'font 축약 속성', re: /\bfont\s*:\s*[^;]*\d+(?:px|pt|rem|em)/, ext: ['.css'] },
];

// Windows 경로(\ 구분자)를 / 로 바꾼다 — SKIP_PATH·네이티브 폴더 정규식이 / 기준이라, 바꾸지 않으면 Windows 에서 tokens/·android/ 를 걸러내지 못했다
const toSlash = (p) => p.split(path.sep).join('/');

// dir 아래에서 검사할 확장자의 파일을 재귀로 모은다. node_modules·dist 와 apps/<앱>/ios·android 폴더에는 들어가지 않는다
function walk(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === 'dist') continue;
      if ((entry.name === 'ios' || entry.name === 'android') && /\/apps\/[^/]+$/.test(toSlash(dir))) continue; // Capacitor 네이티브 프로젝트 (생성물)
      walk(full, out);
    } else if (EXTENSIONS.has(path.extname(entry.name))) {
      out.push(full);
    }
  }
  return out;
}

// 파일을 한 줄씩 읽어 규칙에 걸린 줄을 모은다. 결과는 { scanned: 검사한 파일 수, findings: [{ file, line, rule, text }] }
export function check(root = ROOT) {
  const files = SCAN_ROOTS.flatMap((r) => walk(path.join(root, r))).filter(
    (f) => !SKIP_PATH.some((re) => re.test(toSlash(f)))
  );
  const findings = [];
  for (const file of files) {
    const ext = path.extname(file);
    const lines = fs.readFileSync(file, 'utf8').split('\n');
    lines.forEach((line, i) => {
      // 예외 표시 주석이 있는 줄은 건너뛴다 (파일 머리 설명의 예외 방법)
      if (line.includes('token-ok')) return;
      for (const rule of RULES) {
        if (!rule.ext.includes(ext)) continue;
        if (rule.re.test(line)) {
          findings.push({ file: path.relative(root, file), line: i + 1, rule: rule.name, text: line.trim() });
        }
      }
    });
  }
  return { scanned: files.length, findings };
}

// 직접 실행했을 때만(npm run check:tokens): 결과를 출력하고 하나라도 걸리면 exit 1 — CI 도 이 명령으로 실패를 본다
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { scanned, findings } = check();
  if (findings.length === 0) {
    console.log(`✓ 하드코딩 없음 (${scanned}개 파일 검사)`);
    process.exit(0);
  }
  console.error(`✗ 하드코딩 ${findings.length}건 (${scanned}개 파일 검사)`);
  for (const f of findings) console.error(`  ${f.file}:${f.line}  [${f.rule}]  ${f.text}`);
  console.error('\n토큰(var(--…))을 사용하세요. 불가피하면 그 줄에 token-ok 주석을 답니다.');
  process.exit(1);
}
