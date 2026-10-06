// 프로덕션 빌드의 index.html 에 Content-Security-Policy 메타 태그를 넣는다.
// 개발 서버(HMR·React Refresh 는 인라인 스크립트를 쓴다)에는 넣지 않는다.
// connect-src: API·LiveKit 주소가 정해지면 https:/wss: 대신 그 도메인만 남긴다.
// frame-ancestors 는 <meta> 로는 무시되므로 배포 서버의 응답 헤더(Content-Security-Policy)에 넣는다.
// 두 앱의 vite.config.js 가 plugins 에 cspPlugin() 을 넣는다.
// CSP: 이 페이지가 스크립트·스타일·이미지·연결을 어디서 받아도 되는지 브라우저에 알려 주는 목록. 목록 밖은 브라우저가 막는다.
// 'self' = 앱과 같은 출처, data: = 내용을 주소에 직접 담은 것, blob:·mediastream: = 코드가 메모리·카메라 스트림으로 만든 주소,
// https:·wss: = 그 방식이면 어느 도메인이든.
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'", // CSS Modules 는 파일이지만 Skeleton·진행 막대는 style 속성을 쓴다
  "img-src 'self' data: blob: https:", // 수리 사진 미리보기·기사 화면의 첨부 사진 = Supabase Storage 서명 URL
  "font-src 'self'",
  "media-src 'self' blob: mediastream: https:", // 라이브 <video> + 녹화본(Supabase Storage 서명 URL)
  "connect-src 'self' https: wss:",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

// Vite 플러그인 객체를 돌려준다. apply: 'build' = 빌드 때만 동작, transformIndexHtml = index.html 을 내보내기 전에 고치는 자리
export function cspPlugin() {
  return {
    name: 'ansimclean-csp',
    apply: 'build',
    transformIndexHtml(html) {
      // charset 메타 태그 바로 뒤에 CSP 메타 태그를 끼운다 — 두 앱 index.html 에 이 글자 그대로의 태그가 있어야 한다
      const marker = '<meta charset="UTF-8" />';
      if (!html.includes(marker)) throw new Error('vite-csp-plugin: index.html 에서 <meta charset="UTF-8" /> 를 찾지 못해 CSP 를 넣을 수 없어요'); // 조용히 빠지면 CSP 없는 빌드가 나간다
      return html.replace(marker, `${marker}\n    <meta http-equiv="Content-Security-Policy" content="${CSP}" />`);
    },
  };
}
