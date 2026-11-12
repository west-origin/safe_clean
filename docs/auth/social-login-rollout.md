# 고객 소셜 인증 전환 (2026-11-09)

## 분석 및 현재 상태

- 고객: React/Vite + Capacitor. 기존 `SignUp`과 `LoginPhone`은 전화번호/비밀번호 로그인. 소셜 버튼은 실제 OAuth 없이 휴대폰 가입 안내를 열었음.
- 서버: Spring Boot, 검증된 Supabase JWT의 sub로 profiles를 조회하고 DB의 role로 권한 부여. 고객/매니저 공용 인증 API.
- profiles.id가 auth.users.id와 동일. bookings.customer_id, payments, 주소 등이 이 ID를 참조. 로그인 변경은 이 ID를 바꾸지 않음.
- 실제 점검한 DB: `safe_clean_test` (`xedkwtdkpxejlqpbdstb`). phone identities 8개, 지원되는 identity가 없는 활성 고객 5개. `safe_clean` 운영 프로젝트는 INACTIVE여서 운영 데이터 검증은 미완료.
- 2026-11-09 최초 Auth settings: kakao=false, google=false, phone=true, email=false, disable_signup=true. 실제 OAuth 설정 없이는 사용 불가.
- 고객 프로필/예약/결제/phone_verifications 존재. 별도 쿠폰/프로모션 사용 이력 테이블은 없음. 기존 가격 계산에는 첫 할인 금액 계산도 없음. 기존 금액을 임의로 바꾸지 않음.
- 기존 public 업무 테이블은 RLS가 켜져 있으나 정책이 없어 Data API 접근은 거부됨. Spring의 소유권 검사와 별도 DB 연결을 유지. 새 고객 식별 테이블도 RLS 및 명시적 REVOKE로 클라이언트 접근을 차단.
- 기존 advisory: mutable `set_updated_at` search_path, public의 btree_gist, leaked password protection 비활성. 이번 작업과 무관한 스키마 변경은 하지 않음.

## 구현

- 고객 가입/로그인: 카카오·구글만. 기존 비밀번호/직접 휴대폰 가입 화면과 API 어댑터 제거, 이전 deep link는 로그인으로 이동.
- 매니저 앱은 별도 제품이므로 기존 전화번호/비밀번호 흐름 유지. 서버의 고객 password/OTP login/signup/reset 요청은 거부.
- Android `SocialAuthPlugin`: Kakao Talk SDK 2.26.0, Google Credential Manager 1.5.0 + googleid 1.1.1. ID token과 요청별 nonce를 Supabase `signInWithIdToken`으로 교환.
- 취소는 재인증/브라우저 전환 없이 복귀. SDK 실패/미설정/앱 미설치는 Supabase PKCE + 시스템 브라우저로 fallback. callback은 `ansimclean://auth/callback`만 수락. 브라우저는 `/auth/callback`.
- 웹은 Supabase OAuth PKCE. iOS는 이번 범위의 Android SDK 대신 PKCE 시스템 브라우저 사용. 기존 iOS 구조와 복귀 URL 유지.
- refresh/persist/auto refresh/signOut은 supabase-js가 담당. 기존 session 객체는 화면/API 호환을 위한 메모리 투영. 새 세션의 토큰은 기존 저장소에 복제하지 않음.
- 네이티브 SDK storage는 기존 Keychain/Keystore 플러그인 사용, localStorage fallback 없음. 기존 세션은 SDK `setSession`으로 검증/이전 후 기존 토큰 저장소 제거.
- 앱 시작은 SDK 세션 확인과 `/auth/me` 응답까지 기다린 뒤 라우터 표시. 갱신 무효는 로그인으로, 일시 네트워크 장애는 재시도 안내.
- 최초 소셜 사용자 프로필은 검증된 JWT의 sub + DB auth.identities의 kakao/google 존재를 확인한 후 같은 ID로 생성. user_metadata의 역할/전화번호/이름은 권한이나 연결 근거로 사용하지 않음.
- 신규 고객은 약관 동의 후 홈. 주소·주거는 예약 때 수집. 기존 고객의 프로필/예약 관계는 보존.
- 소셜 탈퇴는 비밀번호 없이 최근 5분 내 JWT AMR의 oauth 인증을 요구. 기존 삭제 가드·익명화·아웃박스 유지. 오래된 세션이면 재로그인 안내 후 사용자가 다시 탈퇴 확인.
- 회복 URL은 `apps/customer/src/auth/config.js` 한곳에서 관리.

## 자동 연결

이메일 문자열 비교, 전화번호/이름 비교, auth.identities 직접 쓰기, 데이터 병합 코드는 없음. Supabase의 Automatic Identity Linking을 그대로 사용.

카카오 OIDC 활성화, account_email 동의, 제공 이메일 및 검증 상태, Google의 verified email을 실제 테스트해야 함. 이메일 미제공/미검증/상이한 이메일은 연결을 강제하지 않음. 별도 계정 또는 제공자/Supabase 거부 결과를 안전하게 안내. 고객센터 안내는 기존 이용 내역을 다른 계정으로 보여주는 우회 경로가 아님. 수동 계정 연결 화면은 없음.

## 테스트 데이터 초기화 (추가 사용자 승인)

사용자가 2026-11-09에 `safe_clean_test`의 기존 고객·매니저 계정 및 예약·결제 데이터 초기화를 명시적으로 승인했다. `reset-test-user-data.sql`을 현재 FK 관계의 자식→부모 순서로 실행했다. 실행 후 auth.users/identities/sessions, profiles, bookings, payments는 모두 0건. 약관 10개, 서비스 분류 6개, app_settings 41개는 유지했다. 운영 `safe_clean`은 대상에서 제외했다. Storage 사진·녹화 객체 59개도 2026-11-10 공식 Storage API로 삭제했다(job-photos 54, recordings 3, repair-photos 2). 후속 SQL로 storage.objects=0을 확인했고 버킷은 유지했다.

V061은 초기화 후 테스트 DB에 적용했고 Flyway 061 checksum=500434441을 로컬 파일과 일치시켰다. 다음 서버 시작 때 중복 적용되지 않는다. 초기화 전 V059·V060의 Flyway 체크섬과 동일한 계산을 교차 검증했다.

아래 기존 계정 전환 절차는 **향후 운영 적용을 위한 보존 대책**이다. 초기화가 승인된 테스트 DB에는 더 이상 기존 5개 계정을 전환하지 않는다.

## 운영 기존 고객의 접근 보존: 배포 선행 조건

**새 서버/앱 배포와 phone provider 비활성화는 아래 대책을 완료한 뒤에 진행한다.** 소셜 설정을 켜는 것만으로 전화번호 전용 기존 계정이 자동 연결되지는 않는다.

1. `social-login-preflight.sql`을 운영/테스트에 각각 실행해 지원 identity가 없는 계정과 업무 관계를 목록화. 프로젝트별 백업/복구 지점 확보.
2. 기존 고객이 기존 인증 방식으로 해당 계정의 소유자임을 증명하도록 운영 전환 기간을 둔다. 새로운 고객 앱에 수동 연결 화면을 추가하지 않는다.
3. 고객의 새 SNS 계정에 대해서도 제공자에서 검증된 이메일과 소유 증명을 별도로 확보한다. 전화번호/이름 동일만으로 승인하지 않는다.
4. 운영자가 증명 자료를 확인한 계정만 Supabase 공식 Admin API로 **기존 auth.users.id에** 검증된 이메일을 등록하는 별도 통제 절차를 수행한다. 이미 동일 이메일의 다른 auth user/identity가 있으면 중단하고 수동 사례 검토. auth.identities SQL 삽입/이동은 금지.
5. 고객이 SNS로 로그인하여 Supabase 자동 연결이 **기존 ID로** 이루어졌는지 확인. 예약/결제/쿠폰의 기존 소유 ID 및 접근권을 전후 비교. 연결 실패 시 기존 인증 접근을 먼저 유지하고 사례 조사.
6. 전환 불가능 고객은 지원 대책과 사용자 통지를 완료하기 전까지 구버전 인증 종료를 보류. 두 개의 기존 계정 데이터는 자동 병합하지 않는다.
7. 모든 대상 확인 후에만 새 서버의 고객 password API 차단 코드를 배포. phone provider 자체 비활성화는 매니저 로그인에도 영향을 주므로 현 공용 프로젝트에서는 끄지 않는다. 매니저 분리 또는 별도 전환 작업이 필요하다.

이 문서는 전환 절차이며 위 Admin 작업이나 기존 사용자 변경을 자동 실행하지 않았다. 무조건 이메일을 confirmed로 바꾸는 스크립트도 제공하지 않는다.

## 첫 이용 고객 검증

- `customer_phone_proofs`: auth 계정 ID별 최초 SMS 인증 번호. 같은 번호를 여러 계정에서 인증할 수 있으나 다른 계정의 업무 데이터는 반환하지 않음. 셀프 번호 변경은 막고 별도 지원 절차 대상으로 둠.
- `customer_phone_challenges`: 인증된 고객과 SMS 요청 번호를 결합, 유효기간/발송·확인 제한. 기존 OtpService/Solapi 재사용. 새 발송 업체/계약/서비스를 도입하지 않음.
- `first_cleaning_claims`: 전화번호 PK + customer_id UNIQUE. home/oneTime 예약과 같은 트랜잭션에서 한 번만 기록. Hibernate flush 후 FK를 기록. 예약 롤백 시 혜택 예약도 롤백.
- 프론트에서 가정 청소 첫 1회 체험 선택은 미인증/기이용이면 비활성화. 서버에서 최종 검증하므로 직접 API 호출로 우회할 수 없음. 다른 카테고리의 정상 1회 서비스는 차단하지 않음.
- V061은 기존 프로필의 전화번호를 자동으로 인증 완료 처리하지 않는다. 기존 home 청소 완료(status=done) 이력만 첫 이용 사용 이력으로 보존하며 취소 예약은 제외한다. 실제 할인 장부가 별도로 있다면 운영 적용 전 해당 장부를 기준으로 대상을 검토한다. 테스트 DB는 사용자 승인으로 초기화되어 기존 사용 이력이 없다.
- 주소·PG 수단은 부정 이용 보조 신호로만 사용. 현재 자동 차단/계정 결합에 사용하지 않음. PG가 재사용 가능한 결제수단 식별값을 제공하는지 확인 후 별도 서버 위험 신호 수집을 구현할 것. 카드번호/소셜 액세스 토큰 자체를 식별값으로 저장하지 않음.
- 번호 proof/혜택 원장의 보유기간, 탈퇴 후 중복할인 방지에 필요한 최소 식별값의 HMAC 전환 및 파기 정책은 실제 운영 정책 확정 후 추가할 것. 로그/분석에 전화번호·코드·토큰을 전달하지 않음.

## 필요한 설정

- 앱 VITE_SUPABASE_URL + VITE_SUPABASE_PUBLISHABLE_KEY: publishable 또는 legacy anon만. 비밀 키 금지.
- 카카오 앱: ID 1619759, Android package `kr.ansimclean.customer`, 각 실제 서명 key hash, Native App Key, REST API Key, OIDC, 허용 이메일 동의항목.
- Supabase Kakao: REST key를 기본 OAuth client ID로, Native key를 ID token의 허용 audience에 포함. Client Secret은 콘솔에서만 관리. nonce 검증을 끄지 말 것.
- 카카오 REST redirect: `https://xedkwtdkpxejlqpbdstb.supabase.co/auth/v1/callback` (테스트). 앱 callback과 혼동하지 말 것.
- Android Gradle 공개 식별자: `-PKAKAO_NATIVE_APP_KEY=... -PGOOGLE_WEB_CLIENT_ID=...`. Native key/Web client ID는 비밀이 아니지만 client secret은 넣지 않음.
- Google Cloud: 웹 OAuth client ID, Client Secret(Supabase 콘솔만), Android client(package + 개발/배포 인증서 SHA), 동의 화면·테스트 사용자·검증 상태 확인.
- Supabase redirect allowlist: 웹 배포 origin의 `/auth/callback`, 개발용 `http://127.0.0.1:5177/auth/callback`, `ansimclean://auth/callback`. 운영에는 임의 wildcard 미사용.
- 신규 OAuth 고객 허용을 위해 disable_signup 설정 검토. 현재 phone provider는 매니저 때문에 유지.
- 각 제공자 키/Secret 신규 발급 및 보안 접근 확장은 실제 적용 직전 승인. 로그인 비밀번호·MFA·법적 동의는 사용자 진행.

## 검증 상태

자동 테스트/빌드 결과는 PROGRESS.md의 2026-11-09 소셜 인증 절에 기록. 실제 카카오·구글 계정 로그인, 같은 검증 이메일로 두 공급자 로그인 시 auth.users.id 동일성, 카카오 동의 거절/미검증, 실제 만료 토큰 갱신, Android 앱 종료 후 복원, 네트워크 단절과 취소, 배포 서명 인증서는 외부 콘솔 설정 후 실기기에서 별도로 확인해야 한다. mock 통과를 실제 OAuth 통과로 간주하지 않는다.

테스트 DB 초기화와 V061 마이그레이션은 실행했다. 서버/운영 배포는 실행하지 않았다.

공식 참고: https://supabase.com/docs/guides/auth/auth-identity-linking , https://supabase.com/docs/guides/auth/social-sign-in/auth-kakao , https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation , https://developers.kakao.com/docs/ko/kakaologin/android

## 외부 설정 진행 기록

- 사용자 승인 후 Kakao 앱 1619759 카카오 로그인 ON. OIDC ON 클릭 적용, 최종 재조회 필요. REST 키의 테스트 Supabase callback 등록.
- 이메일 동의항목은 콘솔에서 `권한 없음` 확인. 비즈 앱 전환 및 이메일 권한 확보는 사업자/약관 동의가 필요한 별도 사용자 작업.
- 사용자 승인 후 테스트 Supabase Kakao provider 활성화, REST/native audiences 및 기존 Client Secret 입력, 이메일 없는 사용자 허용. 공개 `/auth/v1/settings`에서 kakao=true 검증. Secret은 소스/환경 파일/문서에 저장하지 않음.
- 이후 Google provider 활성화 및 신규 가입 허용 완료. 공개 Auth settings에서 google=true, kakao=true, disable_signup=false 확인. 실제 OAuth 검증은 별도 필요.
- 콘솔 로그인은 Brave에서 이어서 진행했다. 앱 안 브라우저는 별도 쿠키 세션이므로 동일 로그인 상태로 간주하지 않는다.

2026-11-10 이어서 진행: Supabase redirect allowlist에 `ansimclean://auth/callback`, `http://127.0.0.1:5177/auth/callback`, `http://localhost:5177/auth/callback`, `http://localhost:5173/auth/callback` 4개를 저장하고 UI에서 확인. Google 웹 클라이언트 생성 및 Supabase 연동은 완료했으며 공개 Auth settings에서 kakao=true, google=true, disable_signup=false 확인. Google nonce 생략과 수동 계정 연결은 비활성 상태 유지.

카카오 브라우저 fallback은 Supabase 기본 provider가 account_email/profile_image/profile_nickname을 요청한다. 현재 카카오 앱의 이메일 권한이 없으므로 비즈 앱 전환/동의항목 권한을 확보하기 전에는 브라우저 로그인이 거부될 수 있다. 클라이언트에서 이메일을 임의 생성하거나 자동 병합으로 우회하지 않는다.

2026-11-10 Google Android 개발용 OAuth 생성 확인: `649043347047-664cdc0u475c56vutjidero7t0kbgku9.apps.googleusercontent.com`, package `kr.ansimclean.customer`, 디버그 SHA-1 `14:5C:79:6B:66:51:6C:59:95:C4:4F:C0:6E:F0:3F:4B:E2:66:37:33`. 웹 OAuth ID는 네이티브 Credential Manager의 server client ID로 사용하고 Android client ID로 대체하지 않는다. 배포 서명은 별도 등록 대상. 프론트 최종 전체 테스트 197개 파일/557개 테스트 통과. 사용자 승인에 따라 테스트 서버 소스 배포 진행 중(운영 제외).

2026-11-10: 테스트 배포 완료 — ansimclean-api-test-00018-rqn, 트래픽 100%. 기존 runtime/시크릿 v5 유지. Cloud Build 서버 테스트 634개 통과. HTTP health 200 UP, 무토큰 auth/me 401, 고객 비밀번호 login 403 확인. 테스트 서비스 manual 1 활성화. Storage 59개 삭제 후 0건, V061 checksum=500434441/success=true 재확인. 운영 미변경. 실제 SNS 로그인/기기 세션 복원은 미검증. 위의 배포 미실행/진행 중 기록은 이 결과로 대체.

2026-11-10 사용자 요청으로 이메일 권한 확보 전 임시 Kakao 로그인 적용. Android는 KakaoTalk→공식 SDK loginWithKakaoAccount 브라우저 fallback→Supabase signInWithIdToken. 추가 개인정보 scope 요청 없음. Kakao Console 이메일/사진/닉네임 동의항목은 사용 안 함, 카카오 로그인 및 OIDC는 ON 필요(비즈 앱과 독립). Supabase 이메일 없는 계정 허용 설정 유지. 자체 이메일 생성/계정 병합 없음. 기존 Google/이메일 자동 연결 코드와 Kakao OAuth 코드 보존하되 개인정보를 자동 요청하는 Supabase Kakao OAuth 경로는 진입 차단. 일반 웹/iOS의 Kakao는 현재 사용 불가 안내, Google 사용 가능. 추후 권한 확보 및 개인정보 최소 수집 브라우저 흐름 구현 후 재개. 이메일 없는 시기에 생성된 계정과 기존 다른 계정 데이터는 자동 합치지 않으며 추후 연결 조건을 별도 검증해야 함.
