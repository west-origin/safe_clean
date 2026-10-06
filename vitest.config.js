// 테스트 실행기(Vitest) 설정. `npm test`(= vitest run) 가 저장소 루트에서 이 파일 하나로 두 앱·ui·스크립트 테스트를 모두 돌린다.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()], // 앱 빌드와 같은 방식으로 JSX 를 변환한다 (파일마다 import React 를 쓰지 않아도 되는 방식)
  test: {
    // jsdom: Node 안에서 흉내 낸 브라우저 DOM. render()·클릭 같은 화면 테스트가 실제 브라우저 없이 돈다
    environment: 'jsdom',
    include: ['packages/**/*.test.{js,jsx}', 'apps/**/*.test.{js,jsx}', 'scripts/**/*.test.js'],
    setupFiles: ['./vitest.setup.js'], // 테스트 파일마다 그 파일보다 먼저 실행된다 (공통 준비·정리)
  },
});
