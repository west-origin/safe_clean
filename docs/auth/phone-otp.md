# 고객 휴대폰 SMS OTP 인증 (2026-11-10)

첫 1회 청소 혜택용 번호 확인이다. **로그인 수단이 아니다.** 고객은 카카오·구글로 로그인하고, 그 계정에 번호를 확인해 붙인다.

## 구조 — Supabase Auth 기본 기능(phone_change)만 쓴다

```
앱 PhoneVerify ─POST /customer/identity/phone/otp─▶ Spring CustomerPhoneService
                                                     ├ 한도 검사(고객·번호 시간당 5, IP 20, 전체 500) · 60초 재발송 간격
                                                     ├ customer_phone_challenges 기록
                                                     └ PUT /auth/v1/user {phone} (그 고객의 JWT) ─▶ Supabase Auth
                                                                                                  ├ 6자리 생성, 3분 유효
                                                                                                  ├ 개발: 테스트 번호 → 고정 인증번호, 발송 없음
                                                                                                  └ 운영: Send SMS Hook ─▶ Spring SmsHookService ─▶ SmsSender(Solapi)
앱 ─POST /customer/identity/phone/verify─▶ Spring ─POST /auth/v1/verify {type: phone_change}─▶ Supabase (auth.users.phone 확정)
                                            ├ 새로 생긴 세션은 바로 logout
                                            ├ auth.users.phone·phone_confirmed_at 으로 최종 확인
                                            ├ 오답 5회 → 요청 폐기(새 인증번호 필요)
                                            └ customer_phone_proofs 기록 → 첫 1회 청소 자격
```

- 우리 서버는 인증번호를 만들거나 저장하지 않는다. 매니저 가입·비밀번호 재설정용 `OtpService`(자체 OTP)는 이번 범위가 아니라 그대로 둔다.
- 앱은 Spring API 만 부른다. Supabase 비밀 키·Hook 비밀·Solapi 키는 서버 환경변수에만 있다.
- 기존 카카오·구글 계정과의 관계: 같은 auth.users 행에 phone 이 붙을 뿐 새 계정을 만들지 않는다. Supabase 는 번호가 하나의 계정에만 붙게 하므로, 다른 계정(매니저 포함)이 쓰는 번호는 "이 번호로는 인증할 수 없어요" 로 거절된다(누가 쓰는지 알리지 않음).
- 매니저 가입이 이 번호의 Supabase 사용자를 "잔재" 로 오인해 비밀번호를 덮어쓰지 않도록 `AuthService.signup` 에 프로필 존재 검사를 넣었다(PHONE_TAKEN).
- 카카오·구글 고객은 Supabase 에 phone identity 가 생긴다. 고객 비밀번호가 없으므로 휴대폰+비밀번호 로그인은 불가하다. Supabase 의 휴대폰 OTP 로그인(`/otp`)은 운영에서 Hook 허용 목록이 막는다(아래).
- 네이버 로그인은 현재 코드에 없다(카카오·구글만). 붙일 때도 같은 auth.users 계정에 번호가 붙는 구조라 충돌하지 않는다.

## Supabase Auth 설정 (프로젝트별, 대시보드)

| 항목 | 개발 `safe_clean_test` | 운영 `safe_clean` | 위치 |
|---|---|---|---|
| Phone provider | ON (매니저도 사용) | ON | Auth > Providers > Phone |
| SMS OTP Expiry `sms_otp_exp` | **180** 초 | **180** 초 | Auth > Providers > Phone |
| SMS OTP Length `sms_otp_length` | **6** | **6** | 같은 곳 |
| SMS 재발송 간격 `sms_max_frequency` | 60 초 | 60 초 | Auth > Rate Limits |
| Test Phone Numbers and OTPs | `scripts/dev-test-phones.json` 의 `supabaseSmsTestOtp` + 만료일 | **비움(절대 넣지 않음)** | Auth > Providers > Phone |
| Send SMS Hook | 끔 (테스트 번호만 사용) | SOLAPI 전환 때 켬 | Auth > Hooks |
| SMS provider(Twilio 등) | 설정 안 함 | 설정 안 함 (Hook 이 대신) | — |

서버 상수 `CustomerPhoneService.CODE_TTL`(3분)·`RESEND_INTERVAL`(60초)이 위 값과 같아야 한다.

개발 테스트 번호(문자 없음, 고정 인증번호): 010-0000-0001 / 0002 / 0003 — 값은 `scripts/dev-test-phones.json`.
테스트 번호가 아닌 번호는 개발에서 SMS provider·Hook 이 없으므로 발송 실패(UPSTREAM)로 끝난다 — 실제 문자는 나가지 않는다.

## 에뮬레이터 가상 SMS (개발 전용)

```bash
npm run dev:sms -- 01000000001
```

켜진 Android 에뮬레이터(`emulator-*`)의 문자함에 `[안심클린] 인증번호 123456 (3분 안에 입력해주세요)` 를 넣는다(`adb emu sms send`). 실제 기기에는 보내지 않고, 테스트 번호가 아니면 거절한다. 앱에서 '인증하기' 를 누른 뒤 실행하면 실제 문자를 받는 흐름과 같다.

## 개발 계정을 '미인증' 으로 되돌리기

같은 테스트 계정으로 인증을 다시 해 보려면 [`reset-test-phone-proof.sql`](reset-test-phone-proof.sql) 을 쓴다.

1. Supabase 대시보드 → **safe_clean_test** 프로젝트 → SQL Editor (운영 `safe_clean` 이 아닌지 꼭 확인).
2. 파일 내용을 붙여 넣고 맨 위 번호(`'+821000000001'`)를 되돌릴 번호로 바꾼 뒤 Run.
3. 마지막 결과 `proofs / auth_phone / phone_identities` 가 모두 0 이면 끝.
4. 앱에서 가정 → 1회를 다시 누르면 인증 안내 시트가 뜬다(앱 재시작 필요 없음, 화면에 다시 들어오면 서버에 다시 묻는다).

- 지우는 것: 우리 인증 기록·진행 중 요청·예약에 아직 쓰지 않은 첫 1회 혜택 기록, 그 계정에 붙은 Supabase 번호와 phone identity.
- 지우지 않는 것: 계정(id)·카카오/구글 연결·예약·결제.
- 번호만 바꿔 다시 해 보는 더 가벼운 방법: 테스트 번호 0002·0003 으로 인증한다. 단 한 번 인증한 계정은 번호를 바꿀 수 없으므로(서버 정책) 결국 위 초기화가 필요하다.
- auth 스키마를 SQL 로 고치는 것은 **개발 프로젝트 테스트 초기화에만** 허용한다. 서버 코드·운영에서는 하지 않는다.

## SOLAPI 전환 (운영 실제 발송)

1. 서버 환경변수: `SMS_PROVIDER=solapi`, `SMS_API_KEY`, `SMS_API_SECRET`, `SMS_SENDER`(사전 등록 발신번호) — 기존 `SolapiSmsSender` 를 그대로 쓴다.
2. Supabase 대시보드 Auth > Hooks > Send SMS Hook > HTTPS: URL `https://<API 도메인>/api/v1/hooks/supabase/send-sms`, "Generate secret".
3. 받은 비밀(`v1,whsec_…`)을 Secret Manager 에 넣고 `SUPABASE_SMS_HOOK_SECRET` 로 서버에 연결 → 배포. 비어 있으면 Hook 엔드포인트는 404(꺼짐).
4. Hook 을 Enable. 이때부터 Supabase 가 만든 인증번호를 서버가 Solapi 로 보낸다.
5. 확인: 실제 번호로 요청 → 문자 수신 → 확인. 서버 로그에 `허용되지 않은 문자 발송 요청` 이 없는지 본다.

Hook 보안: Standard Webhooks 서명(5분 허용 오차) + **우리 서버가 1분 안에 시작한 같은 계정·번호의 요청만** 발송 + 번호별 시간당 5회. 누군가 공개 키로 Supabase `/otp`·`/signup` 을 직접 불러도 문자는 나가지 않는다(403).

## 보안 정책 요약

| 위협 | 대책 |
|---|---|
| 인증번호 대입 | 6자리·3분 유효, 요청당 오답 5회면 폐기, 고객 시간당 확인 10회·IP 60회 (+ Supabase verify IP 한도) |
| 문자 폭탄·비용 | 고객·번호 시간당 5회, IP 20회, 서비스 전체 500회, 60초 재발송 간격 (Supabase sms_max_frequency 와 이중) |
| 남의 번호 도용 | 그 번호로 온 문자를 받아야만 확인됨. 한 번 인증한 고객은 번호 변경 불가(별도 지원 절차) |
| 여러 계정으로 첫 혜택 반복 | `first_cleaning_claims` 가 번호 단위로 한 번만(기존) |
| 테스트 설정 운영 유출 | 테스트 번호는 개발 Supabase 프로젝트에만, 만료일 지정. 가상 SMS 스크립트는 에뮬레이터만. 운영 서버엔 테스트 코드 경로 없음 |
| 비밀 노출 | service_role·Hook 비밀·Solapi 키는 서버 환경변수. 로그에 번호는 마스킹. 운영은 `SMS_PROVIDER=solapi` 만 허용(ProductionGuard)이라 인증번호가 로그에 남지 않는다 — `logging` 발송(로컬·테스트)만 본문을 로그에 쓴다 |
