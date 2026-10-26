# 개발 Supabase 데이터 사전

작성일: 2026-10-26 (한국 시간). 프로젝트: **safe_clean_test** (`xedkwtdkpxejlqpbdstb`).

실제 개발 DB의 일반·파티션 테이블 **85개, 컬럼 773개** 전체를 조회했다. PostgreSQL 시스템 카탈로그와 뷰·시퀀스·함수는 테이블 수에 포함하지 않는다. 실제 사용자 레코드나 비밀값은 조회·수록하지 않았다.

## 설명 기준과 코멘트 적용 범위

- `public` 45개 테이블: 실제 DB 구조, `backend/src/main/resources/db/migration/V001~V057`, 현재 Java 엔티티·서비스 코드를 대조했다. 정기예약은 초기 SQL의 매주 방식이 아니라 현재 코드의 **매월 1회** 기준이다.
- `auth`, `storage`, `realtime`, `vault` 등의 설명은 실제 컬럼·타입·제약조건과 Supabase 공식 문서에 근거한 관리 기능 설명이다. 내부 구현 필드는 이름과 스키마에 따른 해석이며 앱에서 사용하는 필드라는 뜻은 아니다. 세부 토큰 표현·상태 코드 등 문서로 확정되지 않은 내부 동작은 단정하지 않았다.
- DB 코멘트를 적용하고 재조회로 일치를 확인한 범위는 현재 연결된 `postgres` 소유 테이블 **46개, 컬럼 400개**다. `public` 전체와 `supabase_migrations.schema_migrations`가 해당한다. 검증 결과: 테이블 46개·컬럼 400개 모두 설명과 일치, 누락·불일치 0개.
- 나머지 39개 관리 테이블은 현재 계정에 소유자 권한이 없어 코멘트를 적용할 수 없다. 전체 설명은 이 문서에 포함했다. 관리 스키마의 소유권·권한을 바꾸지 않는다.
- 기존 코멘트에 있던 녹화 구간의 추정·검토 필요, 시간 경계, 사진 공개 시점 등 의미를 새 설명에도 보존했다. 적용 전 코멘트는 별도 복구 SQL로 보관한다.

## 읽는 법

- `uuid`: 고유 식별자. `text[]`: 문자열 목록. `json/jsonb`: JSON 데이터. `timestamptz`: 절대 시각이며 표시 시간대에 따라 달라진다.
- 방문 날짜·시각은 앱에서 서울 시간으로 해석한다. `weekday`는 앱 테이블에서 0=월~6=일이다.
- 앱 금액 컬럼은 원 단위이며, 녹화 구간의 `*_ms`는 밀리초, `duration_seconds`는 초다.
- NULL 허용은 DB 제약이며, 실제 앱이 항상 값을 입력하는지 여부와는 별개다. 기본값 없음은 자동 값이 주어진다는 뜻이 아니다.
- FK가 없는 `user_id/item_id` 등도 논리적 연관이 있을 수 있다. 아래 제약조건이 DB에서 강제하는 실제 관계다.

## 스키마별 개요

| 스키마 | 테이블 수 | 역할 | DB 코멘트 |
| --- | ---: | --- | --- |
| `auth` | 27 | Supabase 로그인·세션·MFA·SSO·OAuth | 소유자 권한 없음 |
| `public` | 45 | 앱 업무 데이터·카탈로그·백엔드 운영 이력 | 적용 완료 |
| `realtime` | 3 | 실시간 메시지·구독 관리 | 소유자 권한 없음 |
| `storage` | 8 | 파일·버킷·업로드 메타데이터 | 소유자 권한 없음 |
| `supabase_migrations` | 1 | Supabase 도구의 마이그레이션 이력 | 적용 완료 |
| `vault` | 1 | 암호화 시크릿 | 소유자 권한 없음 |

## 전체 테이블 찾아보기

| 테이블 | 설명 |
| --- | --- |
| [`auth.audit_log_entries`](#auth-audit-log-entries) | Supabase Auth가 기록하는 인증 관련 사용자 작업 감사 로그. |
| [`auth.custom_oauth_providers`](#auth-custom-oauth-providers) | 사용자 정의 OAuth/OIDC 로그인 공급자 설정. 앱의 PG provider와 관계없는 인증 공급자이다. |
| [`auth.flow_state`](#auth-flow-state) | OAuth·SSO 로그인 도중 코드 교환과 계정 연결에 필요한 임시 흐름 상태. |
| [`auth.identities`](#auth-identities) | Auth 사용자에 연결된 이메일·전화번호·소셜 로그인 등의 인증 identity. |
| [`auth.instances`](#auth-instances) | Auth 서비스의 인스턴스 정보와 기본 설정을 위한 내부 관리 테이블. |
| [`auth.mfa_amr_claims`](#auth-mfa-amr-claims) | 세션에서 사용한 인증 방식(AMR) 기록. 다중 인증 수준 판단에 사용한다. |
| [`auth.mfa_challenges`](#auth-mfa-challenges) | MFA 인증 수단 검증을 위한 일회성 챌린지. |
| [`auth.mfa_factors`](#auth-mfa-factors) | 사용자가 등록한 TOTP·전화·WebAuthn 등 MFA 인증 수단. |
| [`auth.mfa_recovery_code_sets`](#auth-mfa-recovery-code-sets) | MFA 수단에 연결된 복구 코드 묶음과 검증 실패 잠금 상태. |
| [`auth.mfa_recovery_codes`](#auth-mfa-recovery-codes) | MFA 복구 코드의 해시와 사용 이력. 코드 원문이 아닌 해시를 보관한다. |
| [`auth.oauth_authorizations`](#auth-oauth-authorizations) | Supabase가 OAuth 서버로 동작할 때 클라이언트의 사용자 인증·인가 요청 상태. |
| [`auth.oauth_client_states`](#auth-oauth-client-states) | 외부 OAuth 로그인 흐름에서 코드 교환에 사용하는 임시 클라이언트 상태. |
| [`auth.oauth_clients`](#auth-oauth-clients) | Supabase OAuth 서버에 등록된 클라이언트 애플리케이션. |
| [`auth.oauth_consents`](#auth-oauth-consents) | 사용자가 OAuth 클라이언트에 허용한 권한 범위와 철회 이력. |
| [`auth.one_time_tokens`](#auth-one-time-tokens) | 이메일 확인·비밀번호 복구 등 Auth 일회성 토큰의 해시와 유효기간. |
| [`auth.refresh_tokens`](#auth-refresh-tokens) | Auth 세션 갱신 토큰과 회전·폐기 상태. public.revoked_sessions와 역할이 다르다. |
| [`auth.saml_providers`](#auth-saml-providers) | 기업 SAML SSO 공급자의 메타데이터와 사용자 속성 매핑. |
| [`auth.saml_relay_states`](#auth-saml-relay-states) | SAML 요청과 로그인 완료 후 리다이렉트를 연결하는 임시 RelayState. |
| [`auth.schema_migrations`](#auth-schema-migrations) | Supabase Auth 자체 DB 스키마의 적용 버전. 앱 Flyway 이력과 별개이다. |
| [`auth.scim_tokens`](#auth-scim-tokens) | 기업 디렉터리의 SCIM 사용자 자동 프로비저닝 API 인증 토큰. |
| [`auth.scim_users`](#auth-scim-users) | 기업 SCIM 디렉터리 사용자와 Supabase Auth 사용자의 연결 정보. |
| [`auth.sessions`](#auth-sessions) | Auth 로그인 세션과 인증 수준·만료·갱신 정보. |
| [`auth.sso_domains`](#auth-sso-domains) | 이메일 도메인을 기업 SSO 공급자에 연결하는 매핑. |
| [`auth.sso_providers`](#auth-sso-providers) | SAML·SCIM 등 기업 SSO 공급자의 공통 관리 정보. |
| [`auth.users`](#auth-users) | Supabase Auth의 로그인 계정 원장. 앱 업무 프로필은 public.profiles에 별도로 저장한다. |
| [`auth.webauthn_challenges`](#auth-webauthn-challenges) | 패스키/WebAuthn 등록·로그인의 일회성 챌린지와 만료 상태. |
| [`auth.webauthn_credentials`](#auth-webauthn-credentials) | 사용자가 등록한 패스키/WebAuthn 공개키 인증 정보. 개인키는 인증장치에 보관한다. |
| [`public.addresses`](#public-addresses) | 고객의 저장 주소. 기본 주소와 방문지 검색 정보를 관리한다. |
| [`public.app_settings`](#public-app-settings) | 가격 정책·매칭 정책·화면 문구 등 서버 운영 설정. 실제 값은 key별 JSON 구조로 저장한다. |
| [`public.booking_series`](#public-booking-series) | 매월 1회 방문하는 정기예약 묶음. 회차별 실제 예약은 bookings.series_id로 연결한다. 같은 날짜가 없는 달은 말일로 보정한다. |
| [`public.booking_status_history`](#public-booking-status-history) | 예약 상태 전환 이력. 누가 언제 어떤 사유로 상태를 바꿨는지 기록한다. |
| [`public.bookings`](#public-bookings) | 고객 예약이자 매니저의 작업 단위. 일정·주소·가격 스냅샷과 배정·결제·촬영 진행 상태를 보관한다. |
| [`public.catalog_frequency_copy`](#public-catalog-frequency-copy) | 서비스 분류·이용 주기에 따른 예약 화면 안내 문구. |
| [`public.catalog_notice_chips`](#public-catalog-notice-chips) | 예약 시 선택할 수 있는 현장 상황 안내 칩 목록. |
| [`public.catalog_options`](#public-catalog-options) | 서비스별 무료·유료 추가 옵션 카탈로그. |
| [`public.catalog_prices`](#public-catalog-prices) | 서비스 분류·이용 주기별 기본 가격표. |
| [`public.catalog_reasons`](#public-catalog-reasons) | 매니저의 콜 거절·촬영 중단 사유 선택지. |
| [`public.checklist_templates`](#public-checklist-templates) | 서비스별 작업 체크리스트 원본. 예약 배정 시 실제 작업 항목으로 복사한다. |
| [`public.cleaning_photos`](#public-cleaning-photos) | 예약별 공간의 청소 전·후 사진 쌍. 파일은 비공개 job-photos Storage에 저장한다. 고객에게는 예약 완료 이후 공개한다. |
| [`public.customer_housing`](#public-customer-housing) | 고객의 주거 형태·면적·방 수·반려동물 정보. |
| [`public.faqs`](#public-faqs) | 고객·매니저용 자주 묻는 질문과 답변. |
| [`public.flyway_schema_history`](#public-flyway-schema-history) | Spring 백엔드 Flyway의 DB 마이그레이션 적용 이력. 업무 데이터가 아닌 스키마 버전 관리용이다. |
| [`public.idempotency_keys`](#public-idempotency-keys) | 쓰기 API의 중복 처리를 막는 요청 키와 응답 캐시. 같은 키라도 사용자별로 분리한다. |
| [`public.inquiries`](#public-inquiries) | 사용자 고객센터 문의와 운영자 답변, 탈퇴 후 보관 만료 시각. |
| [`public.job_checklist_events`](#public-job-checklist-events) | 체크리스트 체크·해제의 원본 이력. 항목 삭제 후에도 보존하며 녹화 구간 라벨 생성 근거로 사용한다. |
| [`public.job_checklist_items`](#public-job-checklist-items) | 예약별 실제 작업 체크리스트의 현재 상태. |
| [`public.job_pauses`](#public-job-pauses) | 작업 일시정지와 재개 구간 이력. |
| [`public.live_sessions`](#public-live-sessions) | 예약별 LiveKit 라이브·녹화 세션. 바디캠 연결·시청 차단·녹화 파일과 시간 기준을 관리한다. |
| [`public.livekit_events`](#public-livekit-events) | LiveKit 웹훅의 중복 처리를 막는 수신 이벤트 원장. |
| [`public.manager_devices`](#public-manager-devices) | 매니저의 바디캠과 재사용 LiveKit Ingress 연결 정보. 매니저별 1대 관리. |
| [`public.manager_hours`](#public-manager-hours) | 매니저의 요일별 기본 근무 가능 시간. |
| [`public.manager_penalties`](#public-manager-penalties) | 매니저 취소·미방문·지각과 회복 점수 원장. 현재 점수는 유효한 행의 합으로 계산한다. |
| [`public.manager_profiles`](#public-manager-profiles) | 매니저의 승인·평가·활동 지역·교육·정산 계좌 표시 정보. |
| [`public.manager_unavailable`](#public-manager-unavailable) | 매니저가 추가 등록한 근무 불가 날짜·시간과 반복 규칙. |
| [`public.notices`](#public-notices) | 고객·매니저 대상 공지사항. |
| [`public.notification_settings`](#public-notification-settings) | 사용자별 푸시·마케팅·콜·정산 등 알림 수신 설정. |
| [`public.offers`](#public-offers) | 예약을 수행할 매니저에게 발송한 콜·오퍼. 예약·매니저·발송 회차 조합별로 관리한다. |
| [`public.outbox_events`](#public-outbox-events) | 업무 트랜잭션과 함께 저장한 외부 알림·후속 처리 이벤트. 별도 처리기가 발송·재시도한다. |
| [`public.payment_methods`](#public-payment-methods) | 고객의 PG 등록 결제수단. 카드 원문 대신 PG 빌링키를 보관한다. |
| [`public.payment_refunds`](#public-payment-refunds) | 결제별 환불 처리 원장. 결제당 한 행으로 환불 요청과 완료를 관리한다. |
| [`public.payments`](#public-payments) | 예약별 PG 결제 거래·환불 누계·재확인 상태. 실제 결제수단과 거래 ID를 연결한다. |
| [`public.phone_verifications`](#public-phone-verifications) | 가입·인증을 위한 휴대폰 OTP 검증 상태. 인증번호 원문 대신 해시를 저장한다. |
| [`public.profiles`](#public-profiles) | 앱 사용자 기본 프로필. ID는 auth.users.id와 대응하지만 탈퇴 후 이력 보존을 위해 Auth 외래 키를 두지 않는다. |
| [`public.push_tokens`](#public-push-tokens) | 사용자·플랫폼별 푸시 발송 토큰. |
| [`public.recording_segments`](#public-recording-segments) | 단일 녹화 영상의 공간 구간 라벨. 체크 완료 경계로 공간을 추정하며 실제 방 이동 감지가 아니다. 학습 전 검토가 필요하고 구간은 [시작 포함, 종료 제외)이다. |
| [`public.repair_photo_uploads`](#public-repair-photo-uploads) | 예약 전 업로드된 수리 사진의 원장. 미첨부 파일 정리와 삭제 중 재첨부 방지에 사용한다. |
| [`public.revoked_sessions`](#public-revoked-sessions) | 로그아웃·비밀번호 재설정 등으로 서버가 차단한 Supabase 세션 목록. |
| [`public.service_categories`](#public-service-categories) | 앱에서 제공하는 청소·수리 서비스 분류. |
| [`public.settlements`](#public-settlements) | 완료 작업의 매니저 정산 금액과 지급 상태. 예약당 한 행. |
| [`public.shedlock`](#public-shedlock) | 여러 서버 인스턴스에서 같은 스케줄 작업이 동시에 실행되지 않도록 관리하는 ShedLock 잠금. |
| [`public.term_agreements`](#public-term-agreements) | 사용자별 약관 동의 버전과 시각. 사용자·약관 조합별 한 행. |
| [`public.terms`](#public-terms) | 고객·매니저별 약관 본문과 필수 동의 여부. |
| [`realtime.messages`](#realtime-messages) | Realtime Broadcast·Presence의 접근 정책 검사와 메시지 처리에 사용하는 내부 테이블. |
| [`realtime.schema_migrations`](#realtime-schema-migrations) | Supabase Realtime 내부 스키마 적용 버전. |
| [`realtime.subscription`](#realtime-subscription) | Postgres 변경 구독의 대상 테이블·필터·JWT 권한 정보. |
| [`storage.buckets`](#storage-buckets) | Supabase Storage 버킷 설정. 파일 본문이 아닌 메타데이터를 저장한다. |
| [`storage.buckets_analytics`](#storage-buckets-analytics) | 분석용 Storage 버킷과 테이블 포맷 정보. |
| [`storage.buckets_vectors`](#storage-buckets-vectors) | 벡터 저장용 Storage 버킷 정보. |
| [`storage.migrations`](#storage-migrations) | Storage 서비스 자체 스키마 마이그레이션 이력. |
| [`storage.objects`](#storage-objects) | Storage 파일 객체의 경로·메타데이터 원장. 실제 파일 본문은 오브젝트 스토리지에 있다. |
| [`storage.s3_multipart_uploads`](#storage-s3-multipart-uploads) | S3 호환 멀티파트 업로드의 진행 상태. 완성 파일과 별개인 업로드 작업 메타데이터. |
| [`storage.s3_multipart_uploads_parts`](#storage-s3-multipart-uploads-parts) | 진행 중인 S3 멀티파트 업로드의 개별 파트 정보. |
| [`storage.vector_indexes`](#storage-vector-indexes) | 벡터 버킷의 검색 인덱스 설정. |
| [`supabase_migrations.schema_migrations`](#supabase_migrations-schema-migrations) | Supabase 도구로 적용한 마이그레이션 이력. 앱 Flyway 이력과 별도로 관리된다. |
| [`vault.secrets`](#vault-secrets) | Supabase Vault의 암호화된 시크릿 저장소. 복호화 값은 별도 Vault 뷰로 제공된다. |

<a id="auth-audit-log-entries"></a>

## auth.audit_log_entries

Supabase Auth가 기록하는 인증 관련 사용자 작업 감사 로그.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `instance_id` | `uuid` | 예 | 없음 | Auth 인스턴스 UUID |
| `id` | `uuid` | 아니오 | 없음 | 감사 로그 UUID |
| `payload` | `json` | 예 | 없음 | 작업 주체·유형 등 이벤트 JSON |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 로그 생성 시각 |
| `ip_address` | `character varying(64)` | 아니오 | `''::character varying` | 요청 IP 주소 |

DB 제약조건:

- `PRIMARY KEY (id)`

<a id="auth-custom-oauth-providers"></a>

## auth.custom_oauth_providers

사용자 정의 OAuth/OIDC 로그인 공급자 설정. 앱의 PG provider와 관계없는 인증 공급자이다.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 공급자 설정 UUID |
| `provider_type` | `text` | 아니오 | 없음 | 공급자 프로토콜 유형 |
| `identifier` | `text` | 아니오 | 없음 | 공급자 고유 식별 문자열 |
| `name` | `text` | 아니오 | 없음 | 공급자 표시명 |
| `client_id` | `text` | 아니오 | 없음 | OAuth 클라이언트 ID |
| `client_secret` | `text` | 아니오 | 없음 | OAuth 클라이언트 시크릿. 민감정보 |
| `acceptable_client_ids` | `text[]` | 아니오 | `'{}'::text[]` | 허용할 클라이언트 ID 목록 |
| `scopes` | `text[]` | 아니오 | `'{}'::text[]` | 로그인 시 요청할 권한 범위 목록 |
| `pkce_enabled` | `boolean` | 아니오 | `true` | PKCE 사용 여부 |
| `attribute_mapping` | `jsonb` | 아니오 | `'{}'::jsonb` | 외부 프로필 속성의 사용자 정보 매핑 JSON |
| `authorization_params` | `jsonb` | 아니오 | `'{}'::jsonb` | 추가 인증 요청 파라미터 JSON |
| `enabled` | `boolean` | 아니오 | `true` | 공급자 활성화 여부 |
| `email_optional` | `boolean` | 아니오 | `false` | 이메일 없는 계정 허용 여부 |
| `issuer` | `text` | 예 | 없음 | OIDC 발급자 식별 URL |
| `discovery_url` | `text` | 예 | 없음 | OIDC Discovery 문서 URL |
| `skip_nonce_check` | `boolean` | 아니오 | `false` | OIDC nonce 검증을 건너뛰는 설정 |
| `cached_discovery` | `jsonb` | 예 | 없음 | 캐시된 Discovery 메타데이터 JSON |
| `discovery_cached_at` | `timestamp with time zone` | 예 | 없음 | Discovery 캐시 갱신 시각 |
| `authorization_url` | `text` | 예 | 없음 | 인증 요청 엔드포인트 |
| `token_url` | `text` | 예 | 없음 | 토큰 교환 엔드포인트 |
| `userinfo_url` | `text` | 예 | 없음 | 사용자 정보 엔드포인트 |
| `jwks_uri` | `text` | 예 | 없음 | 서명 공개키 JWKS 주소 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 설정 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 설정 갱신 시각 |
| `custom_claims_allowlist` | `text[]` | 아니오 | `'{}'::text[]` | 외부 공급자에서 허용할 사용자 정의 클레임 목록 |

DB 제약조건:

- `CHECK (((authorization_url IS NULL) OR (authorization_url ~~ 'https://%'::text)))`
- `CHECK (((authorization_url IS NULL) OR (char_length(authorization_url) <= 2048)))`
- `CHECK (((char_length(client_id) >= 1) AND (char_length(client_id) <= 512)))`
- `CHECK (((discovery_url IS NULL) OR (char_length(discovery_url) <= 2048)))`
- `CHECK ((identifier ~ '^[a-z0-9][a-z0-9:-]{0,48}[a-z0-9]$'::text))`
- `UNIQUE (identifier)`
- `CHECK (((issuer IS NULL) OR ((char_length(issuer) >= 1) AND (char_length(issuer) <= 2048))))`
- `CHECK (((jwks_uri IS NULL) OR (jwks_uri ~~ 'https://%'::text)))`
- `CHECK (((jwks_uri IS NULL) OR (char_length(jwks_uri) <= 2048)))`
- `CHECK (((char_length(name) >= 1) AND (char_length(name) <= 100)))`
- `CHECK (((provider_type <> 'oauth2'::text) OR ((authorization_url IS NOT NULL) AND (token_url IS NOT NULL) AND (userinfo_url IS NOT NULL))))`
- `CHECK (((provider_type <> 'oidc'::text) OR (discovery_url IS NULL) OR (discovery_url ~~ 'https://%'::text)))`
- `CHECK (((provider_type <> 'oidc'::text) OR (issuer IS NULL) OR (issuer ~~ 'https://%'::text)))`
- `CHECK (((provider_type <> 'oidc'::text) OR (issuer IS NOT NULL)))`
- `PRIMARY KEY (id)`
- `CHECK ((provider_type = ANY (ARRAY['oauth2'::text, 'oidc'::text])))`
- `CHECK (((token_url IS NULL) OR (token_url ~~ 'https://%'::text)))`
- `CHECK (((token_url IS NULL) OR (char_length(token_url) <= 2048)))`
- `CHECK (((userinfo_url IS NULL) OR (userinfo_url ~~ 'https://%'::text)))`
- `CHECK (((userinfo_url IS NULL) OR (char_length(userinfo_url) <= 2048)))`

<a id="auth-flow-state"></a>

## auth.flow_state

OAuth·SSO 로그인 도중 코드 교환과 계정 연결에 필요한 임시 흐름 상태.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 인증 흐름 UUID |
| `user_id` | `uuid` | 예 | 없음 | 연관 Auth 사용자 UUID |
| `auth_code` | `text` | 예 | 없음 | 흐름의 인증 코드 |
| `code_challenge_method` | `auth.code_challenge_method` | 예 | 없음 | PKCE 코드 챌린지 계산 방식 |
| `code_challenge` | `text` | 예 | 없음 | PKCE 코드 챌린지 |
| `provider_type` | `text` | 아니오 | 없음 | 로그인 공급자 유형 |
| `provider_access_token` | `text` | 예 | 없음 | 외부 공급자 액세스 토큰. 민감정보 |
| `provider_refresh_token` | `text` | 예 | 없음 | 외부 공급자 리프레시 토큰. 민감정보 |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 흐름 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 흐름 수정 시각 |
| `authentication_method` | `text` | 아니오 | 없음 | 사용한 인증 방식 |
| `auth_code_issued_at` | `timestamp with time zone` | 예 | 없음 | 인증 코드 발급 시각 |
| `invite_token` | `text` | 예 | 없음 | 초대 흐름의 토큰 |
| `referrer` | `text` | 예 | 없음 | 흐름 시작·복귀 출처 정보 |
| `oauth_client_state_id` | `uuid` | 예 | 없음 | 연결된 OAuth 클라이언트 흐름 상태 UUID |
| `linking_target_id` | `uuid` | 예 | 없음 | 기존 계정에 identity를 연결할 대상 사용자 UUID |
| `email_optional` | `boolean` | 아니오 | `false` | 이 흐름에서 이메일을 선택 사항으로 취급하는지 여부 |

DB 제약조건:

- `PRIMARY KEY (id)`

<a id="auth-identities"></a>

## auth.identities

Auth 사용자에 연결된 이메일·전화번호·소셜 로그인 등의 인증 identity.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `provider_id` | `text` | 아니오 | 없음 | 공급자 측 사용자 식별자 |
| `user_id` | `uuid` | 아니오 | 없음 | 연결된 auth.users.id |
| `identity_data` | `jsonb` | 아니오 | 없음 | 외부 공급자가 제공한 identity 속성 JSON |
| `provider` | `text` | 아니오 | 없음 | 인증 공급자 식별자 |
| `last_sign_in_at` | `timestamp with time zone` | 예 | 없음 | 해당 identity로 마지막 로그인한 시각 |
| `created_at` | `timestamp with time zone` | 예 | 없음 | identity 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | identity 갱신 시각 |
| `email` | `text` | 예 | `lower((identity_data ->> 'email'::text))` | identity_data.email에서 계산한 소문자 이메일 |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | identity 자체의 UUID |

DB 제약조건:

- `PRIMARY KEY (id)`
- `UNIQUE (provider_id, provider)`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="auth-instances"></a>

## auth.instances

Auth 서비스의 인스턴스 정보와 기본 설정을 위한 내부 관리 테이블.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 인스턴스 행 UUID |
| `uuid` | `uuid` | 예 | 없음 | Auth 인스턴스 식별 UUID 필드 |
| `raw_base_config` | `text` | 예 | 없음 | 인스턴스 기본 설정 원문 |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 수정 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`

<a id="auth-mfa-amr-claims"></a>

## auth.mfa_amr_claims

세션에서 사용한 인증 방식(AMR) 기록. 다중 인증 수준 판단에 사용한다.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `session_id` | `uuid` | 아니오 | 없음 | 대상 auth.sessions.id |
| `created_at` | `timestamp with time zone` | 아니오 | 없음 | 기록 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | 없음 | 기록 갱신 시각 |
| `authentication_method` | `text` | 아니오 | 없음 | 인증 방식 식별자 |
| `id` | `uuid` | 아니오 | 없음 | 인증 방식 기록 UUID |

DB 제약조건:

- `PRIMARY KEY (id)`
- `UNIQUE (session_id, authentication_method)`
- `FOREIGN KEY (session_id) REFERENCES auth.sessions(id) ON DELETE CASCADE`

<a id="auth-mfa-challenges"></a>

## auth.mfa_challenges

MFA 인증 수단 검증을 위한 일회성 챌린지.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 챌린지 UUID |
| `factor_id` | `uuid` | 아니오 | 없음 | 대상 MFA 수단 UUID |
| `created_at` | `timestamp with time zone` | 아니오 | 없음 | 챌린지 생성 시각 |
| `verified_at` | `timestamp with time zone` | 예 | 없음 | 검증 완료 시각 |
| `ip_address` | `inet` | 아니오 | 없음 | 검증 요청 IP |
| `otp_code` | `text` | 예 | 없음 | 전화 OTP 등 챌린지 코드 관련 내부 값. 민감정보 |
| `web_authn_session_data` | `jsonb` | 예 | 없음 | WebAuthn 검증 세션 데이터 JSON |

DB 제약조건:

- `FOREIGN KEY (factor_id) REFERENCES auth.mfa_factors(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`

<a id="auth-mfa-factors"></a>

## auth.mfa_factors

사용자가 등록한 TOTP·전화·WebAuthn 등 MFA 인증 수단.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 인증 수단 UUID |
| `user_id` | `uuid` | 아니오 | 없음 | 소유 Auth 사용자 UUID |
| `friendly_name` | `text` | 예 | 없음 | 사용자가 지정한 수단 표시명 |
| `factor_type` | `auth.factor_type` | 아니오 | 없음 | 인증 수단 종류 |
| `status` | `auth.factor_status` | 아니오 | 없음 | 인증 수단 등록·검증 상태 |
| `created_at` | `timestamp with time zone` | 아니오 | 없음 | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | 없음 | 수정 시각 |
| `secret` | `text` | 예 | 없음 | TOTP 등 인증 수단의 비밀값. 민감정보 |
| `phone` | `text` | 예 | 없음 | MFA 전화번호 |
| `last_challenged_at` | `timestamp with time zone` | 예 | 없음 | 마지막 챌린지 생성 시각 |
| `web_authn_credential` | `jsonb` | 예 | 없음 | WebAuthn 인증 수단 데이터 |
| `web_authn_aaguid` | `uuid` | 예 | 없음 | WebAuthn 인증장치 모델 식별자(AAGUID) |
| `last_webauthn_challenge_data` | `jsonb` | 예 | 없음 | 가장 최근 WebAuthn 등록·인증 챌린지 데이터 |

DB 제약조건:

- `UNIQUE (last_challenged_at)`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="auth-mfa-recovery-code-sets"></a>

## auth.mfa_recovery_code_sets

MFA 수단에 연결된 복구 코드 묶음과 검증 실패 잠금 상태.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 복구 코드 묶음 UUID |
| `user_id` | `uuid` | 아니오 | 없음 | 소유 Auth 사용자 UUID |
| `mfa_factor_id` | `uuid` | 아니오 | 없음 | 대상 MFA 수단 UUID |
| `failed_verification_count` | `integer` | 아니오 | `0` | 복구 코드 검증 실패 횟수 |
| `verification_locked_until` | `timestamp with time zone` | 예 | 없음 | 검증 잠금 종료 시각 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 묶음 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 묶음 수정 시각 |

DB 제약조건:

- `CHECK ((failed_verification_count >= 0))`
- `FOREIGN KEY (mfa_factor_id) REFERENCES auth.mfa_factors(id) ON DELETE CASCADE`
- `UNIQUE (mfa_factor_id)`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`
- `UNIQUE (user_id)`

<a id="auth-mfa-recovery-codes"></a>

## auth.mfa_recovery_codes

MFA 복구 코드의 해시와 사용 이력. 코드 원문이 아닌 해시를 보관한다.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 복구 코드 행 UUID |
| `mfa_recovery_code_set_id` | `uuid` | 아니오 | 없음 | 소속 복구 코드 묶음 UUID |
| `code_hash` | `text` | 아니오 | 없음 | 복구 코드 해시 |
| `consumed_at` | `timestamp with time zone` | 예 | 없음 | 일회성 코드 사용 시각 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 코드 행 생성 시각 |

DB 제약조건:

- `FOREIGN KEY (mfa_recovery_code_set_id) REFERENCES auth.mfa_recovery_code_sets(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`

<a id="auth-oauth-authorizations"></a>

## auth.oauth_authorizations

Supabase가 OAuth 서버로 동작할 때 클라이언트의 사용자 인증·인가 요청 상태.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 인가 요청 행 UUID |
| `authorization_id` | `text` | 아니오 | 없음 | 외부 흐름에서 사용하는 인가 요청 식별자 |
| `client_id` | `uuid` | 아니오 | 없음 | 요청 OAuth 클라이언트 UUID |
| `user_id` | `uuid` | 예 | 없음 | 인가하는 Auth 사용자 UUID |
| `redirect_uri` | `text` | 아니오 | 없음 | 인가 결과를 돌려줄 리다이렉트 URI |
| `scope` | `text` | 아니오 | 없음 | 요청한 권한 범위 문자열 |
| `state` | `text` | 예 | 없음 | 클라이언트가 보낸 요청 상태 값 |
| `resource` | `text` | 예 | 없음 | 접근 대상 리소스 식별자 |
| `code_challenge` | `text` | 예 | 없음 | PKCE 코드 챌린지 |
| `code_challenge_method` | `auth.code_challenge_method` | 예 | 없음 | PKCE 코드 챌린지 계산 방식 |
| `response_type` | `auth.oauth_response_type` | 아니오 | `'code'::auth.oauth_response_type` | 요청한 OAuth 응답 유형 |
| `status` | `auth.oauth_authorization_status` | 아니오 | `'pending'::auth.oauth_authorization_status` | 인가 처리 상태 |
| `authorization_code` | `text` | 예 | 없음 | 발급한 인가 코드. 민감정보 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 요청 생성 시각 |
| `expires_at` | `timestamp with time zone` | 아니오 | `(now() + '00:03:00'::interval)` | 요청 만료 시각 |
| `approved_at` | `timestamp with time zone` | 예 | 없음 | 사용자 승인 시각 |
| `nonce` | `text` | 예 | 없음 | OIDC 재사용 공격 방지 nonce |

DB 제약조건:

- `UNIQUE (authorization_code)`
- `CHECK ((char_length(authorization_code) <= 255))`
- `UNIQUE (authorization_id)`
- `FOREIGN KEY (client_id) REFERENCES auth.oauth_clients(id) ON DELETE CASCADE`
- `CHECK ((char_length(code_challenge) <= 128))`
- `CHECK ((expires_at > created_at))`
- `CHECK ((char_length(nonce) <= 255))`
- `PRIMARY KEY (id)`
- `CHECK ((char_length(redirect_uri) <= 2048))`
- `CHECK ((char_length(resource) <= 2048))`
- `CHECK ((char_length(scope) <= 4096))`
- `CHECK ((char_length(state) <= 4096))`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="auth-oauth-client-states"></a>

## auth.oauth_client_states

외부 OAuth 로그인 흐름에서 코드 교환에 사용하는 임시 클라이언트 상태.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 상태 UUID |
| `provider_type` | `text` | 아니오 | 없음 | 외부 OAuth 공급자 유형 |
| `code_verifier` | `text` | 예 | 없음 | PKCE 코드 검증 원문. 민감정보 |
| `created_at` | `timestamp with time zone` | 아니오 | 없음 | 상태 생성 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`

<a id="auth-oauth-clients"></a>

## auth.oauth_clients

Supabase OAuth 서버에 등록된 클라이언트 애플리케이션.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 클라이언트 UUID |
| `client_secret_hash` | `text` | 예 | 없음 | 클라이언트 시크릿 해시 |
| `registration_type` | `auth.oauth_registration_type` | 아니오 | 없음 | 클라이언트 등록 방식 |
| `redirect_uris` | `text` | 아니오 | 없음 | 허용된 리다이렉트 URI 목록 |
| `grant_types` | `text` | 아니오 | 없음 | 허용된 OAuth grant 유형 목록 |
| `client_name` | `text` | 예 | 없음 | 클라이언트 표시명 |
| `client_uri` | `text` | 예 | 없음 | 클라이언트 홈페이지 URI |
| `logo_uri` | `text` | 예 | 없음 | 클라이언트 로고 URI |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 등록 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 수정 시각 |
| `deleted_at` | `timestamp with time zone` | 예 | 없음 | 삭제 처리 시각 |
| `client_type` | `auth.oauth_client_type` | 아니오 | `'confidential'::auth.oauth_client_type` | 공개·기밀 등 클라이언트 유형 |
| `token_endpoint_auth_method` | `text` | 아니오 | 없음 | 토큰 엔드포인트에서 사용할 클라이언트 인증 방식 |

DB 제약조건:

- `CHECK ((char_length(client_name) <= 1024))`
- `CHECK ((char_length(client_uri) <= 2048))`
- `CHECK ((char_length(logo_uri) <= 2048))`
- `PRIMARY KEY (id)`
- `CHECK ((token_endpoint_auth_method = ANY (ARRAY['client_secret_basic'::text, 'client_secret_post'::text, 'none'::text])))`

<a id="auth-oauth-consents"></a>

## auth.oauth_consents

사용자가 OAuth 클라이언트에 허용한 권한 범위와 철회 이력.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 동의 UUID |
| `user_id` | `uuid` | 아니오 | 없음 | 동의한 Auth 사용자 UUID |
| `client_id` | `uuid` | 아니오 | 없음 | 권한을 받은 OAuth 클라이언트 UUID |
| `scopes` | `text` | 아니오 | 없음 | 승인한 권한 범위 문자열 |
| `granted_at` | `timestamp with time zone` | 아니오 | `now()` | 권한 승인 시각 |
| `revoked_at` | `timestamp with time zone` | 예 | 없음 | 동의 철회 시각 |

DB 제약조건:

- `FOREIGN KEY (client_id) REFERENCES auth.oauth_clients(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`
- `CHECK (((revoked_at IS NULL) OR (revoked_at >= granted_at)))`
- `CHECK ((char_length(scopes) <= 2048))`
- `CHECK ((char_length(TRIM(BOTH FROM scopes)) > 0))`
- `UNIQUE (user_id, client_id)`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="auth-one-time-tokens"></a>

## auth.one_time_tokens

이메일 확인·비밀번호 복구 등 Auth 일회성 토큰의 해시와 유효기간.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 토큰 행 UUID |
| `user_id` | `uuid` | 아니오 | 없음 | 대상 Auth 사용자 UUID |
| `token_type` | `auth.one_time_token_type` | 아니오 | 없음 | 일회성 토큰 용도 |
| `token_hash` | `text` | 아니오 | 없음 | 토큰 해시 |
| `relates_to` | `text` | 아니오 | 없음 | 토큰이 연결된 이메일 등 대상 식별값 |
| `created_at` | `timestamp without time zone` | 아니오 | `now()` | 생성 시각 |
| `updated_at` | `timestamp without time zone` | 아니오 | `now()` | 수정 시각 |
| `expires_at` | `timestamp with time zone` | 예 | 없음 | 토큰 만료 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `CHECK ((char_length(token_hash) > 0))`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="auth-refresh-tokens"></a>

## auth.refresh_tokens

Auth 세션 갱신 토큰과 회전·폐기 상태. public.revoked_sessions와 역할이 다르다.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `instance_id` | `uuid` | 예 | 없음 | Auth 인스턴스 UUID |
| `id` | `bigint` | 아니오 | `nextval('auth.refresh_tokens_id_seq'::regclass)` | 갱신 토큰 행 순번 |
| `token` | `character varying(255)` | 예 | 없음 | 리프레시 토큰 내부 값. 민감정보 |
| `user_id` | `character varying(255)` | 예 | 없음 | Auth 사용자 식별자 |
| `revoked` | `boolean` | 예 | 없음 | 토큰 폐기 여부 |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 발급 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 수정 시각 |
| `parent` | `character varying(255)` | 예 | 없음 | 토큰 회전 전 부모 토큰 식별값 |
| `session_id` | `uuid` | 예 | 없음 | 연결된 Auth 세션 UUID |

DB 제약조건:

- `PRIMARY KEY (id)`
- `FOREIGN KEY (session_id) REFERENCES auth.sessions(id) ON DELETE CASCADE`
- `UNIQUE (token)`

<a id="auth-saml-providers"></a>

## auth.saml_providers

기업 SAML SSO 공급자의 메타데이터와 사용자 속성 매핑.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | SAML 공급자 설정 UUID |
| `sso_provider_id` | `uuid` | 아니오 | 없음 | 상위 SSO 공급자 UUID |
| `entity_id` | `text` | 아니오 | 없음 | SAML IdP entityID |
| `metadata_xml` | `text` | 아니오 | 없음 | SAML 메타데이터 XML |
| `metadata_url` | `text` | 예 | 없음 | SAML 메타데이터 조회 URL |
| `attribute_mapping` | `jsonb` | 예 | 없음 | SAML 속성의 사용자 정보 매핑 JSON |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 수정 시각 |
| `name_id_format` | `text` | 예 | 없음 | 요청할 SAML NameID 형식 |

DB 제약조건:

- `CHECK ((char_length(entity_id) > 0))`
- `CHECK (((metadata_url = NULL::text) OR (char_length(metadata_url) > 0)))`
- `CHECK ((char_length(metadata_xml) > 0))`
- `UNIQUE (entity_id)`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE`

<a id="auth-saml-relay-states"></a>

## auth.saml_relay_states

SAML 요청과 로그인 완료 후 리다이렉트를 연결하는 임시 RelayState.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | RelayState UUID |
| `sso_provider_id` | `uuid` | 아니오 | 없음 | 대상 SSO 공급자 UUID |
| `request_id` | `text` | 아니오 | 없음 | SAML 인증 요청 ID |
| `for_email` | `text` | 예 | 없음 | 로그인 대상 이메일 |
| `redirect_to` | `text` | 예 | 없음 | 완료 후 이동할 URL |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 수정 시각 |
| `flow_state_id` | `uuid` | 예 | 없음 | 연결된 Auth 흐름 상태 UUID |

DB 제약조건:

- `CHECK ((char_length(request_id) > 0))`
- `FOREIGN KEY (flow_state_id) REFERENCES auth.flow_state(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE`

<a id="auth-schema-migrations"></a>

## auth.schema_migrations

Supabase Auth 자체 DB 스키마의 적용 버전. 앱 Flyway 이력과 별개이다.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `version` | `character varying(255)` | 아니오 | 없음 | 적용된 Auth 마이그레이션 버전 |

DB 제약조건:

- `PRIMARY KEY (version)`

<a id="auth-scim-tokens"></a>

## auth.scim_tokens

기업 디렉터리의 SCIM 사용자 자동 프로비저닝 API 인증 토큰.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | SCIM 토큰 UUID |
| `sso_provider_id` | `uuid` | 아니오 | 없음 | 연결된 SSO 공급자 UUID |
| `token_hash` | `text` | 아니오 | 없음 | SCIM 토큰 해시 |
| `prefix` | `text` | 아니오 | 없음 | 토큰 식별용 접두 문자열 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 발급 시각 |
| `expires_at` | `timestamp with time zone` | 예 | 없음 | 만료 시각 |
| `revoked_at` | `timestamp with time zone` | 예 | 없음 | 폐기 시각 |
| `last_used_at` | `timestamp with time zone` | 예 | 없음 | 마지막 사용 시각 |

DB 제약조건:

- `CHECK (((expires_at IS NULL) OR (expires_at > created_at)))`
- `PRIMARY KEY (id)`
- `CHECK (((revoked_at IS NULL) OR (revoked_at >= created_at)))`
- `FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE`
- `CHECK ((token_hash ~ '^[0-9a-f]{64}$'::text))`

<a id="auth-scim-users"></a>

## auth.scim_users

기업 SCIM 디렉터리 사용자와 Supabase Auth 사용자의 연결 정보.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | SCIM 사용자 행 UUID |
| `sso_provider_id` | `uuid` | 아니오 | 없음 | 연결된 SSO 공급자 UUID |
| `user_id` | `uuid` | 예 | 없음 | 연결된 auth.users.id |
| `resource` | `jsonb` | 아니오 | 없음 | SCIM 사용자 리소스 JSON |
| `user_name` | `text` | 아니오 | `lower((resource ->> 'userName'::text))` | SCIM userName |
| `external_id` | `text` | 예 | `(resource ->> 'externalId'::text)` | 외부 디렉터리의 사용자 식별자 |
| `active` | `boolean` | 아니오 | `COALESCE(((resource ->> 'active'::text))::boolean, true)` | 디렉터리 사용자 활성 상태 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 수정 시각 |
| `deleted_at` | `timestamp with time zone` | 예 | 없음 | 삭제 처리 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE SET NULL`

<a id="auth-sessions"></a>

## auth.sessions

Auth 로그인 세션과 인증 수준·만료·갱신 정보.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 세션 UUID. JWT의 session_id와 대응 |
| `user_id` | `uuid` | 아니오 | 없음 | 로그인한 Auth 사용자 UUID |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 세션 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 세션 갱신 시각 |
| `factor_id` | `uuid` | 예 | 없음 | 인증에 연결된 MFA 수단 UUID |
| `aal` | `auth.aal_level` | 예 | 없음 | 인증 보증 수준(AAL) |
| `not_after` | `timestamp with time zone` | 예 | 없음 | 세션의 최종 허용 종료 시각 |
| `refreshed_at` | `timestamp without time zone` | 예 | 없음 | 최근 토큰 갱신 시각 |
| `user_agent` | `text` | 예 | 없음 | 접속 브라우저·앱 User-Agent |
| `ip` | `inet` | 예 | 없음 | 접속 IP |
| `tag` | `text` | 예 | 없음 | 세션 분류용 내부 태그 |
| `oauth_client_id` | `uuid` | 예 | 없음 | 연결된 OAuth 클라이언트 UUID |
| `refresh_token_hmac_key` | `text` | 예 | 없음 | 리프레시 토큰 검증용 HMAC 키. 민감정보 |
| `refresh_token_counter` | `bigint` | 예 | 없음 | 리프레시 토큰 발급·회전 카운터 |
| `scopes` | `text` | 예 | 없음 | 세션에 허용한 권한 범위 |

DB 제약조건:

- `FOREIGN KEY (oauth_client_id) REFERENCES auth.oauth_clients(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`
- `CHECK ((char_length(scopes) <= 4096))`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="auth-sso-domains"></a>

## auth.sso_domains

이메일 도메인을 기업 SSO 공급자에 연결하는 매핑.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 도메인 매핑 UUID |
| `sso_provider_id` | `uuid` | 아니오 | 없음 | 대상 SSO 공급자 UUID |
| `domain` | `text` | 아니오 | 없음 | SSO 적용 이메일 도메인 |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 수정 시각 |

DB 제약조건:

- `CHECK ((char_length(domain) > 0))`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE`

<a id="auth-sso-providers"></a>

## auth.sso_providers

SAML·SCIM 등 기업 SSO 공급자의 공통 관리 정보.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | SSO 공급자 UUID |
| `resource_id` | `text` | 예 | 없음 | 외부 관리용 리소스 식별자 |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 수정 시각 |
| `disabled` | `boolean` | 예 | 없음 | SSO 공급자 비활성화 여부 |

DB 제약조건:

- `CHECK (((resource_id = NULL::text) OR (char_length(resource_id) > 0)))`
- `PRIMARY KEY (id)`

<a id="auth-users"></a>

## auth.users

Supabase Auth의 로그인 계정 원장. 앱 업무 프로필은 public.profiles에 별도로 저장한다.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `instance_id` | `uuid` | 예 | 없음 | Auth 인스턴스 UUID |
| `id` | `uuid` | 아니오 | 없음 | Auth 사용자 UUID |
| `aud` | `character varying(255)` | 예 | 없음 | 토큰 대상 audience |
| `role` | `character varying(255)` | 예 | 없음 | 인증 DB 역할. 앱의 customer/manager 구분과 다름 |
| `email` | `character varying(255)` | 예 | 없음 | 로그인 이메일 |
| `encrypted_password` | `character varying(255)` | 예 | 없음 | 비밀번호 해시. 이름과 달리 복호화하는 원문 저장이 아님 |
| `email_confirmed_at` | `timestamp with time zone` | 예 | 없음 | 이메일 인증 완료 시각 |
| `invited_at` | `timestamp with time zone` | 예 | 없음 | 초대 발송 시각 |
| `confirmation_token` | `character varying(255)` | 예 | 없음 | 이메일 확인 토큰 내부 값. 민감정보 |
| `confirmation_sent_at` | `timestamp with time zone` | 예 | 없음 | 이메일 확인 메시지 발송 시각 |
| `recovery_token` | `character varying(255)` | 예 | 없음 | 비밀번호 복구 토큰 내부 값. 민감정보 |
| `recovery_sent_at` | `timestamp with time zone` | 예 | 없음 | 복구 메시지 발송 시각 |
| `email_change_token_new` | `character varying(255)` | 예 | 없음 | 새 이메일 측 변경 확인 토큰 |
| `email_change` | `character varying(255)` | 예 | 없음 | 변경하려는 새 이메일 |
| `email_change_sent_at` | `timestamp with time zone` | 예 | 없음 | 이메일 변경 메시지 발송 시각 |
| `last_sign_in_at` | `timestamp with time zone` | 예 | 없음 | 마지막 로그인 시각 |
| `raw_app_meta_data` | `jsonb` | 예 | 없음 | 서버가 관리하는 앱 메타데이터 JSON |
| `raw_user_meta_data` | `jsonb` | 예 | 없음 | 사용자가 수정할 수 있는 프로필 메타데이터 JSON |
| `is_super_admin` | `boolean` | 예 | 없음 | Auth 내부 최고 관리자 여부 |
| `created_at` | `timestamp with time zone` | 예 | 없음 | 계정 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | 없음 | 계정 수정 시각 |
| `phone` | `text` | 예 | `NULL::character varying` | 로그인 전화번호 |
| `phone_confirmed_at` | `timestamp with time zone` | 예 | 없음 | 전화번호 인증 완료 시각 |
| `phone_change` | `text` | 예 | `''::character varying` | 변경하려는 새 전화번호 |
| `phone_change_token` | `character varying(255)` | 예 | `''::character varying` | 전화번호 변경 인증 토큰 |
| `phone_change_sent_at` | `timestamp with time zone` | 예 | 없음 | 전화번호 변경 메시지 발송 시각 |
| `confirmed_at` | `timestamp with time zone` | 예 | `LEAST(email_confirmed_at, phone_confirmed_at)` | 이메일·전화 중 확인된 인증 시각을 계산한 컬럼 |
| `email_change_token_current` | `character varying(255)` | 예 | `''::character varying` | 기존 이메일 측 변경 확인 토큰 |
| `email_change_confirm_status` | `smallint` | 예 | `0` | 이메일 변경 확인 진행 상태 코드 |
| `banned_until` | `timestamp with time zone` | 예 | 없음 | 로그인 차단 종료 시각 |
| `reauthentication_token` | `character varying(255)` | 예 | `''::character varying` | 재인증 토큰 내부 값 |
| `reauthentication_sent_at` | `timestamp with time zone` | 예 | 없음 | 재인증 메시지 발송 시각 |
| `is_sso_user` | `boolean` | 아니오 | `false` | SSO 계정 여부 |
| `deleted_at` | `timestamp with time zone` | 예 | 없음 | 계정 삭제 처리 시각 |
| `is_anonymous` | `boolean` | 아니오 | `false` | 익명 로그인 계정 여부 |

DB 제약조건:

- `CHECK (((email_change_confirm_status >= 0) AND (email_change_confirm_status <= 2)))`
- `UNIQUE (phone)`
- `PRIMARY KEY (id)`

<a id="auth-webauthn-challenges"></a>

## auth.webauthn_challenges

패스키/WebAuthn 등록·로그인의 일회성 챌린지와 만료 상태.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 챌린지 UUID |
| `user_id` | `uuid` | 예 | 없음 | 대상 Auth 사용자 UUID |
| `challenge_type` | `text` | 아니오 | 없음 | 등록·인증 등 챌린지 유형 |
| `session_data` | `jsonb` | 아니오 | 없음 | WebAuthn 세션 검증 데이터 JSON |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 생성 시각 |
| `expires_at` | `timestamp with time zone` | 아니오 | 없음 | 만료 시각 |

DB 제약조건:

- `CHECK ((challenge_type = ANY (ARRAY['signup'::text, 'registration'::text, 'authentication'::text])))`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="auth-webauthn-credentials"></a>

## auth.webauthn_credentials

사용자가 등록한 패스키/WebAuthn 공개키 인증 정보. 개인키는 인증장치에 보관한다.

소유자: `supabase_auth_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 인증 정보 UUID |
| `user_id` | `uuid` | 아니오 | 없음 | 소유 Auth 사용자 UUID |
| `credential_id` | `bytea` | 아니오 | 없음 | 인증장치가 발급한 credential ID |
| `public_key` | `bytea` | 아니오 | 없음 | 서명 검증 공개키 |
| `attestation_type` | `text` | 아니오 | `''::text` | 인증장치 증명 유형 |
| `aaguid` | `uuid` | 예 | 없음 | 인증장치 모델 식별자(AAGUID) |
| `sign_count` | `bigint` | 아니오 | `0` | 서명 카운터. 재사용·복제 탐지에 활용 |
| `transports` | `jsonb` | 아니오 | `'[]'::jsonb` | 지원 전송 방식 목록 |
| `backup_eligible` | `boolean` | 아니오 | `false` | 백업 가능한 인증 정보 여부 |
| `backed_up` | `boolean` | 아니오 | `false` | 백업된 인증 정보 여부 |
| `friendly_name` | `text` | 아니오 | `''::text` | 패스키 표시명 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 등록 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 수정 시각 |
| `last_used_at` | `timestamp with time zone` | 예 | 없음 | 마지막 사용 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE`

<a id="public-addresses"></a>

## public.addresses

고객의 저장 주소. 기본 주소와 방문지 검색 정보를 관리한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 주소 UUID |
| `customer_id` | `uuid` | 아니오 | 없음 | 주소 소유 고객의 profiles.id |
| `label` | `text` | 아니오 | `'기타'::text` | 주소 별칭(예: 집, 회사, 기타) |
| `road_address` | `text` | 아니오 | 없음 | 도로명 주소 |
| `detail_address` | `text` | 예 | 없음 | 동·호수 등 상세 주소 |
| `sigungu` | `text` | 예 | 없음 | 매칭에 사용하는 시·군·구 |
| `sido` | `text` | 예 | 없음 | 매칭에 사용하는 시·도 |
| `is_default` | `boolean` | 아니오 | `false` | 고객의 기본 주소 여부 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 주소 등록 시각 |

DB 제약조건:

- `FOREIGN KEY (customer_id) REFERENCES profiles(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`

<a id="public-app-settings"></a>

## public.app_settings

가격 정책·매칭 정책·화면 문구 등 서버 운영 설정. 실제 값은 key별 JSON 구조로 저장한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `key` | `text` | 아니오 | 없음 | 설정 식별 키 |
| `value` | `jsonb` | 아니오 | 없음 | 해당 설정의 JSON 값(숫자·문자열·객체·배열 등) |

DB 제약조건:

- `PRIMARY KEY (key)`

<a id="public-booking-series"></a>

## public.booking_series

매월 1회 방문하는 정기예약 묶음. 회차별 실제 예약은 bookings.series_id로 연결한다. 같은 날짜가 없는 달은 말일로 보정한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 정기예약 묶음 UUID |
| `customer_id` | `uuid` | 아니오 | 없음 | 예약한 고객의 profiles.id |
| `category_key` | `text` | 아니오 | 없음 | 서비스 분류 키 |
| `weekday` | `smallint` | 아니오 | 없음 | 첫 방문 요일 기록(0=월~6=일). 현재 월 단위 반복 계산 기준은 term_start |
| `service_time` | `time without time zone` | 아니오 | 없음 | 묶음 생성 시 모든 회차에 적용한 방문 시각(서울 기준) |
| `road_address` | `text` | 아니오 | 없음 | 생성 당시 도로명 주소 |
| `detail_address` | `text` | 예 | 없음 | 생성 당시 상세 주소 |
| `free_options` | `text[]` | 아니오 | `'{}'::text[]` | 선택한 무료 옵션 키 배열 |
| `paid_options` | `text[]` | 아니오 | `'{}'::text[]` | 선택한 유료 옵션 키 배열 |
| `notices` | `text[]` | 아니오 | `'{}'::text[]` | 현장 안내 칩 키 배열 |
| `request` | `text` | 예 | 없음 | 고객 요청사항 |
| `status` | `text` | 아니오 | 없음 | active=진행 중, ended=종료, cancelled=취소 |
| `term_start` | `date` | 아니오 | 없음 | 첫 방문일. 월별 회차 생성 기준 날짜 |
| `term_end` | `date` | 아니오 | 없음 | 마지막 회차 방문일 |
| `visits_planned` | `integer` | 아니오 | 없음 | 생성 시 계획한 방문 횟수 |
| `ending_notified_at` | `timestamp with time zone` | 예 | 없음 | 종료 임박 안내를 발송한 시각. 중복 발송 방지 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 묶음 생성 시각 |
| `cancelled_at` | `timestamp with time zone` | 예 | 없음 | 묶음 취소 시각 |

DB 제약조건:

- `FOREIGN KEY (category_key) REFERENCES service_categories(key)`
- `FOREIGN KEY (customer_id) REFERENCES profiles(id)`
- `PRIMARY KEY (id)`
- `CHECK ((status = ANY (ARRAY['active'::text, 'ended'::text, 'cancelled'::text])))`
- `CHECK (((weekday >= 0) AND (weekday <= 6)))`

<a id="public-booking-status-history"></a>

## public.booking_status_history

예약 상태 전환 이력. 누가 언제 어떤 사유로 상태를 바꿨는지 기록한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `bigint` | 아니오 | `nextval('booking_status_history_id_seq'::regclass)` | 이력 순번 |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약의 bookings.id |
| `from_status` | `text` | 예 | 없음 | 변경 전 상태. 최초 전환이면 null 가능 |
| `to_status` | `text` | 아니오 | 없음 | 변경 후 상태 |
| `actor_id` | `uuid` | 예 | 없음 | 상태를 변경한 사용자 UUID. null이면 시스템 처리 |
| `reason` | `text` | 예 | 없음 | 상태 변경 사유 |
| `at` | `timestamp with time zone` | 아니오 | `now()` | 상태 전환 시각 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`

<a id="public-bookings"></a>

## public.bookings

고객 예약이자 매니저의 작업 단위. 일정·주소·가격 스냅샷과 배정·결제·촬영 진행 상태를 보관한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 예약 UUID |
| `series_id` | `uuid` | 예 | 없음 | 정기예약 묶음의 booking_series.id. 일회성 예약은 null |
| `customer_id` | `uuid` | 아니오 | 없음 | 예약 고객의 profiles.id |
| `manager_id` | `uuid` | 예 | 없음 | 배정 매니저의 profiles.id. 미배정이면 null |
| `category_key` | `text` | 아니오 | 없음 | 서비스 분류의 service_categories.key |
| `frequency` | `text` | 아니오 | 없음 | recurring=정기, oneTime=일회성 |
| `service_date` | `date` | 아니오 | 없음 | 방문 날짜(서울 기준) |
| `service_time` | `time without time zone` | 아니오 | 없음 | 방문 시각(서울 기준) |
| `road_address` | `text` | 아니오 | 없음 | 예약 당시 도로명 주소 스냅샷 |
| `detail_address` | `text` | 예 | 없음 | 예약 당시 상세 주소 스냅샷 |
| `sigungu` | `text` | 예 | 없음 | 매니저 지역 매칭용 시·군·구 |
| `sido` | `text` | 예 | 없음 | 매니저 지역 매칭용 시·도 |
| `free_options` | `text[]` | 아니오 | `'{}'::text[]` | 선택한 무료 옵션 키 배열 |
| `paid_options` | `text[]` | 아니오 | `'{}'::text[]` | 선택한 유료 옵션 키 배열 |
| `notices` | `text[]` | 아니오 | `'{}'::text[]` | 현장 안내 칩 키 배열 |
| `request` | `text` | 예 | 없음 | 고객 요청사항 |
| `repair_type` | `text` | 예 | 없음 | 수리 서비스 종류 키 |
| `base_amount` | `bigint` | 아니오 | 없음 | 기본 서비스 금액(원) |
| `options_amount` | `bigint` | 아니오 | `0` | 유료 옵션 합계(원) |
| `total_amount` | `bigint` | 아니오 | 없음 | 예약 총액(원) |
| `platform_fee` | `bigint` | 아니오 | 없음 | 생성 시 정책으로 확정한 플랫폼 수수료(원) |
| `duration_minutes` | `integer` | 아니오 | 없음 | 예약 당시 예상 소요 시간(분). 일정 겹침 검사에 사용 |
| `starts_at` | `timestamp with time zone` | 아니오 | 없음 | 서울 방문 일시를 변환한 절대 시작 시각. 취소 기한·겹침 검사 기준 |
| `ends_at` | `timestamp with time zone` | 아니오 | 없음 | 예정 종료 시각. starts_at에 duration_minutes를 더한 값 |
| `status` | `text` | 아니오 | 없음 | requested=요청, assigning=매칭 중, scheduled=배정, in_progress=작업 중, paused=중지, done=완료, cancelled=취소 |
| `payment_status` | `text` | 아니오 | `'unpaid'::text` | unpaid=미결제, authorized=승인, paid=결제 완료, refunded=환불 |
| `assigned_at` | `timestamp with time zone` | 예 | 없음 | 매니저 배정 시각 |
| `started_at` | `timestamp with time zone` | 예 | 없음 | 실제 작업 시작 시각 |
| `ended_at` | `timestamp with time zone` | 예 | 없음 | 실제 작업 종료 시각 |
| `cancelled_at` | `timestamp with time zone` | 예 | 없음 | 예약 취소 시각 |
| `cancel_reason` | `text` | 예 | 없음 | 예약 취소 사유 |
| `dispatch_round` | `integer` | 아니오 | `1` | 현재 오퍼 발송 회차. 재매칭·일정 변경 시 증가 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 예약 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 예약 최종 수정 시각 |
| `repair_appliance` | `text` | 예 | 없음 | 수리 가전 표시명 스냅샷(예: 냉장고) |
| `repair_symptom` | `text` | 예 | 없음 | 수리 증상 표시명 스냅샷 |
| `repair_detail` | `text` | 예 | 없음 | 수리 증상 상세 설명 |
| `repair_photos` | `text[]` | 아니오 | `'{}'::text[]` | repair-photos 버킷의 수리 사진 경로 배열 |
| `matching_failed_at` | `timestamp with time zone` | 예 | 없음 | 매칭 실패를 기록한 시각 |
| `repair_appliance_key` | `text` | 예 | 없음 | 수리 가전의 안정적인 분류 키(예: fridge) |
| `repair_symptom_key` | `text` | 예 | 없음 | 수리 증상의 안정적인 분류 키(예: cooling) |
| `late_notified_at` | `timestamp with time zone` | 예 | 없음 | 매니저 지각 안내를 발송한 시각. 중복 안내 방지 |
| `matching_from_round` | `integer` | 아니오 | `1` | 현재 매칭 구간의 시작 회차. 이전 일정의 오퍼와 구분 |
| `dispatch_held_at` | `timestamp with time zone` | 예 | 없음 | 야간 콜 발송을 보류한 시각. 보류 해제 시 null |
| `dispatch_held_manager_id` | `uuid` | 예 | 없음 | 보류 해제 후 우선 오퍼를 받을 매니저 UUID |
| `dispatch_held_rescheduled` | `boolean` | 아니오 | `false` | 보류된 우선 매니저가 일정 변경 전 담당자인지 여부 |
| `required_photo_rooms` | `text[]` | 아니오 | `'{}'::text[]` | 작업 시작 시 복사한 필수 전·후 사진 공간명 배열 |
| `before_photos_confirmed_at` | `timestamp with time zone` | 예 | 없음 | 모든 필수 공간의 전 사진 검사 통과 시각 |
| `video_finished_at` | `timestamp with time zone` | 예 | 없음 | 매니저의 라이브 종료 요청 시각. 이후 후 사진 촬영 단계 |

DB 제약조건:

- `FOREIGN KEY (category_key) REFERENCES service_categories(key)`
- `FOREIGN KEY (customer_id) REFERENCES profiles(id)`
- `FOREIGN KEY (dispatch_held_manager_id) REFERENCES profiles(id) ON DELETE SET NULL`
- `CHECK ((frequency = ANY (ARRAY['recurring'::text, 'oneTime'::text])))`
- `FOREIGN KEY (manager_id) REFERENCES profiles(id)`
- `EXCLUDE USING gist (manager_id WITH =, tstzrange(starts_at, ends_at) WITH &&) WHERE (((manager_id IS NOT NULL) AND (status = ANY (ARRAY['scheduled'::text, 'in_progress'::text, 'paused'::text]))))`
- `CHECK ((payment_status = ANY (ARRAY['unpaid'::text, 'authorized'::text, 'paid'::text, 'refunded'::text])))`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (series_id) REFERENCES booking_series(id)`
- `CHECK ((status = ANY (ARRAY['requested'::text, 'assigning'::text, 'scheduled'::text, 'in_progress'::text, 'paused'::text, 'done'::text, 'cancelled'::text])))`

<a id="public-catalog-frequency-copy"></a>

## public.catalog_frequency_copy

서비스 분류·이용 주기에 따른 예약 화면 안내 문구.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `category_key` | `text` | 아니오 | 없음 | 서비스 분류 키 |
| `frequency` | `text` | 아니오 | 없음 | 이용 주기(recurring 또는 oneTime) |
| `description` | `text` | 아니오 | 없음 | 해당 주기의 안내 설명 |

DB 제약조건:

- `FOREIGN KEY (category_key) REFERENCES service_categories(key)`
- `PRIMARY KEY (category_key, frequency)`

<a id="public-catalog-notice-chips"></a>

## public.catalog_notice_chips

예약 시 선택할 수 있는 현장 상황 안내 칩 목록.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `category_key` | `text` | 아니오 | 없음 | 서비스 분류 키 |
| `key` | `text` | 아니오 | 없음 | 안내 칩 식별 키 |
| `label` | `text` | 아니오 | 없음 | 화면 표시 문구 |
| `icon` | `text` | 아니오 | 없음 | 안내 칩 아이콘 키 |
| `sort` | `integer` | 아니오 | 없음 | 화면 표시 순서 |

DB 제약조건:

- `FOREIGN KEY (category_key) REFERENCES service_categories(key)`
- `PRIMARY KEY (category_key, key)`

<a id="public-catalog-options"></a>

## public.catalog_options

서비스별 무료·유료 추가 옵션 카탈로그.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `category_key` | `text` | 아니오 | 없음 | 서비스 분류 키 |
| `key` | `text` | 아니오 | 없음 | 옵션 식별 키 |
| `kind` | `text` | 아니오 | 없음 | free=무료, paid=유료 |
| `label` | `text` | 아니오 | 없음 | 옵션 표시명 |
| `price` | `bigint` | 아니오 | `0` | 추가 요금(원) |
| `icon` | `smallint` | 아니오 | 없음 | 앱 옵션 아이콘 배열의 인덱스 |
| `sort` | `integer` | 아니오 | 없음 | 화면 표시 순서 |

DB 제약조건:

- `FOREIGN KEY (category_key) REFERENCES service_categories(key)`
- `CHECK ((kind = ANY (ARRAY['free'::text, 'paid'::text])))`
- `PRIMARY KEY (category_key, key)`

<a id="public-catalog-prices"></a>

## public.catalog_prices

서비스 분류·이용 주기별 기본 가격표.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `category_key` | `text` | 아니오 | 없음 | 서비스 분류 키 |
| `frequency` | `text` | 아니오 | 없음 | 이용 주기(recurring=정기, oneTime=일회성) |
| `base_amount` | `bigint` | 아니오 | 없음 | 기본 금액(원) |

DB 제약조건:

- `FOREIGN KEY (category_key) REFERENCES service_categories(key)`
- `CHECK ((frequency = ANY (ARRAY['recurring'::text, 'oneTime'::text])))`
- `PRIMARY KEY (category_key, frequency)`

<a id="public-catalog-reasons"></a>

## public.catalog_reasons

매니저의 콜 거절·촬영 중단 사유 선택지.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `kind` | `text` | 아니오 | 없음 | 사유 구분(reject=콜 거절, pause=중단) |
| `label` | `text` | 아니오 | 없음 | 사유 표시 문구 |
| `sort` | `integer` | 아니오 | 없음 | 화면 표시 순서 |

DB 제약조건:

- `CHECK ((kind = ANY (ARRAY['reject'::text, 'pause'::text])))`
- `PRIMARY KEY (kind, label)`

<a id="public-checklist-templates"></a>

## public.checklist_templates

서비스별 작업 체크리스트 원본. 예약 배정 시 실제 작업 항목으로 복사한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `category_key` | `text` | 아니오 | 없음 | 서비스 분류 키 |
| `group_name` | `text` | 아니오 | 없음 | 공간·작업 그룹 이름 |
| `label` | `text` | 아니오 | 없음 | 체크리스트 항목 문구 |
| `sort` | `integer` | 아니오 | 없음 | 표시 순서 |

DB 제약조건:

- `FOREIGN KEY (category_key) REFERENCES service_categories(key)`
- `PRIMARY KEY (category_key, group_name, label)`

<a id="public-cleaning-photos"></a>

## public.cleaning_photos

예약별 공간의 청소 전·후 사진 쌍. 파일은 비공개 job-photos Storage에 저장한다. 고객에게는 예약 완료 이후 공개한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 사진 쌍 UUID |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약 ID |
| `location` | `text` | 아니오 | 없음 | 필수 촬영 공간명. required_photo_rooms와 연결 |
| `sort` | `integer` | 아니오 | 없음 | 사진 표시 순서 |
| `before_path` | `text` | 아니오 | 없음 | 청소 전 사진의 비공개 Storage 경로 |
| `before_at` | `timestamp with time zone` | 아니오 | 없음 | 청소 전 사진 등록 시각 |
| `after_path` | `text` | 예 | 없음 | 청소 후 사진의 비공개 Storage 경로. 미촬영이면 null |
| `after_at` | `timestamp with time zone` | 예 | 없음 | 청소 후 사진 등록·교체 시각 |
| `created_at` | `timestamp with time zone` | 아니오 | 없음 | 사진 쌍 생성 시각 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE`
- `CHECK (((char_length(location) >= 1) AND (char_length(location) <= 20)))`
- `PRIMARY KEY (id)`

<a id="public-customer-housing"></a>

## public.customer_housing

고객의 주거 형태·면적·방 수·반려동물 정보.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `customer_id` | `uuid` | 아니오 | 없음 | 고객의 profiles.id. 고객별 1행 |
| `housing_type` | `text` | 아니오 | 없음 | apartment=아파트, studio=원룸, house=주택, other=기타 |
| `size` | `text` | 예 | 없음 | 면적·평수 자유 입력값 |
| `size_unknown` | `boolean` | 아니오 | `false` | 면적을 모름으로 선택했는지 여부 |
| `rooms` | `text` | 예 | 없음 | 방 개수 입력값 |
| `bathrooms` | `text` | 예 | 없음 | 욕실 개수 입력값 |
| `pet` | `text` | 아니오 | 없음 | 반려동물 유무(yes/no) |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 주거 정보 수정 시각 |

DB 제약조건:

- `FOREIGN KEY (customer_id) REFERENCES profiles(id) ON DELETE CASCADE`
- `CHECK ((housing_type = ANY (ARRAY['apartment'::text, 'studio'::text, 'house'::text, 'other'::text])))`
- `CHECK ((pet = ANY (ARRAY['yes'::text, 'no'::text])))`
- `PRIMARY KEY (customer_id)`

<a id="public-faqs"></a>

## public.faqs

고객·매니저용 자주 묻는 질문과 답변.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | FAQ UUID |
| `audience` | `text` | 아니오 | 없음 | 노출 대상(customer=고객, manager=매니저) |
| `question` | `text` | 아니오 | 없음 | 질문 문구 |
| `answer` | `text` | 아니오 | 없음 | 답변 본문 |
| `sort` | `integer` | 아니오 | 없음 | 표시 순서 |

DB 제약조건:

- `CHECK ((audience = ANY (ARRAY['customer'::text, 'manager'::text])))`
- `PRIMARY KEY (id)`

<a id="public-flyway-schema-history"></a>

## public.flyway_schema_history

Spring 백엔드 Flyway의 DB 마이그레이션 적용 이력. 업무 데이터가 아닌 스키마 버전 관리용이다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `installed_rank` | `integer` | 아니오 | 없음 | 적용 순번(기본 키) |
| `version` | `character varying(50)` | 예 | 없음 | 마이그레이션 버전 |
| `description` | `character varying(200)` | 아니오 | 없음 | 마이그레이션 설명 |
| `type` | `character varying(20)` | 아니오 | 없음 | 마이그레이션 종류(예: SQL) |
| `script` | `character varying(1000)` | 아니오 | 없음 | 실행한 스크립트 이름 |
| `checksum` | `integer` | 예 | 없음 | 스크립트 체크섬 |
| `installed_by` | `character varying(100)` | 아니오 | 없음 | 실행 DB 사용자 |
| `installed_on` | `timestamp without time zone` | 아니오 | `now()` | 설치 시각 |
| `execution_time` | `integer` | 아니오 | 없음 | 실행 시간(밀리초) |
| `success` | `boolean` | 아니오 | 없음 | 적용 성공 여부 |

DB 제약조건:

- `PRIMARY KEY (installed_rank)`

<a id="public-idempotency-keys"></a>

## public.idempotency_keys

쓰기 API의 중복 처리를 막는 요청 키와 응답 캐시. 같은 키라도 사용자별로 분리한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `key` | `text` | 아니오 | 없음 | 클라이언트가 제공한 멱등성 키 |
| `user_id` | `uuid` | 아니오 | 없음 | 요청 사용자 UUID |
| `request_hash` | `text` | 아니오 | 없음 | HTTP 메서드·경로·본문을 기반으로 한 요청 해시 |
| `status` | `text` | 아니오 | 없음 | processing=처리 중, done=완료 |
| `response_status` | `integer` | 예 | 없음 | 저장된 HTTP 응답 상태 코드 |
| `response_body` | `jsonb` | 예 | 없음 | 재요청에 돌려줄 JSON 응답 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 키 최초 등록 시각 |

DB 제약조건:

- `PRIMARY KEY (key, user_id)`
- `CHECK ((status = ANY (ARRAY['processing'::text, 'done'::text])))`

<a id="public-inquiries"></a>

## public.inquiries

사용자 고객센터 문의와 운영자 답변, 탈퇴 후 보관 만료 시각.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 문의 UUID |
| `user_id` | `uuid` | 아니오 | 없음 | 문의 작성자의 profiles.id |
| `content` | `text` | 아니오 | 없음 | 문의 본문 |
| `status` | `text` | 아니오 | `'open'::text` | open=접수, answered=답변 완료 |
| `answer` | `text` | 예 | 없음 | 운영자 답변 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 문의 접수 시각 |
| `answered_at` | `timestamp with time zone` | 예 | 없음 | 답변 시각 |
| `purge_after` | `timestamp with time zone` | 예 | 없음 | 문의 파기 예정 시각. 탈퇴 후 보관 기한 관리 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `CHECK ((status = ANY (ARRAY['open'::text, 'answered'::text])))`
- `FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE`

<a id="public-job-checklist-events"></a>

## public.job_checklist_events

체크리스트 체크·해제의 원본 이력. 항목 삭제 후에도 보존하며 녹화 구간 라벨 생성 근거로 사용한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 이벤트 UUID |
| `event_order` | `bigint` | 아니오 | 없음 | DB가 생성한 저장 순번. 같은 시각 이벤트의 선후관계 판정 |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약 ID |
| `item_id` | `uuid` | 아니오 | 없음 | 원본 체크리스트 항목 UUID 스냅샷 |
| `group_name` | `text` | 아니오 | 없음 | 당시 그룹·공간 이름 |
| `label` | `text` | 아니오 | 없음 | 당시 항목 문구 |
| `done` | `boolean` | 아니오 | 없음 | 변경 후 완료 여부(true=체크, false=해제) |
| `changed_at` | `timestamp with time zone` | 아니오 | 없음 | 서버가 상태 변경을 기록한 시각 |
| `manager_id` | `uuid` | 아니오 | 없음 | 변경한 매니저 UUID 스냅샷 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE`
- `UNIQUE (event_order)`
- `PRIMARY KEY (id)`

<a id="public-job-checklist-items"></a>

## public.job_checklist_items

예약별 실제 작업 체크리스트의 현재 상태.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 항목 UUID |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약 ID |
| `group_name` | `text` | 아니오 | 없음 | 공간·작업 그룹 이름 |
| `label` | `text` | 아니오 | 없음 | 작업 항목 문구 |
| `done` | `boolean` | 아니오 | `false` | 완료 체크 여부 |
| `done_at` | `timestamp with time zone` | 예 | 없음 | 완료 체크 시각 |
| `sort` | `integer` | 아니오 | 없음 | 표시 순서 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`

<a id="public-job-pauses"></a>

## public.job_pauses

작업 일시정지와 재개 구간 이력.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 중지 구간 UUID |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약 ID |
| `reason` | `text` | 아니오 | 없음 | 중지 사유 |
| `paused_at` | `timestamp with time zone` | 아니오 | `now()` | 중지 시작 시각 |
| `resumed_at` | `timestamp with time zone` | 예 | 없음 | 재개 시각. 아직 중지 중이면 null |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`

<a id="public-live-sessions"></a>

## public.live_sessions

예약별 LiveKit 라이브·녹화 세션. 바디캠 연결·시청 차단·녹화 파일과 시간 기준을 관리한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `booking_id` | `uuid` | 아니오 | 없음 | 예약 ID이자 세션 기본 키 |
| `room_name` | `text` | 아니오 | 없음 | LiveKit 방 이름. 예약별 유일 |
| `status` | `text` | 아니오 | 없음 | created=생성, waiting_camera=카메라 대기, live=송출 중, paused=중지, ended=종료 |
| `ingress_id` | `text` | 예 | 없음 | 연결한 바디캠 LiveKit Ingress ID |
| `camera_online_at` | `timestamp with time zone` | 예 | 없음 | 카메라가 온라인으로 연결된 시각 |
| `camera_track_sid` | `text` | 예 | 없음 | 카메라 영상 트랙 SID. 음소거 제어 대상 |
| `mute_pending` | `boolean` | 아니오 | `false` | 카메라 연결 후 음소거가 필요한지 여부 |
| `started_at` | `timestamp with time zone` | 예 | 없음 | 라이브 시작 요청·처리 시각. 실제 파일 시작 기준과 구분 |
| `ended_at` | `timestamp with time zone` | 예 | 없음 | 라이브 세션 실제 종료 시각 |
| `egress_id` | `text` | 예 | 없음 | LiveKit 녹화 출력 작업(Egress) ID |
| `recording_status` | `text` | 아니오 | `'none'::text` | none=없음, recording=녹화 중, uploading=업로드 중, ready=준비 완료, failed=실패 |
| `recording_path` | `text` | 예 | 없음 | recordings 버킷 내 녹화 파일 경로 |
| `duration_seconds` | `integer` | 예 | 없음 | 최종 녹화 길이(초) |
| `viewer_count` | `integer` | 아니오 | `0` | 집계된 시청자 수 |
| `unmute_pending` | `boolean` | 아니오 | `false` | 음소거 해제를 재시도해야 하는지 여부 |
| `camera_wait_notified_at` | `timestamp with time zone` | 예 | 없음 | 카메라 연결 지연 안내 발송 시각 |
| `camera_offline_at` | `timestamp with time zone` | 예 | 없음 | 작업 중 카메라 연결이 끊긴 시각 |
| `paused_at` | `timestamp with time zone` | 예 | 없음 | 영상 일시정지가 확정된 시각 |
| `viewers_blocked` | `boolean` | 아니오 | `false` | 고객 참가자의 영상 구독을 차단했는지 여부 |
| `job_paused_at` | `timestamp with time zone` | 예 | 없음 | 작업 일시정지가 실제 반영된 시각 |
| `video_held` | `boolean` | 아니오 | `false` | 매니저가 명시적으로 재개할 때까지 영상 차단을 유지하는 표시 |
| `recording_started_at` | `timestamp with time zone` | 예 | 없음 | 녹화 파일 시작 기준 시각. 확인 여부는 recording_start_confirmed로 판정 |
| `recording_start_confirmed` | `boolean` | 아니오 | `false` | 녹화 시작 시각이 실제 파일 기준으로 확인됐는지 여부 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE`
- `PRIMARY KEY (booking_id)`
- `CHECK ((recording_status = ANY (ARRAY['none'::text, 'recording'::text, 'uploading'::text, 'ready'::text, 'failed'::text])))`
- `UNIQUE (room_name)`
- `CHECK ((status = ANY (ARRAY['created'::text, 'waiting_camera'::text, 'live'::text, 'paused'::text, 'ended'::text])))`

<a id="public-livekit-events"></a>

## public.livekit_events

LiveKit 웹훅의 중복 처리를 막는 수신 이벤트 원장.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `text` | 아니오 | 없음 | LiveKit 이벤트 ID |
| `event` | `text` | 아니오 | 없음 | LiveKit 이벤트 종류 |
| `received_at` | `timestamp with time zone` | 아니오 | `now()` | 서버 수신 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`

<a id="public-manager-devices"></a>

## public.manager_devices

매니저의 바디캠과 재사용 LiveKit Ingress 연결 정보. 매니저별 1대 관리.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `manager_id` | `uuid` | 아니오 | 없음 | 매니저의 profiles.id |
| `name` | `text` | 아니오 | 없음 | 운영자가 등록한 장비 표시명 |
| `serial` | `text` | 예 | 없음 | 장비 일련번호 |
| `ingress_id` | `text` | 예 | 없음 | 바디캠 LiveKit Ingress ID |
| `stream_url` | `text` | 예 | 없음 | 장비가 송출할 RTMP 등 스트림 주소 |
| `stream_key` | `text` | 예 | 없음 | 송출 인증 스트림 키. 민감정보 |
| `stream_status` | `text` | 아니오 | `'offline'::text` | offline=연결 끊김, online=연결됨 |
| `last_online_at` | `timestamp with time zone` | 예 | 없음 | 마지막 온라인 확인 시각 |
| `pending_room` | `text` | 예 | 없음 | 장비 연결 종료 후 적용할 대기 방 이름 |
| `last_report` | `jsonb` | 예 | 없음 | 배터리·네트워크 등 장비 상태 JSON |
| `reported_at` | `timestamp with time zone` | 예 | 없음 | 장비 상태 보고 시각 |
| `ingress_enabled` | `boolean` | 아니오 | `true` | Ingress가 새 연결을 허용하는지 여부 |

DB 제약조건:

- `UNIQUE (ingress_id)`
- `FOREIGN KEY (manager_id) REFERENCES profiles(id) ON DELETE CASCADE`
- `PRIMARY KEY (manager_id)`
- `UNIQUE (serial)`
- `CHECK ((stream_status = ANY (ARRAY['offline'::text, 'online'::text])))`

<a id="public-manager-hours"></a>

## public.manager_hours

매니저의 요일별 기본 근무 가능 시간.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `manager_id` | `uuid` | 아니오 | 없음 | 매니저 UUID |
| `weekday` | `smallint` | 아니오 | 없음 | 요일(0=월, 1=화, 2=수, 3=목, 4=금, 5=토, 6=일) |
| `start_time` | `time without time zone` | 예 | 없음 | 근무 시작 시각(서울 기준) |
| `end_time` | `time without time zone` | 예 | 없음 | 근무 종료 시각(서울 기준) |
| `off` | `boolean` | 아니오 | `false` | 해당 요일 휴무 여부 |

DB 제약조건:

- `FOREIGN KEY (manager_id) REFERENCES profiles(id) ON DELETE CASCADE`
- `PRIMARY KEY (manager_id, weekday)`
- `CHECK (((weekday >= 0) AND (weekday <= 6)))`

<a id="public-manager-penalties"></a>

## public.manager_penalties

매니저 취소·미방문·지각과 회복 점수 원장. 현재 점수는 유효한 행의 합으로 계산한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 패널티 이력 UUID |
| `manager_id` | `uuid` | 아니오 | 없음 | 대상 매니저 UUID |
| `booking_id` | `uuid` | 예 | 없음 | 대상 예약 ID. 회복 이력은 null 가능 |
| `series_id` | `uuid` | 예 | 없음 | 정기예약 묶음 ID. 우선 배정 제외 판단에 사용 |
| `category` | `text` | 아니오 | 없음 | cancel_early=사전 취소, cancel_late=임박 취소, no_show=미방문, tardy=지각, recovery=회복 |
| `points` | `numeric(3,1)` | 아니오 | 없음 | 부과 점수. 회복은 음수 |
| `reason` | `text` | 예 | 없음 | 선택한 사유 코드 |
| `detail` | `text` | 예 | 없음 | 직접 입력한 상세 사유 |
| `status` | `text` | 아니오 | `'active'::text` | active=유효, expired=만료, waived=면제 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 이력 생성 시각 |
| `expires_at` | `timestamp with time zone` | 아니오 | 없음 | 점수 유효기간 만료 시각 |
| `waived_at` | `timestamp with time zone` | 예 | 없음 | 운영자가 면제한 시각 |
| `waived_note` | `text` | 예 | 없음 | 면제 사유·운영 메모 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id)`
- `CHECK ((category = ANY (ARRAY['cancel_early'::text, 'cancel_late'::text, 'no_show'::text, 'tardy'::text, 'recovery'::text])))`
- `FOREIGN KEY (manager_id) REFERENCES profiles(id)`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (series_id) REFERENCES booking_series(id)`
- `CHECK ((status = ANY (ARRAY['active'::text, 'expired'::text, 'waived'::text])))`

<a id="public-manager-profiles"></a>

## public.manager_profiles

매니저의 승인·평가·활동 지역·교육·정산 계좌 표시 정보.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `manager_id` | `uuid` | 아니오 | 없음 | 매니저의 profiles.id |
| `approved` | `boolean` | 아니오 | `false` | 신원 확인·교육 후 운영 승인 여부 |
| `rating` | `numeric(2,1)` | 아니오 | `0` | 매니저 평점 |
| `completed_count` | `integer` | 아니오 | `0` | 완료 작업 수 |
| `active_since` | `date` | 예 | 없음 | 활동 시작일 |
| `areas` | `text[]` | 아니오 | `'{}'::text[]` | 시·도와 시·군·구를 포함한 활동 지역 문자열 배열 |
| `sido` | `text` | 예 | 없음 | 예약의 구 정보가 없을 때 지역 매칭에 쓸 시·도 |
| `identity_verified_at` | `timestamp with time zone` | 예 | 없음 | 신원 확인 완료 시각 |
| `background_check_at` | `timestamp with time zone` | 예 | 없음 | 신원·배경 조회 완료 시각 |
| `insurance_expires_on` | `date` | 예 | 없음 | 보험 만료일 |
| `education` | `jsonb` | 아니오 | `'[]'::jsonb` | 교육 이수 내역 JSON 배열 |
| `bank_name` | `text` | 예 | 없음 | 정산 은행명 |
| `account_number_masked` | `text` | 예 | 없음 | 마스킹된 계좌번호. 원문 계좌번호가 아님 |
| `account_holder` | `text` | 예 | 없음 | 예금주명 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 매니저 정보 수정 시각 |
| `suspended_until` | `date` | 예 | 없음 | 활동 정지 종료 기준일 |

DB 제약조건:

- `FOREIGN KEY (manager_id) REFERENCES profiles(id) ON DELETE CASCADE`
- `PRIMARY KEY (manager_id)`

<a id="public-manager-unavailable"></a>

## public.manager_unavailable

매니저가 추가 등록한 근무 불가 날짜·시간과 반복 규칙.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 근무 불가 일정 UUID |
| `manager_id` | `uuid` | 아니오 | 없음 | 대상 매니저 UUID |
| `date` | `date` | 아니오 | 없음 | 불가 일정 기준 날짜 |
| `all_day` | `boolean` | 아니오 | `false` | 하루 전체 불가 여부 |
| `start_time` | `time without time zone` | 예 | 없음 | 불가 시작 시각 |
| `end_time` | `time without time zone` | 예 | 없음 | 불가 종료 시각 |
| `repeat` | `text` | 아니오 | `'none'::text` | none=반복 없음, weekly=매주, monthly=매월 |
| `reason` | `text` | 예 | 없음 | 근무 불가 사유 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 등록 시각 |

DB 제약조건:

- `FOREIGN KEY (manager_id) REFERENCES profiles(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`
- `CHECK ((repeat = ANY (ARRAY['none'::text, 'weekly'::text, 'monthly'::text])))`

<a id="public-notices"></a>

## public.notices

고객·매니저 대상 공지사항.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 공지 UUID |
| `audience` | `text` | 아니오 | 없음 | 노출 대상(customer=고객, manager=매니저, all=전체) |
| `title` | `text` | 아니오 | 없음 | 공지 제목 |
| `body` | `text` | 아니오 | 없음 | 공지 본문 |
| `published_at` | `timestamp with time zone` | 아니오 | `now()` | 게시 시각 |

DB 제약조건:

- `CHECK ((audience = ANY (ARRAY['customer'::text, 'manager'::text, 'all'::text])))`
- `PRIMARY KEY (id)`

<a id="public-notification-settings"></a>

## public.notification_settings

사용자별 푸시·마케팅·콜·정산 등 알림 수신 설정.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `user_id` | `uuid` | 아니오 | 없음 | 사용자의 profiles.id |
| `key` | `text` | 아니오 | 없음 | 알림 종류 키 |
| `enabled` | `boolean` | 아니오 | `true` | 수신 허용 여부 |

DB 제약조건:

- `PRIMARY KEY (user_id, key)`
- `FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE`

<a id="public-offers"></a>

## public.offers

예약을 수행할 매니저에게 발송한 콜·오퍼. 예약·매니저·발송 회차 조합별로 관리한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 오퍼 UUID |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약 ID |
| `manager_id` | `uuid` | 아니오 | 없음 | 수신 매니저 UUID |
| `round` | `integer` | 아니오 | `1` | 예약의 dispatch_round에 대응하는 발송 회차 |
| `status` | `text` | 아니오 | 없음 | pending=대기, accepted=수락, rejected=거절, expired=만료, cancelled=취소, superseded=일정 변경 등으로 기존 수락 무효 |
| `pay_amount` | `bigint` | 아니오 | 없음 | 매니저 수령 예정 금액(원). 예약 총액에서 플랫폼 수수료 차감 |
| `expires_at` | `timestamp with time zone` | 아니오 | 없음 | 수락 가능 기한 |
| `responded_at` | `timestamp with time zone` | 예 | 없음 | 매니저 응답 시각 |
| `reject_reason` | `text` | 예 | 없음 | 거절 사유 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 오퍼 생성 시각 |
| `preferred` | `boolean` | 아니오 | `false` | 이전 담당자 등에게 먼저 보내는 우선 오퍼 여부 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE`
- `UNIQUE (booking_id, manager_id, round)`
- `FOREIGN KEY (manager_id) REFERENCES profiles(id)`
- `PRIMARY KEY (id)`
- `CHECK ((status = ANY (ARRAY['pending'::text, 'accepted'::text, 'rejected'::text, 'expired'::text, 'cancelled'::text, 'superseded'::text])))`

<a id="public-outbox-events"></a>

## public.outbox_events

업무 트랜잭션과 함께 저장한 외부 알림·후속 처리 이벤트. 별도 처리기가 발송·재시도한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 이벤트 UUID |
| `type` | `text` | 아니오 | 없음 | 업무 이벤트 종류 |
| `payload` | `jsonb` | 아니오 | 없음 | 처리에 필요한 JSON 데이터 |
| `status` | `text` | 아니오 | `'pending'::text` | pending=대기, sent=처리 완료, failed=실패 |
| `attempts` | `integer` | 아니오 | `0` | 처리 시도 횟수 |
| `next_attempt_at` | `timestamp with time zone` | 아니오 | `now()` | 다음 재시도 예정 시각 |
| `last_error` | `text` | 예 | 없음 | 마지막 처리 오류 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 이벤트 생성 시각 |
| `sent_at` | `timestamp with time zone` | 예 | 없음 | 외부 처리 완료 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `CHECK ((status = ANY (ARRAY['pending'::text, 'sent'::text, 'failed'::text])))`

<a id="public-payment-methods"></a>

## public.payment_methods

고객의 PG 등록 결제수단. 카드 원문 대신 PG 빌링키를 보관한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 결제수단 UUID |
| `customer_id` | `uuid` | 아니오 | 없음 | 소유 고객 UUID |
| `provider` | `text` | 아니오 | 없음 | 결제대행사 식별자 |
| `billing_key` | `text` | 아니오 | 없음 | PG가 발급한 재결제용 빌링키. 민감정보 |
| `label` | `text` | 아니오 | 없음 | 사용자에게 표시할 결제수단명 |
| `is_default` | `boolean` | 아니오 | `false` | 기본 결제수단 여부 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 등록 시각 |

DB 제약조건:

- `FOREIGN KEY (customer_id) REFERENCES profiles(id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`

<a id="public-payment-refunds"></a>

## public.payment_refunds

결제별 환불 처리 원장. 결제당 한 행으로 환불 요청과 완료를 관리한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 환불 UUID |
| `payment_id` | `uuid` | 아니오 | 없음 | 대상 payments.id |
| `amount` | `bigint` | 아니오 | 없음 | 환불 요청 금액(원), 0보다 큼 |
| `status` | `text` | 아니오 | 없음 | pending=처리 중, done=완료 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 환불 요청 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 환불 처리 갱신 시각 |

DB 제약조건:

- `CHECK ((amount > 0))`
- `FOREIGN KEY (payment_id) REFERENCES payments(id)`
- `UNIQUE (payment_id)`
- `PRIMARY KEY (id)`
- `CHECK ((status = ANY (ARRAY['pending'::text, 'done'::text])))`

<a id="public-payments"></a>

## public.payments

예약별 PG 결제 거래·환불 누계·재확인 상태. 실제 결제수단과 거래 ID를 연결한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 결제 UUID |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약 ID |
| `payment_method_id` | `uuid` | 예 | 없음 | 등록 결제수단 ID. 일회성 결제는 null 가능 |
| `provider` | `text` | 아니오 | 없음 | 결제대행사 식별자 |
| `provider_tx_id` | `text` | 예 | 없음 | PG 거래 ID. 유일값 |
| `amount` | `bigint` | 아니오 | 없음 | 결제 금액(원) |
| `status` | `text` | 아니오 | 없음 | pending=대기, authorized=승인, paid=결제 완료, failed=실패, partially_refunded=부분 환불, refunded=전액 환불 |
| `failure_reason` | `text` | 예 | 없음 | 결제 실패 사유 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 결제 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 결제 갱신 시각 |
| `refunded_amount` | `bigint` | 아니오 | `0` | 누적 환불 금액(원). 결제액 이하 |
| `next_check_at` | `timestamp with time zone` | 예 | 없음 | PG 거래 상태를 다음에 재확인할 시각 |
| `last_webhook_at` | `timestamp with time zone` | 예 | 없음 | 마지막 결제 웹훅 수신 시각 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id)`
- `FOREIGN KEY (payment_method_id) REFERENCES payment_methods(id)`
- `PRIMARY KEY (id)`
- `UNIQUE (provider_tx_id)`
- `CHECK (((amount >= 0) AND (refunded_amount >= 0) AND (refunded_amount <= amount)))`
- `CHECK ((status = ANY (ARRAY['pending'::text, 'authorized'::text, 'paid'::text, 'failed'::text, 'partially_refunded'::text, 'refunded'::text])))`

<a id="public-phone-verifications"></a>

## public.phone_verifications

가입·인증을 위한 휴대폰 OTP 검증 상태. 인증번호 원문 대신 해시를 저장한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 인증 요청 UUID |
| `phone` | `text` | 아니오 | 없음 | E.164 형식 휴대폰 번호 |
| `role` | `text` | 아니오 | 없음 | 인증 역할(customer=고객, manager=매니저) |
| `code_hash` | `text` | 아니오 | 없음 | 인증번호와 서버 salt로 계산한 해시 |
| `expires_at` | `timestamp with time zone` | 아니오 | 없음 | 인증번호 만료 시각 |
| `attempts` | `integer` | 아니오 | `0` | 인증번호 검증 시도 횟수 |
| `verified_at` | `timestamp with time zone` | 예 | 없음 | 인증 성공 시각 |
| `token` | `text` | 예 | 없음 | 검증 성공 후 가입 완료 등에 쓰는 불투명 토큰 |
| `consumed_at` | `timestamp with time zone` | 예 | 없음 | 토큰 사용 완료 시각. 재사용 방지 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 인증 요청 생성 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `CHECK ((role = ANY (ARRAY['customer'::text, 'manager'::text])))`
- `UNIQUE (token)`

<a id="public-profiles"></a>

## public.profiles

앱 사용자 기본 프로필. ID는 auth.users.id와 대응하지만 탈퇴 후 이력 보존을 위해 Auth 외래 키를 두지 않는다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 사용자 UUID. auth.users.id와 대응 |
| `role` | `text` | 아니오 | 없음 | customer=고객, manager=매니저 |
| `name` | `text` | 아니오 | 없음 | 이름 |
| `nickname` | `text` | 예 | 없음 | 닉네임 |
| `phone` | `text` | 아니오 | 없음 | E.164 형식 휴대폰 번호. 살아 있는 계정 간 중복 제한 |
| `avatar_path` | `text` | 예 | 없음 | 프로필 이미지 Storage 경로 |
| `signup_completed_at` | `timestamp with time zone` | 예 | 없음 | 가입 절차 완료 시각. null이면 가입 진행 중 |
| `deleted_at` | `timestamp with time zone` | 예 | 없음 | 탈퇴 처리 시각 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 프로필 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 프로필 수정 시각 |
| `purged_at` | `timestamp with time zone` | 예 | 없음 | 탈퇴 후 보관 기간이 끝나 잔여 개인정보를 파기한 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `CHECK ((role = ANY (ARRAY['customer'::text, 'manager'::text])))`

<a id="public-push-tokens"></a>

## public.push_tokens

사용자·플랫폼별 푸시 발송 토큰.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `user_id` | `uuid` | 아니오 | 없음 | 사용자 UUID |
| `platform` | `text` | 아니오 | 없음 | 클라이언트 플랫폼(ios/android/web) |
| `token` | `text` | 아니오 | 없음 | 푸시 제공자가 발급한 기기 토큰 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 토큰 등록·갱신 시각 |

DB 제약조건:

- `PRIMARY KEY (user_id, token)`
- `CHECK ((platform = ANY (ARRAY['ios'::text, 'android'::text, 'web'::text])))`
- `FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE`

<a id="public-recording-segments"></a>

## public.recording_segments

단일 녹화 영상의 공간 구간 라벨. 체크 완료 경계로 공간을 추정하며 실제 방 이동 감지가 아니다. 학습 전 검토가 필요하고 구간은 [시작 포함, 종료 제외)이다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | 없음 | 영상 구간 라벨 UUID |
| `live_session_id` | `uuid` | 아니오 | 없음 | 녹화 세션 ID=예약 ID. 파일 경로·길이는 live_sessions에서 조회 |
| `checklist_event_id` | `uuid` | 아니오 | 없음 | 구간 생성 근거인 체크 완료 이벤트 ID. 중복 생성 방지 |
| `item_id` | `uuid` | 아니오 | 없음 | 체크리스트 항목 UUID 스냅샷. 원래 항목 삭제 후에도 보존 |
| `room_code` | `text` | 아니오 | 없음 | 표준 공간 분류(living_room/kitchen/bedroom/bathroom/other) |
| `room_name` | `text` | 아니오 | 없음 | 완료 당시 공간명 스냅샷 |
| `start_at` | `timestamp with time zone` | 아니오 | 없음 | 원본 시작 경계. 이전 체크 완료 시각, 첫 구간은 라이브 시작 요청 시각 |
| `end_at` | `timestamp with time zone` | 아니오 | 없음 | 서버가 현재 체크 완료를 수신한 원본 종료 시각 |
| `start_ms` | `bigint` | 예 | 없음 | 실제 영상 시작 기준 시작 밀리초(포함). 시간 미확인·유효 구간 없음이면 null |
| `end_ms` | `bigint` | 예 | 없음 | 실제 영상 시작 기준 종료 밀리초(제외). 최종 영상 길이로 상한 보정 |
| `label_source` | `text` | 아니오 | `'checklist'::text` | checklist=체크 기반 추정, manual=수동, ai=모델 |
| `review_status` | `text` | 아니오 | `'pending'::text` | pending=검토 대기, approved=승인, revoked=체크 해제로 무효. 무효 라벨은 학습 제외 |
| `timing_status` | `text` | 아니오 | `'pending'::text` | pending=실제 녹화 시작 미확인, ready=시간 계산됨, empty=영상 밖·길이 0 |
| `created_by` | `uuid` | 아니오 | 없음 | 완료 체크한 매니저 UUID 스냅샷 |
| `created_at` | `timestamp with time zone` | 아니오 | 없음 | 구간 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | 없음 | 시간 보정·상태 변경 시각 |

DB 제약조건:

- `CHECK ((end_at >= start_at))`
- `CHECK ((((start_ms IS NULL) AND (end_ms IS NULL)) OR ((start_ms >= 0) AND (end_ms > start_ms))))`
- `CHECK (((timing_status = 'ready'::text) = ((start_ms IS NOT NULL) AND (end_ms IS NOT NULL))))`
- `FOREIGN KEY (checklist_event_id) REFERENCES job_checklist_events(id) ON DELETE CASCADE`
- `UNIQUE (checklist_event_id)`
- `CHECK ((label_source = ANY (ARRAY['checklist'::text, 'manual'::text, 'ai'::text])))`
- `FOREIGN KEY (live_session_id) REFERENCES live_sessions(booking_id) ON DELETE CASCADE`
- `PRIMARY KEY (id)`
- `CHECK ((review_status = ANY (ARRAY['pending'::text, 'approved'::text, 'revoked'::text])))`
- `CHECK ((timing_status = ANY (ARRAY['pending'::text, 'ready'::text, 'empty'::text])))`

<a id="public-repair-photo-uploads"></a>

## public.repair_photo_uploads

예약 전 업로드된 수리 사진의 원장. 미첨부 파일 정리와 삭제 중 재첨부 방지에 사용한다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `path` | `text` | 아니오 | 없음 | repair-photos 버킷 내 파일 경로(기본 키) |
| `customer_id` | `uuid` | 아니오 | 없음 | 업로드 고객 UUID |
| `created_at` | `timestamp with time zone` | 아니오 | 없음 | 업로드 등록 시각 |
| `attached_at` | `timestamp with time zone` | 예 | 없음 | 예약에 사진을 첨부한 시각. 미첨부이면 null |
| `deleting_at` | `timestamp with time zone` | 예 | 없음 | Storage 삭제 절차 시작 시각. 이 상태에서는 예약 첨부 금지 |

DB 제약조건:

- `PRIMARY KEY (path)`

<a id="public-revoked-sessions"></a>

## public.revoked_sessions

로그아웃·비밀번호 재설정 등으로 서버가 차단한 Supabase 세션 목록.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `session_id` | `text` | 아니오 | 없음 | 차단할 Supabase JWT session_id |
| `user_id` | `uuid` | 아니오 | 없음 | 세션 사용자 UUID |
| `revoked_at` | `timestamp with time zone` | 아니오 | 없음 | 차단 처리 시각 |
| `expires_at` | `timestamp with time zone` | 아니오 | 없음 | 차단 기록 보관 만료 시각 |

DB 제약조건:

- `PRIMARY KEY (session_id)`

<a id="public-service-categories"></a>

## public.service_categories

앱에서 제공하는 청소·수리 서비스 분류.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `key` | `text` | 아니오 | 없음 | 서비스 분류 키(예: home, office, appliance, move-in, repair) |
| `label` | `text` | 아니오 | 없음 | 서비스 표시명 |
| `promo` | `text` | 아니오 | 없음 | 프로모션 유형(recurring=정기 안내, placeholder=기타 안내) |
| `promo_text` | `text` | 예 | 없음 | 프로모션 표시 문구 |
| `sort` | `integer` | 아니오 | 없음 | 서비스 표시 순서 |

DB 제약조건:

- `PRIMARY KEY (key)`
- `CHECK ((promo = ANY (ARRAY['recurring'::text, 'placeholder'::text])))`

<a id="public-settlements"></a>

## public.settlements

완료 작업의 매니저 정산 금액과 지급 상태. 예약당 한 행.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 정산 UUID |
| `booking_id` | `uuid` | 아니오 | 없음 | 대상 예약 ID. 예약당 유일 |
| `manager_id` | `uuid` | 아니오 | 없음 | 수령 매니저 UUID |
| `service_amount` | `bigint` | 아니오 | 없음 | 서비스 정산 기준 금액(원) |
| `platform_fee` | `bigint` | 아니오 | 없음 | 플랫폼 수수료(원) |
| `other_fee` | `bigint` | 아니오 | `0` | 기타 공제액(원) |
| `final_amount` | `bigint` | 아니오 | 없음 | 매니저 최종 정산액(원) |
| `status` | `text` | 아니오 | 없음 | pending=지급 대기, complete=정산 완료 |
| `settled_at` | `timestamp with time zone` | 예 | 없음 | 정산 완료 시각 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 정산 생성 시각 |

DB 제약조건:

- `FOREIGN KEY (booking_id) REFERENCES bookings(id)`
- `UNIQUE (booking_id)`
- `FOREIGN KEY (manager_id) REFERENCES profiles(id)`
- `PRIMARY KEY (id)`
- `CHECK ((status = ANY (ARRAY['pending'::text, 'complete'::text])))`

<a id="public-shedlock"></a>

## public.shedlock

여러 서버 인스턴스에서 같은 스케줄 작업이 동시에 실행되지 않도록 관리하는 ShedLock 잠금.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `name` | `character varying(64)` | 아니오 | 없음 | 스케줄 작업의 잠금 이름 |
| `lock_until` | `timestamp(3) without time zone` | 아니오 | 없음 | 잠금 유효 종료 시각 |
| `locked_at` | `timestamp(3) without time zone` | 아니오 | 없음 | 잠금 획득 시각 |
| `locked_by` | `character varying(255)` | 아니오 | 없음 | 잠금을 획득한 서버 식별자 |

DB 제약조건:

- `PRIMARY KEY (name)`

<a id="public-term-agreements"></a>

## public.term_agreements

사용자별 약관 동의 버전과 시각. 사용자·약관 조합별 한 행.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `user_id` | `uuid` | 아니오 | 없음 | 동의한 사용자 UUID |
| `term_id` | `text` | 아니오 | 없음 | 약관 식별자. 사용자 역할에 맞는 terms.audience와 함께 해석 |
| `version` | `integer` | 아니오 | 없음 | 동의 당시 약관 버전 |
| `agreed_at` | `timestamp with time zone` | 아니오 | `now()` | 동의 시각 |

DB 제약조건:

- `PRIMARY KEY (user_id, term_id)`
- `FOREIGN KEY (user_id) REFERENCES profiles(id) ON DELETE CASCADE`

<a id="public-terms"></a>

## public.terms

고객·매니저별 약관 본문과 필수 동의 여부.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `text` | 아니오 | 없음 | 약관 식별자 |
| `audience` | `text` | 아니오 | 없음 | 적용 완료(customer=고객, manager=매니저) |
| `label` | `text` | 아니오 | 없음 | 동의 화면의 표시 문구 |
| `title` | `text` | 아니오 | 없음 | 약관 제목 |
| `body` | `text` | 아니오 | 없음 | 약관 본문 |
| `required` | `boolean` | 아니오 | 없음 | 필수 동의 여부 |
| `version` | `integer` | 아니오 | `1` | 약관 버전 |
| `sort` | `integer` | 아니오 | 없음 | 화면 표시 순서 |

DB 제약조건:

- `CHECK ((audience = ANY (ARRAY['customer'::text, 'manager'::text])))`
- `PRIMARY KEY (id, audience)`

<a id="realtime-messages"></a>

## realtime.messages

Realtime Broadcast·Presence의 접근 정책 검사와 메시지 처리에 사용하는 내부 테이블.

소유자: `supabase_realtime_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `topic` | `text` | 아니오 | 없음 | 채널 토픽 |
| `extension` | `text` | 아니오 | 없음 | Realtime 기능·확장 구분 |
| `payload` | `jsonb` | 예 | 없음 | 메시지 JSON 데이터 |
| `event` | `text` | 예 | 없음 | 메시지 이벤트 이름 |
| `private` | `boolean` | 예 | `false` | 비공개 채널 메시지 여부 |
| `updated_at` | `timestamp without time zone` | 아니오 | `timezone('utc'::text, clock_timestamp())` | 수정 시각 |
| `inserted_at` | `timestamp without time zone` | 아니오 | `timezone('utc'::text, clock_timestamp())` | 삽입 시각 |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 메시지 UUID |
| `binary_payload` | `bytea` | 예 | 없음 | 바이너리 메시지 본문 |
| `skip_broadcast` | `boolean` | 아니오 | `false` | 해당 메시지의 브로드캐스트를 건너뛸지 여부 |

DB 제약조건:

- `CHECK (((payload IS NULL) OR (binary_payload IS NULL))) NOT VALID`
- `PRIMARY KEY (id, inserted_at)`

<a id="realtime-schema-migrations"></a>

## realtime.schema_migrations

Supabase Realtime 내부 스키마 적용 버전.

소유자: `supabase_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `version` | `bigint` | 아니오 | 없음 | 마이그레이션 버전 |
| `inserted_at` | `timestamp(0) without time zone` | 예 | `now()` | 적용 시각 |

DB 제약조건:

- `PRIMARY KEY (version)`

<a id="realtime-subscription"></a>

## realtime.subscription

Postgres 변경 구독의 대상 테이블·필터·JWT 권한 정보.

소유자: `supabase_realtime_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `bigint` | 아니오 | 없음 | 구독 행 순번 |
| `subscription_id` | `uuid` | 아니오 | 없음 | 클라이언트 구독 UUID |
| `entity` | `regclass` | 아니오 | 없음 | 구독할 관계(테이블) 식별자 |
| `filters` | `realtime.user_defined_filter[]` | 아니오 | `'{}'::realtime.user_defined_filter[]` | 행 필터 배열 |
| `claims` | `jsonb` | 아니오 | 없음 | 구독자의 JWT 클레임 JSON |
| `claims_role` | `regrole` | 아니오 | `realtime.to_regrole((claims ->> 'role'::text))` | JWT 클레임에서 도출한 DB 역할 |
| `created_at` | `timestamp without time zone` | 아니오 | `timezone('utc'::text, now())` | 구독 등록 시각 |
| `action_filter` | `text` | 예 | `'*'::text` | 구독할 변경 작업 필터 |
| `selected_columns` | `text[]` | 예 | 없음 | 구독할 컬럼 목록 |

DB 제약조건:

- `PRIMARY KEY (id)`
- `CHECK ((action_filter = ANY (ARRAY['*'::text, 'INSERT'::text, 'UPDATE'::text, 'DELETE'::text])))`

<a id="storage-buckets"></a>

## storage.buckets

Supabase Storage 버킷 설정. 파일 본문이 아닌 메타데이터를 저장한다.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `text` | 아니오 | 없음 | 버킷 ID |
| `name` | `text` | 아니오 | 없음 | 버킷 이름 |
| `owner` | `uuid` | 예 | 없음 | 이전 소유자 UUID 필드. deprecated, owner_id 사용 |
| `created_at` | `timestamp with time zone` | 예 | `now()` | 버킷 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | `now()` | 버킷 수정 시각 |
| `public` | `boolean` | 예 | `false` | 파일 공개 읽기 허용 여부 |
| `avif_autodetection` | `boolean` | 예 | `false` | AVIF 파일 자동 판별 설정 |
| `file_size_limit` | `bigint` | 예 | 없음 | 업로드 파일 크기 상한(바이트) |
| `allowed_mime_types` | `text[]` | 예 | 없음 | 허용 MIME 유형 배열 |
| `owner_id` | `text` | 예 | 없음 | 버킷 소유자 식별자 |
| `type` | `storage.buckettype` | 아니오 | `'STANDARD'::storage.buckettype` | 버킷 종류(일반·분석·벡터) |
| `versioning_status` | `text` | 아니오 | `'DISABLED'::text` | 객체 버전 관리 상태 |
| `lifecycle_configuration` | `jsonb` | 예 | 없음 | 객체 수명주기 정책 JSON |
| `lifecycle_configuration_generation` | `uuid` | 예 | 없음 | 수명주기 설정 세대 식별자 |

DB 제약조건:

- `CHECK (((lifecycle_configuration IS NULL) = (lifecycle_configuration_generation IS NULL)))`
- `CHECK (((lifecycle_configuration IS NULL) OR ((jsonb_typeof(lifecycle_configuration) = 'object'::text) AND (lifecycle_configuration ? 'rules'::text) AND
CASE
    WHEN (jsonb_typeof((lifecycle_configuration -> 'rules'::text)) = 'array'::text) THEN ((jsonb_array_length((lifecycle_configuration -> 'rules'::text)) >= 1) AND (jsonb_array_length((lifecycle_configuration -> 'rules'::text)) <= 1000))
    ELSE false
END)))`
- `CHECK (((type = 'STANDARD'::storage.buckettype) OR ((lifecycle_configuration IS NULL) AND (lifecycle_configuration_generation IS NULL))))`
- `PRIMARY KEY (id)`
- `CHECK ((versioning_status = 'DISABLED'::text))`
- `CHECK (((type = 'STANDARD'::storage.buckettype) OR (versioning_status = 'DISABLED'::text)))`
- `CHECK ((versioning_status = ANY (ARRAY['DISABLED'::text, 'ENABLED'::text, 'SUSPENDED'::text])))`

<a id="storage-buckets-analytics"></a>

## storage.buckets_analytics

분석용 Storage 버킷과 테이블 포맷 정보.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `name` | `text` | 아니오 | 없음 | 분석 버킷 이름 |
| `type` | `storage.buckettype` | 아니오 | `'ANALYTICS'::storage.buckettype` | 분석 버킷 유형 |
| `format` | `text` | 아니오 | `'ICEBERG'::text` | 분석 데이터 테이블 포맷(예: ICEBERG) |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 수정 시각 |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 분석 버킷 UUID |
| `deleted_at` | `timestamp with time zone` | 예 | 없음 | 삭제 처리 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`

<a id="storage-buckets-vectors"></a>

## storage.buckets_vectors

벡터 저장용 Storage 버킷 정보.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `text` | 아니오 | 없음 | 벡터 버킷 ID |
| `type` | `storage.buckettype` | 아니오 | `'VECTOR'::storage.buckettype` | 버킷 유형 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 수정 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`

<a id="storage-migrations"></a>

## storage.migrations

Storage 서비스 자체 스키마 마이그레이션 이력.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `integer` | 아니오 | 없음 | 마이그레이션 순번 |
| `name` | `character varying(100)` | 아니오 | 없음 | 마이그레이션 이름 |
| `hash` | `character varying(40)` | 아니오 | 없음 | 마이그레이션 해시 |
| `executed_at` | `timestamp without time zone` | 예 | `CURRENT_TIMESTAMP` | 적용 시각 |

DB 제약조건:

- `UNIQUE (name)`
- `PRIMARY KEY (id)`

<a id="storage-objects"></a>

## storage.objects

Storage 파일 객체의 경로·메타데이터 원장. 실제 파일 본문은 오브젝트 스토리지에 있다.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 객체 UUID |
| `bucket_id` | `text` | 예 | 없음 | 소속 버킷 ID |
| `name` | `text` | 예 | 없음 | 버킷 안의 객체 이름·경로 |
| `owner` | `uuid` | 예 | 없음 | 이전 소유자 UUID 필드. deprecated, owner_id 사용 |
| `created_at` | `timestamp with time zone` | 예 | `now()` | 객체 생성 시각 |
| `updated_at` | `timestamp with time zone` | 예 | `now()` | 객체 수정 시각 |
| `last_accessed_at` | `timestamp with time zone` | 예 | `now()` | 마지막 접근 시각 필드 |
| `metadata` | `jsonb` | 예 | 없음 | 파일 크기·MIME 등 Storage 관리 메타데이터 JSON |
| `path_tokens` | `text[]` | 예 | `string_to_array(name, '/'::text)` | 객체 경로를 /로 분리한 계산 배열 |
| `version` | `text` | 예 | 없음 | 객체 버전 식별값 |
| `owner_id` | `text` | 예 | 없음 | 객체 소유자 식별자 |
| `user_metadata` | `jsonb` | 예 | 없음 | 업로더가 지정한 사용자 메타데이터 JSON |
| `archived_at` | `timestamp with time zone` | 예 | 없음 | 객체 아카이브 시각 |
| `is_delete_marker` | `boolean` | 아니오 | `false` | 버전 관리상 삭제 마커 여부 |
| `is_versioned` | `boolean` | 아니오 | `false` | 버전 관리 객체 여부 |

DB 제약조건:

- `FOREIGN KEY (bucket_id) REFERENCES storage.buckets(id)`
- `PRIMARY KEY (id)`

<a id="storage-s3-multipart-uploads"></a>

## storage.s3_multipart_uploads

S3 호환 멀티파트 업로드의 진행 상태. 완성 파일과 별개인 업로드 작업 메타데이터.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `text` | 아니오 | 없음 | 업로드 작업 ID |
| `in_progress_size` | `bigint` | 아니오 | `0` | 진행 중인 업로드 크기(바이트) |
| `upload_signature` | `text` | 아니오 | 없음 | 업로드 서명 관련 내부 값 |
| `bucket_id` | `text` | 아니오 | 없음 | 대상 버킷 ID |
| `key` | `text` | 아니오 | 없음 | 대상 객체 키·경로 |
| `version` | `text` | 아니오 | 없음 | 대상 객체 버전 |
| `owner_id` | `text` | 예 | 없음 | 업로드 소유자 식별자 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 업로드 시작 시각 |
| `user_metadata` | `jsonb` | 예 | 없음 | 사용자 지정 메타데이터 JSON |
| `metadata` | `jsonb` | 예 | 없음 | 파일·업로드 관리 메타데이터 JSON |

DB 제약조건:

- `FOREIGN KEY (bucket_id) REFERENCES storage.buckets(id)`
- `PRIMARY KEY (id)`

<a id="storage-s3-multipart-uploads-parts"></a>

## storage.s3_multipart_uploads_parts

진행 중인 S3 멀티파트 업로드의 개별 파트 정보.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 파트 UUID |
| `upload_id` | `text` | 아니오 | 없음 | 상위 멀티파트 업로드 ID |
| `size` | `bigint` | 아니오 | `0` | 파트 크기(바이트) |
| `part_number` | `integer` | 아니오 | 없음 | 업로드 내 파트 순번 |
| `bucket_id` | `text` | 아니오 | 없음 | 대상 버킷 ID |
| `key` | `text` | 아니오 | 없음 | 대상 객체 키·경로 |
| `etag` | `text` | 아니오 | 없음 | 파트 콘텐츠의 ETag |
| `owner_id` | `text` | 예 | 없음 | 소유자 식별자 |
| `version` | `text` | 아니오 | 없음 | 객체 버전 |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 파트 생성 시각 |

DB 제약조건:

- `FOREIGN KEY (bucket_id) REFERENCES storage.buckets(id)`
- `PRIMARY KEY (id)`
- `FOREIGN KEY (upload_id) REFERENCES storage.s3_multipart_uploads(id) ON DELETE CASCADE`

<a id="storage-vector-indexes"></a>

## storage.vector_indexes

벡터 버킷의 검색 인덱스 설정.

소유자: `supabase_storage_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `text` | 아니오 | `gen_random_uuid()` | 벡터 인덱스 ID |
| `name` | `text` | 아니오 | 없음 | 인덱스 이름 |
| `bucket_id` | `text` | 아니오 | 없음 | 소속 벡터 버킷 ID |
| `data_type` | `text` | 아니오 | 없음 | 벡터 요소 데이터 유형 |
| `dimension` | `integer` | 아니오 | 없음 | 벡터 차원 수 |
| `distance_metric` | `text` | 아니오 | 없음 | 유사도 거리 계산 방식 |
| `metadata_configuration` | `jsonb` | 예 | 없음 | 인덱스 메타데이터 설정 JSON |
| `created_at` | `timestamp with time zone` | 아니오 | `now()` | 인덱스 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `now()` | 인덱스 수정 시각 |

DB 제약조건:

- `FOREIGN KEY (bucket_id) REFERENCES storage.buckets_vectors(id)`
- `PRIMARY KEY (id)`

<a id="supabase_migrations-schema-migrations"></a>

## supabase_migrations.schema_migrations

Supabase 도구로 적용한 마이그레이션 이력. 앱 Flyway 이력과 별도로 관리된다.

소유자: `postgres`. 코멘트: 개발 DB 적용 완료.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `version` | `text` | 아니오 | 없음 | 마이그레이션 버전 |
| `statements` | `text[]` | 예 | 없음 | 실행한 SQL 문장 배열 |
| `name` | `text` | 예 | 없음 | 마이그레이션 이름 |
| `created_by` | `text` | 예 | 없음 | 생성·실행 주체 |
| `idempotency_key` | `text` | 예 | 없음 | 중복 적용 방지 키 |
| `rollback` | `text[]` | 예 | 없음 | 롤백 관련 SQL·메타데이터 |

DB 제약조건:

- `UNIQUE (idempotency_key)`
- `PRIMARY KEY (version)`

<a id="vault-secrets"></a>

## vault.secrets

Supabase Vault의 암호화된 시크릿 저장소. 복호화 값은 별도 Vault 뷰로 제공된다.

소유자: `supabase_admin`. 코멘트: 관리 테이블 권한 제한으로 문서에만 제공.

| 컬럼 | DB 타입 | NULL 허용 | 기본값·계산식 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `uuid` | 아니오 | `gen_random_uuid()` | 시크릿 UUID |
| `name` | `text` | 예 | 없음 | 시크릿 이름 |
| `description` | `text` | 아니오 | `''::text` | 시크릿 설명 |
| `secret` | `text` | 아니오 | 없음 | 디스크에 암호화되어 저장되는 시크릿 값 |
| `key_id` | `uuid` | 예 | 없음 | 암호화 키 식별자 |
| `nonce` | `bytea` | 예 | `vault._crypto_aead_det_noncegen()` | 암호화 nonce |
| `created_at` | `timestamp with time zone` | 아니오 | `CURRENT_TIMESTAMP` | 생성 시각 |
| `updated_at` | `timestamp with time zone` | 아니오 | `CURRENT_TIMESTAMP` | 수정 시각 |

DB 제약조건:

- `PRIMARY KEY (id)`

## Supabase에서 코멘트 확인하기

1. [개발 프로젝트 SQL Editor](https://supabase.com/dashboard/project/xedkwtdkpxejlqpbdstb/sql/new)를 연다.
2. 아래 SQL을 실행하면 테이블 설명과 컬럼 설명을 각각 확인할 수 있다.
3. 컬럼 조회 조건의 `c.relname = 'bookings'`를 추가하면 예약 테이블만 볼 수 있다. 다른 스키마는 `n.nspname`을 바꾼다.
4. Table Editor의 컬럼 편집 화면에서 Description 항목을 제공하는 경우 같은 코멘트를 확인할 수 있다. UI 표시는 버전에 따라 달라지므로 SQL 조회가 확실한 방법이다.

```sql
-- 테이블 설명
select n.nspname as schema_name, c.relname as table_name,
       obj_description(c.oid, 'pg_class') as description
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where c.relkind in ('r', 'p')
  and n.nspname not in ('pg_catalog', 'information_schema')
  and n.nspname not like 'pg_toast%'
order by 1, 2;

-- 컬럼 설명: 필요하면 아래 조건에 and c.relname = 'bookings' 추가
select n.nspname as schema_name, c.relname as table_name,
       a.attname as column_name,
       format_type(a.atttypid, a.atttypmod) as data_type,
       col_description(c.oid, a.attnum) as description
from pg_class c join pg_namespace n on n.oid = c.relnamespace
join pg_attribute a on a.attrelid = c.oid
where c.relkind in ('r', 'p') and n.nspname = 'public'
  and a.attnum > 0 and not a.attisdropped
order by n.nspname, c.relname, a.attnum;
```

## 관련 파일

- `sql/development-schema-comments-20261026.sql`: 실제 적용 완료 코멘트 SQL. 행 데이터·RLS·컬럼 구조를 변경하지 않는다.
- `sql/development-schema-comments-rollback-20261026.sql`: 적용 전 기존 코멘트로 복원하는 SQL. 이번 프로젝트와 동일한 객체에만 사용한다.
- `sql/inspect-schema-comments.sql`: 코멘트 조회 SQL.
- `database-schema-snapshot.json`: 작성 시점의 실제 스키마 메타데이터. 레코드 데이터가 아니다.

## 근거

- 로컬 스키마 마이그레이션 및 `BookingSeries.java`, `Booking.java`, `LiveSession.java`, `Settlement.java`, 작업·결제·프로필 관련 서비스.
- [Supabase 테이블 관리](https://supabase.com/docs/guides/database/tables)
- [Supabase 사용자 관리](https://supabase.com/docs/guides/auth/managing-user-data)
- [Supabase Auth 모델 소스](https://github.com/supabase/auth/tree/master/internal/models)
- [Storage 스키마](https://supabase.com/docs/guides/storage/schema/design)
- [Realtime 권한](https://supabase.com/docs/guides/realtime/authorization)
- [Vault](https://supabase.com/docs/guides/database/vault)
- [PostgreSQL COMMENT](https://www.postgresql.org/docs/current/sql-comment.html)
