# 고객앱 로그인 실패 (01022320688) — 원인과 해결

작성 2026-10-31

## 증상

고객앱 개발 서버(`npm run dev:customer`, http://localhost:5173)에서 휴대폰 `010-2232-0688` / 비밀번호 `a123456789` 로 로그인하면
홈으로 가지 않고 비밀번호 칸 아래에 **"네트워크 연결을 확인해주세요"** 가 뜬다.
(비밀번호가 틀렸을 때 나오는 "휴대폰 번호 또는 비밀번호를 확인해주세요" 가 아니다.)

## 어떻게 찾았나

1. **로그인 코드 흐름 확인**
   `LoginPhone.jsx` → `loginWithPhone()`(`apps/customer/src/api/customer.js`) → `http.post('/auth/login', { phone, password, role: 'customer' })`.
   `PhoneField` 가 앞자리 010 을 떼므로 서버에는 `phone: "22320688"` 이 간다 (11자리 `01022320688` 도 서버가 받는다).
   요청 주소는 `VITE_API_URL` 이고, 개발 모드에서는 `.env.development.local` 이 가장 우선이라
   **Cloud Run 테스트 서버**(`https://ansimclean-api-test-…a.run.app/api/v1`)로 간다.

2. **서버를 직접 호출해 계정·비밀번호 확인 (curl)**

   | 서버 | 결과 |
   |---|---|
   | 테스트 서버 (`ansimclean-api-test`) | **200** — 토큰 발급, `name: "Seo"`, `profileComplete: true` |
   | 운영 서버 (`ansimclean-api`) | 401 `LOGIN_FAILED` — 이 계정은 운영 DB 에 없다 |

   → 테스트 서버에서는 계정·비밀번호·서버 로직 모두 정상. 문제는 앱(브라우저) 쪽.

3. **브라우저에서 그대로 재현**
   개발 서버를 띄워 같은 번호·비밀번호로 로그인 → "네트워크 연결을 확인해주세요". 콘솔:

   ```
   Access to fetch at 'https://ansimclean-api-test-….a.run.app/api/v1/auth/login' from origin 'http://localhost:5173'
   has been blocked by CORS policy: Response to preflight request doesn't pass access control check
   ```

4. **CORS preflight 를 출처별로 확인 (curl `OPTIONS`)**

   | Origin | 응답 |
   |---|---|
   | `http://localhost:5173` | **403** (Access-Control-Allow-Origin 없음) |
   | `capacitor://localhost`, `https://localhost` | 200 (허용) |

   테스트 서버의 `ALLOWED_ORIGINS` 는 앱 출처만 허용한다 (`deploy/cloudrun/.env.example`).
   `http://localhost:*` 는 보안 검토(2026-09-22) 때 배포 서버에 넣지 않기로 한 값이다 (`backend/.env.example` 주석).

## 원인

개발 서버(http://localhost:5173)의 브라우저가 **다른 출처의 테스트 서버를 직접 호출**했고, 서버가 이 출처를 CORS 로 거절했다.
fetch 가 실패하니 `http.js` 가 `ApiError(0, 'NETWORK')` 로 바꿨고, 화면은 이를 "네트워크 연결을 확인해주세요" 로 보여 줬다.
계정·비밀번호 문제는 아니었다.

## 해결

서버의 보안 설정(ALLOWED_ORIGINS)은 그대로 두고, **개발 서버가 API 요청을 대신 전달(proxy)** 하게 했다.
수정 파일: `apps/customer/vite.config.js`

- 개발 모드이고 `VITE_API_URL` 이 `https://…` 같은 다른 출처일 때만 동작한다.
- 앱 코드의 `import.meta.env.VITE_API_URL` 을 같은 출처의 경로(`/api/v1`)로 바꾼다 → 브라우저는 `http://localhost:5173/api/v1/...` 를 부른다 (CORS 없음).
- Vite `server.proxy` 가 `/api/v1` 을 테스트 서버로 전달한다 (`changeOrigin: true`).
- 전달할 때 `Origin` 헤더를 지운다. 남겨 두면 Spring 이 여전히 CORS 요청으로 보고 403 을 낸다.
- 빌드(`vite build`, `--mode staging`, Capacitor 앱)에는 영향이 없다 — 빌드 결과에는 여전히 절대 주소(`https://…a.run.app/api/v1`)가 들어간다.

## 확인

- 브라우저: 같은 번호·비밀번호로 로그인 → `/home` 으로 이동 (성공).
- `curl -X POST http://localhost:5173/api/v1/auth/login -H 'Origin: http://localhost:5173' …` → 200.
- `npm run lint` 통과, `npm run build -w apps/customer` 통과, `apps/customer/src/api` 테스트 44개 통과.

## 2차: Android Studio 에서 실행한 앱도 로그인 실패

위 수정은 개발 서버(브라우저)용이라 Android Studio 로 띄운 앱에는 효과가 없었다. 원인은 다르다.

### 어떻게 찾았나

1. Android Studio 가 설치하는 웹 번들 `apps/customer/android/app/src/main/assets/public/` 을 열어 봤다.
   `index.html` → `assets/index-*.js` → `assets/http-*.js` 순서로 실제로 쓰는 파일을 따라가니 API 주소가
   **운영 서버** `https://ansimclean-api-rbtzr3o4cq-du.a.run.app/api/v1` 이었다 (2026-10-30 11:59 빌드).
   `npm run cap:sync` 은 `vite build`(production 모드)라 `.env.production` 의 운영 주소를 넣는다.
2. 앱과 같은 출처(`Origin: https://localhost`)로 서버를 직접 호출했다.

   | 서버 | 결과 |
   |---|---|
   | 운영 | 401 `LOGIN_FAILED`, 계속 시도하면 429 `RATE_LIMITED` — **이 계정이 운영 DB 에 없다** |
   | 테스트 | 200 — 로그인 성공 (`https://localhost` 출처는 CORS 허용) |

### 원인

앱은 운영 서버로 빌드됐는데, 계정 `010-2232-0688` 은 테스트 서버에만 있다. 그래서 운영 서버가 "휴대폰 번호 또는 비밀번호를 확인해주세요" 로 거절했다.

### 해결

테스트 서버용(staging 모드)으로 다시 빌드해 네이티브 프로젝트에 복사했다. 코드는 바꾸지 않았다.

```bash
npm run cap:sync:development -w apps/customer   # vite build --mode staging && cap sync
```

확인: 새 번들(`index-D41R_fv4.js` → `http-emZpxLFa.js`)의 API 주소가 테스트 서버 `https://ansimclean-api-test-…a.run.app/api/v1` 이다.
그다음 Android Studio 에서 ▶ Run 으로 다시 설치한다.

**주의:** 나중에 `npm run cap:sync`/`cap:android` 를 돌리면 다시 운영 주소로 빌드된다. 테스트 계정으로 확인할 때는 항상 `cap:sync:development` 를 쓴다.

## 참고

- **운영 서버로 붙는 앱(production 빌드)** 에서는 이 계정이 없어서 로그인되지 않는다. 운영에서 쓰려면 운영에서 새로 가입해야 한다.
- 운영 서버에서 이 번호로 여러 번 실패해 지금은 `RATE_LIMITED`(429)가 걸려 있다. 잠시 지나면 풀린다.
- 매니저 앱(`apps/manager`)도 `.env.development.local` 이 같은 테스트 서버를 가리키므로 개발 서버에서 같은 문제가 생긴다. 같은 수정을 `apps/manager/vite.config.js` 에 넣으면 된다.
