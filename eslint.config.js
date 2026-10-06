// ESLint 설정 (flat config 형식). `npm run lint`(= eslint .) 와 CI(.github/workflows/ci.yml) 가 이 파일을 읽는다.
// 아래 배열의 항목 중 파일 경로가 맞는 것들이 위에서부터 차례로 겹쳐 적용된다 (같은 설정이면 뒤 항목이 이긴다).
//   ① 검사하지 않을 폴더 ② JS 기본 권장 규칙 ③ 모든 JS·JSX 파일(브라우저 전역·React 훅 규칙) ④ Node 로 도는 스크립트·설정 파일
import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';

export default [
  // backend/target 은 Maven·JaCoCo 가 만든 산출물이다 (JS 리포트 파일이 들어 있다)
  { ignores: ['**/dist/**', '**/node_modules/**', 'design/figma/**', 'apps/*/ios/**', 'apps/*/android/**', 'backend/target/**'] },
  js.configs.recommended,
  // 저장소의 모든 JS·JSX 파일에 적용된다 (앱·ui·스크립트 모두)
  {
    files: ['**/*.{js,jsx,mjs}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: { ...globals.browser }, // 앱·ui 는 브라우저 코드 — node 전역(process 등)을 쓰면 번들에서 깨진다. 스크립트·설정은 아래에서 따로 준다
      parserOptions: { ecmaFeatures: { jsx: true }, sourceType: 'module' },
    },
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    rules: {
      // 훅 규칙: 훅은 조건문·반복문 밖(컴포넌트·훅 함수의 최상위)에서만 부른다, useEffect 등의 의존성 배열 누락을 알린다.
      // 이 플러그인 7판의 권장 묶음에는 렌더 중 setState·ref 읽기 같은 React 컴파일러 기반 검사도 들어 있다
      ...reactHooks.configs.recommended.rules,
      // .jsx 파일이 컴포넌트가 아닌 것(함수·배열·객체 등)을 함께 export 하면 개발 서버의 즉시 반영(Fast Refresh)이
      // 안 되고 페이지 전체를 다시 읽게 돼 경고한다. 문자열·숫자 같은 단순 상수 export 만 허용한다 (.js 파일은 보지 않는다)
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      'no-unused-vars': ['error', { varsIgnorePattern: '^_' }], // JSX 컴포넌트 참조는 사용으로 집계되므로 대문자 면제는 필요 없다
    },
  },
  // Node 에서 실행되는 파일: 위 브라우저 전역에 node 전역(process 등)을 더한다 (languageOptions 는 합쳐진다)
  {
    files: ['scripts/**/*.{js,mjs}', '*.config.js', 'vitest.setup.js', 'apps/*/vite.config.js'],
    languageOptions: { globals: { ...globals.node } },
  },
];
