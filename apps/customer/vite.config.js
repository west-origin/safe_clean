// 고객 앱의 Vite 설정 (개발 서버 `npm run dev:customer` = apps/customer 에서 `vite`, 빌드 `vite build`).
// 하는 일: React 플러그인, 빌드 때만 CSP 메타 태그 삽입, package.json 버전을 상수로 주입,
// 프로덕션 빌드에서 VITE_API_URL 이 비었거나 http:// 이면 경고. 개발 서버 포트는 5173 (매니저 앱은 5174).
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { cspPlugin } from '../../scripts/vite-csp-plugin.js';
import { createRequire } from 'node:module';
// 이 설정 파일은 ES 모듈이라 require 가 없다. createRequire 로 require 를 만들어 package.json(JSON)을 읽는다.

const { version } = createRequire(import.meta.url)('./package.json'); // 설정 화면 '버전 정보' — DB 고정 문자열 대신 실제 빌드 (검증 검토)

// mode: `vite`(개발 서버)는 'development', `vite build` 는 'production'.
export default defineConfig(({ mode }) => {
  // 실행 폴더(npm -w 로 돌리면 apps/customer)의 .env, .env.local, .env.<mode> 등에서 VITE_ 로 시작하는 값만 읽는다
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  if (mode === 'production') {
    // Capacitor 앱은 CSP(connect-src https:)와 allowMixedContent=false 라 http:// API 는 통째로 막힌다. 비어 있으면 /api/v1(웹 프록시에서만 동작)
    if (!env.VITE_API_URL) console.warn('[ansimclean] VITE_API_URL 이 비어 있어요 — Capacitor 앱에서는 API 를 부를 수 없습니다 (.env.production)');
    else if (env.VITE_API_URL.startsWith('http://')) console.warn(`[ansimclean] VITE_API_URL 이 http:// 입니다 (${env.VITE_API_URL}) — 앱의 CSP 가 막습니다. https:// 로 바꾸세요`);
  }
  // 개발 서버에서 VITE_API_URL 이 다른 출처(예: Cloud Run 테스트 서버)면 브라우저가 CORS 로 막는다 — 배포 서버는 앱 출처(capacitor://localhost 등)만
  // 허용하고 http://localhost:5173 은 일부러 넣지 않았다 (backend/.env.example). 그래서 로그인이 '네트워크 연결을 확인해주세요' 로 실패했다.
  // 개발 서버가 그 주소로 대신 전달(proxy)하고, 앱은 같은 출처의 경로(예: /api/v1)만 부르게 바꾼다. 빌드(앱)에는 영향 없다.
  const apiTarget = mode === 'development' && /^https?:\/\//.test(env.VITE_API_URL ?? '') ? new URL(env.VITE_API_URL) : null;
  return {
  // define: 코드 안의 import.meta.env.VITE_APP_VERSION 글자를 버전 문자열로 바꿔 넣는다 (screens/settings/Settings.jsx 의 버전 행이 읽는다)
  define: {
    'import.meta.env.VITE_APP_VERSION': JSON.stringify(version),
    ...(apiTarget && { 'import.meta.env.VITE_API_URL': JSON.stringify(apiTarget.pathname) }), // 개발 서버: 같은 출처의 경로로 (아래 proxy 가 전달)
  },
  // react(): JSX 변환 + 개발 중 화면 즉시 갱신. cspPlugin(): 빌드 때만 index.html 에 CSP 를 넣는다
  plugins: [react(), cspPlugin({ payments: true })],
  server: {
    port: 5173, // .claude/launch.json 의 customer 와 같은 포트. 가짜 서버 모드(dev:customer:mock)는 --port 5175 로 띄운다
    // Origin 헤더를 지워 보낸다 — 남겨 두면 서버(Spring)가 여전히 CORS 요청으로 보고 허용 목록에 없는 localhost 를 403 으로 막는다
    proxy: apiTarget ? { [apiTarget.pathname]: { target: apiTarget.origin, changeOrigin: true, configure: (proxy) => proxy.on('proxyReq', (req) => req.removeHeader('origin')) } } : undefined,
  },
  optimizeDeps: {
    // @ansimclean/ui 는 워크스페이스 링크 패키지라 Vite 가 그 안의 react 사용을 뒤늦게 발견하고
    // 재최적화하면서 React 가 두 벌 로드되는 문제가 있다("Invalid hook call"). 미리 목록에 넣어 막는다.
    include: ['react', 'react-dom', 'react-dom/client', 'react/jsx-runtime', 'react/jsx-dev-runtime', 'react-router'],
  },
  };
});
