# 안심클린 앱

Mac의 iOS/Android 개발 빌드는 [Mac 빌드 안내](docs/mac-build.md)를 따른다.

고객용 앱과 청소매니저 앱. React + Vite로 만들고 Capacitor로 iOS/Android 앱으로 감싼다.
사용 언어는 HTML / CSS / JavaScript(JSX)뿐이다. TypeScript, Tailwind는 쓰지 않는다.

## 실행

```bash
npm install            # 루트에서 한 번 (모든 패키지 설치)
npm run dev:customer   # 고객용 앱  http://localhost:5173
npm run dev:manager    # 매니저 앱  http://localhost:5174
```

## 자주 쓰는 명령

| 명령 | 하는 일 |
|---|---|
| `npm run tokens` | `design/figma-tokens.json` → `packages/ui/src/tokens/*` 재생성 |
| `npm run check:tokens` | 코드에 하드코딩된 색상(hex)·폰트 크기(px)가 있으면 실패 |
| `npm test` | Vitest 테스트 실행 |
| `npm run lint` | ESLint |
| `npm run format` | Prettier |

## 폴더

```
design/            Figma에서 읽은 원본 값(JSON)과 읽기 스크립트
scripts/           토큰 생성기, 검사 스크립트 (Node)
packages/ui/       디자인 시스템 (토큰·타이포·컴포넌트) — 두 앱 공용
apps/customer/     고객용 앱
apps/manager/      매니저 앱
backend/           API 서버 (Java 21 + Spring Boot, 백엔드 컴퓨터에서 작업) — 사양은 BACKEND_PLAN.md
```

백엔드 실행은 `backend/README.md` (`./mvnw spring-boot:run`). 앱은 `VITE_API_URL` 로 그 서버를 부른다 (단계 B2 부터). 서버 없이 화면만 보려면 `npm run dev:customer:mock`(5175) · `npm run dev:manager:mock`(5176) — 테스트용 가짜 서버(`src/test/fakeHttp.js`), DEV 전용.

자세한 계획은 `PLAN.md`, 진행 상태는 `PROGRESS.md`.

운영 API 서버는 **Google Cloud Run 서울 리전**에 배포한다. Supabase는 이전한 서울 프로젝트를 계속 쓴다.
새 GCP 프로젝트 생성, Secret Manager, 소스 배포·검증·롤백은 [배포 안내](deploy/cloudrun/README.md)를 따른다.
