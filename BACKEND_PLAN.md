# 안심클린 백엔드 계획 — Java · Spring Boot · Supabase (승인 대기)

작성일: 2026-09-19
기준 코드: 커밋 `aba06d2` (고객용 29 + 매니저 27 화면, `api/*.js` 함수 96개, 테스트 136개)
읽는 순서: `CLAUDE.md` → `PLAN.md` → 이 문서. 승인되면 이 문서가 단계 7(백엔드)의 유일한 사양이다.

> **이 문서의 목적**: 어떤 모델(Opus 5 포함)·어떤 세션이 이어받아도 **같은 파일, 같은 API, 같은 DB, 같은 테스트**가 나오도록 결정을 전부 적어 둔다. "알아서" 할 여지를 남기지 않는다. 여기 없는 것을 정해야 하면 §13 "결정이 필요한 항목" 에 적고 사용자에게 묻는다.

---

## 0. 한 줄 결론과 바뀌는 결정

**Spring Boot(Java 21) 가 두 앱이 부르는 유일한 API 서버**이고, **Supabase 는 Postgres(DB)·Auth(휴대폰 OTP·비밀번호·토큰)·Storage(녹화본·사진)** 를 맡고, **LiveKit Cloud 가 영상 전송·녹화(Egress)** 를 맡는다. 앱은 Supabase 를 직접 부르지 않는다(anon key 를 앱에 넣지 않는다).

| 항목 | 지금(PLAN.md §0·CLAUDE.md §1) | 이 계획 | 이유 |
|---|---|---|---|
| 서버 언어 | Node.js 만 | **Java 21 + Spring Boot 3.5** | 백엔드 담당자가 다룰 수 있는 것이 Java·Spring Boot·Supabase 뿐 |
| `server/` (Node http 뼈대) | 단계 5 토큰 서버 자리 | **삭제** — 역할(LiveKit 토큰·녹화)이 Spring `live` 패키지로 옮겨간다 | 서버가 둘이면 유지보수 불가 |
| 인증 | 미정 (sessionStorage 표시) | **Supabase Auth** (비밀번호·JWT·refresh) — Spring 이 대신 호출한다. **휴대폰 인증번호(OTP)는 Spring 이 직접** 만들고 국내 문자 API 로 보낸다 (결정 2026-09-19) | 비밀번호 해시·토큰은 직접 만들지 않고, 문자 발송은 국내 업체를 쓴다 |
| DB | 없음 | **Supabase Postgres** + Flyway 마이그레이션(SQL 파일을 git 에) | 스키마 변경 이력이 코드와 같이 간다 |
| 녹화 저장 | 미정 | **Supabase Storage** 버킷 `recordings` (S3 호환 → LiveKit Egress 가 바로 올린다) | 저장소를 하나 더 두지 않는다 |
| 카메라 | 매니저 휴대폰 (단계 5 가정) | **별도 바디캠** 이 LiveKit **Ingress(RTMP)** 로 직접 송출. 휴대폰 앱은 시작·정지·미리보기만 (결정 2026-09-19) | 매니저 손이 자유로워야 하고 휴대폰 백그라운드 송출 문제가 사라진다 |
| 결제 | 없음 | **구조만**: `payments`·`payment_methods` 테이블 + `PaymentGateway` 인터페이스(`NoopPaymentGateway`). PG 연동은 사용자가 나중에 직접 | 나중에 인터페이스 구현체 하나만 추가하면 된다 |
| 프론트엔드 | mock | `api/*.js` 함수 **안만** `fetch` 로 교체. 화면·컴포넌트는 손대지 않는다 | 점검에서 이미 오류·로딩 처리를 화면에 넣어 두었다 |

승인되면 **B0 에서 `CLAUDE.md` §1 의 "서버: Node.js 만" 을 이 표대로 고치고 §11(백엔드 규칙)을 추가**한다. `PLAN.md` §0 의 "서버도 JS" 행도 같이 고친다.

### 프론트엔드 유지보수자(HTML/CSS/JS 만)와의 경계
- 프론트엔드 사람은 `apps/*/src/api/*.js` 와 `live/liveClient.js` 만 보면 된다. 그 안은 전부 `fetch` 한 줄짜리 함수다.
- 백엔드 사람은 `backend/` 만 보면 된다. API 응답 모양은 **지금 `mock.js` 에 있는 모양 그대로**다(§5). 화면이 무엇을 기대하는지 `mock.js` 를 열면 바로 보인다.
- 두 사람의 계약서 = §5 의 표 + `backend/docs/api.md`(B1 에서 같은 내용을 옮겨 적고, 이후엔 이 파일이 기준).

---

## 1. 아키텍처

```
 고객 앱 (Capacitor/웹)  ──┐                                   ┌── Supabase Auth   (비밀번호 · JWT · refresh)
 매니저 앱 (Capacitor/웹) ──┼── HTTPS JSON ──▶  Spring Boot API ──┼── Supabase Postgres (모든 데이터, Flyway 로 관리)
                            │   Bearer JWT      backend/          ├── Supabase Storage (녹화본 MP4 · 프로필 사진, 서명 URL)
                            │                                     ├── LiveKit Cloud   (방·토큰·Ingress·Egress 녹화 → Storage S3)
                            │                                     ├── 국내 문자 API   (인증번호 SMS)
 고객 앱 ─ WebRTC(시청) ──▶ LiveKit Cloud ◀── RTMP ── 바디캠       └── FCM            (푸시: 새 콜 · 배정 · 정산)
 매니저 앱 ─ WebRTC(미리보기) ─▶   ▲  └── Egress ──▶ Storage
                                   └── 매니저 앱은 "시작/정지" 만 Spring 에 보낸다 (영상은 바디캠 → Ingress)
```

역할 분담(누가 무엇을 절대 하지 않는가):

| 구성요소 | 한다 | 하지 않는다 |
|---|---|---|
| 앱 | Spring API 호출, LiveKit 방 접속(토큰은 Spring 이 발급 — 고객 시청·매니저 미리보기), 화면 | Supabase 직접 호출, 비밀키 보관, 가격·상태 계산, **영상 송출** |
| 바디캠 | 정해진 RTMP 주소·스트림 키로 송출 (기기에 한 번 설정) | 앱과 직접 통신 |
| Spring | 인증 위임(Supabase Auth REST), **OTP 생성·검증 + 문자 발송**, 권한 검사(JWT + role), 모든 비즈니스 규칙(상태기계·매칭·가격·정산), Postgres 읽기/쓰기, LiveKit 토큰·Ingress·Egress, 서명 URL 발급, 푸시 발송, 한국어 표시 문자열 만들기 | 영상 데이터 중계, 비밀번호 해시 직접 구현 |
| Supabase Auth | 비밀번호 저장, access/refresh 토큰 발급, JWT 서명 (사용자 생성은 Spring 이 admin API 로) | OTP(→ Spring), 역할·프로필(→ `profiles` 테이블) |
| 국내 문자 API | 인증번호 SMS 발송 (`common/sms/SmsSender` 인터페이스 뒤. 기본 구현 **Solapi(구 CoolSMS)**, 업체 교체는 클래스 하나) | 그 외 문자 |
| Supabase Postgres | 저장. RLS 는 **전 테이블 deny-all** (anon/authenticated 로는 아무것도 못 읽음. Spring 만 service_role 로 접속) | PostgREST 로 앱에 직접 노출 |
| Supabase Storage | 녹화본·사진 보관. 비공개 버킷 + Spring 이 만든 **서명 URL(짧은 만료)** 로만 접근 | 공개 버킷 |
| LiveKit Cloud | WebRTC 전송, 방 관리, **Ingress(바디캠 RTMP → 방 참가자)**, Egress(방 합성 녹화 → Storage 의 S3 엔드포인트) | 인증(토큰은 Spring 이 API key/secret 으로 서명) |

왜 앱이 Supabase 를 직접 부르지 않는가: (1) 프론트엔드 규칙 "화면은 `api/*.js` 만 안다" 를 그대로 유지, (2) RLS 정책을 앱 수만큼 관리하지 않아도 됨, (3) 가격·매칭·상태 전이가 한 곳(Spring)에만 있음, (4) 나중에 Supabase 를 다른 Postgres 로 바꿔도 앱은 그대로.

### 원칙 3개 (모든 설계가 여기서 나온다)
1. **화면 하나 = 엔드포인트 하나(BFF).** `getHome()` 이 홈 화면에 필요한 것을 한 번에 돌려주듯, 서버도 `GET /api/v1/manager/home` 하나로 준다. 조합은 서버가 한다.
2. **응답 모양 = `mock.js` 모양.** 한국어 표시 문자열("9월 15일 (화)", "85,000원", "4:32 후 만료")도 서버가 만든다. 형식 규칙은 `common/format/KoreanFormat.java` 한 파일에만 있다. 앱은 포맷을 모른다. (§6.7)
3. **상태는 서버만 바꾼다.** 앱은 "청소 시작" 같은 **동작**을 보내고, 서버가 상태기계(§6)에 따라 바꾼 결과를 돌려준다. 앱이 `status` 값을 써서 보내는 일은 없다.

---

## 2. 저장소 구조

프론트엔드 모노레포 안에 `backend/` 를 둔다(같은 git, 같은 PR 에서 API 와 `api/*.js` 를 같이 바꾸기 위해). npm workspace 에는 넣지 않는다(Java 프로젝트).

```
청소앱 프론트엔드/
├─ backend/                                 Spring Boot (Maven Wrapper — Maven 설치 불필요)
│   ├─ mvnw · mvnw.cmd · .mvn/             start.spring.io 가 만든 래퍼. 손대지 않음
│   ├─ pom.xml                              의존성·버전 (§2.2). 버전은 여기 한 곳에만
│   ├─ README.md                            실행·환경변수·명령 (프론트 README 와 같은 톤)
│   ├─ .env.example                         환경변수 목록 (값 없음). .env 는 커밋 금지
│   ├─ docs/
│   │   ├─ api.md                           엔드포인트 계약 (§5 를 옮겨 적고 이후 여기서 관리)
│   │   ├─ domain.md                        상태기계·매칭·가격 규칙 (§6)
│   │   └─ runbook.md                       배포·마이그레이션·장애 대응 (§12)
│   └─ src/
│       ├─ main/java/kr/ansimclean/api/
│       │   ├─ ApiApplication.java
│       │   ├─ common/                      기능과 무관한 공통 코드만. 비즈니스 로직 금지
│       │   │   ├─ config/                  CorsConfig · JacksonConfig · ClockConfig · AsyncConfig
│       │   │   ├─ security/                SecurityConfig · SupabaseJwtDecoderConfig · CurrentUser(@AuthenticationPrincipal 래퍼) · Roles
│       │   │   ├─ error/                   ApiException · ErrorCode(enum) · GlobalExceptionHandler · ErrorResponse(record)
│       │   │   ├─ format/                  KoreanFormat (날짜·시간·금액·만료 문자열) — 표시 문자열은 여기서만
│       │   │   ├─ supabase/                SupabaseAuthClient · SupabaseStorageClient (RestClient 기반, REST 만)
│       │   │   ├─ sms/                     SmsSender(인터페이스) · SolapiSmsSender · LoggingSmsSender(local 프로필: 콘솔에 코드 출력)
│       │   │   └─ time/                    AppClock (Asia/Seoul, 테스트에서 고정 가능)
│       │   ├─ auth/                        로그인·가입·OTP(생성·검증)·refresh·로그아웃 (두 앱 공용, role 로 구분)
│       │   ├─ profile/                     profiles · 고객 주거정보 · 주소 · 알림설정 (두 앱)
│       │   ├─ catalog/                     카테고리·옵션·주의사항 칩·문구·가격표 (읽기 전용 참조 데이터)
│       │   ├─ booking/                     고객 예약 생성·조회·변경·취소 + 상태기계
│       │   ├─ offer/                       매니저 콜(제안) 생성·만료·수락·거절 (매칭)
│       │   ├─ job/                         매니저 작업: 준비·시작·체크리스트·일시정지·완료
│       │   ├─ live/                        LiveKit 토큰(시청·미리보기)·Ingress(바디캠)·Egress·웹훅·녹화본 서명 URL
│       │   ├─ payment/                     구조만: PaymentGateway 인터페이스 + NoopPaymentGateway · payments/payment_methods 조회 (PG 는 사용자가 나중에)
│       │   ├─ settlement/                  정산 생성·조회
│       │   ├─ schedule/                    매니저 근무시간·근무불가·일정 조회
│       │   ├─ managerhome/                 GET /manager/home 조합 (booking·offer·job 을 읽기만)
│       │   ├─ support/                     약관·FAQ·공지·1:1 문의·고객센터
│       │   └─ push/                        FCM 토큰 등록·발송
│       ├─ main/resources/
│       │   ├─ application.yml              공통 설정 (값은 환경변수 참조)
│       │   ├─ application-local.yml        로컬 개발 (dev Supabase 프로젝트)
│       │   ├─ application-test.yml         테스트 (Testcontainers Postgres)
│       │   └─ db/migration/                Flyway. V001__schema.sql · V002__seed_catalog.sql · V003__seed_terms.sql …
│       └─ test/java/kr/ansimclean/api/    main 과 같은 패키지 구조. §11
├─ apps/*/src/api/http.js                  fetch 래퍼 (새로 추가, 두 앱 동일 파일)
├─ apps/*/src/api/session.js               토큰 보관 (새로 추가)
├─ apps/*/src/api/{customer,manager}.js    함수 안만 http 호출로 교체
├─ apps/*/src/api/mock.js                  DEV `?scenario=` 와 테스트에서만 사용 (삭제하지 않음)
└─ server/                                 B1 에서 삭제 (package.json workspaces · vitest include 에서도 제거)
```

### 2.1 패키지 안의 파일 규칙 (모든 기능 패키지 동일)
```
booking/
  BookingController.java      @RestController. 경로·검증·DTO 변환만. 비즈니스 로직 금지 (10줄 넘는 메서드 금지)
  BookingService.java         트랜잭션 경계(@Transactional). 규칙은 전부 여기. 다른 기능의 Service 를 부를 수 있고 Repository 는 자기 것만
  BookingRepository.java      Spring Data JPA 인터페이스. 메서드 이름 쿼리 우선, 복잡하면 @Query(JPQL). native 는 금지(마이그레이션 SQL 은 예외)
  Booking.java                @Entity. 필드 + getter + 상태 전이 메서드(`markAssigned(managerId)` 처럼 규칙이 있는 변경). setter 없음
  BookingStatus.java          enum (DB 의 text 컬럼과 이름 일치)
  dto/                        요청/응답은 Java record. 응답 record 필드 이름 = mock.js 키 이름 (camelCase)
    CreateBookingRequest.java
    ReservationResponse.java
  BookingMapper.java          Entity → 응답 record 변환 (정적 메서드). 표시 문자열은 KoreanFormat 호출
```
- 클래스 하나 = 파일 하나. 내부 클래스는 record 만 허용.
- Lombok 사용 금지(마법 없이 읽히도록). DTO 는 record, 엔티티는 명시적 getter.
- 엔티티를 컨트롤러 밖으로 내보내지 않는다(항상 dto).
- 예외는 `ApiException(ErrorCode, message)` 하나로 던진다. `GlobalExceptionHandler` 가 `{ "error": { "code", "message" } }` 로 바꾼다 (§5.0).
- 모든 시간은 `Instant`(UTC) 로 저장, 표시할 때만 `KoreanFormat` 이 `Asia/Seoul` 로 바꾼다. `LocalDate`/`LocalTime` 은 예약 날짜·시간처럼 "벽시계" 값에만.
- 금액은 `long` 원 단위(`int` 금지, 소수 금지). `BigDecimal` 은 수수료율 계산 때만.
- Postgres `text[]` 는 엔티티에서 `@JdbcTypeCode(SqlTypes.ARRAY) List<String>`, `jsonb` 는 `@JdbcTypeCode(SqlTypes.JSON)` + record/Map — Hibernate 내장 기능만 쓴다. 별도 컨버터·라이브러리(hypersistence 등) 금지.
- 쓰기 엔드포인트(예약 생성·콜 수락·작업 시작/완료·문의)는 **`Idempotency-Key` 헤더**를 받는다(앱이 UUID 를 만들어 보냄). `common/web/IdempotencyFilter` 가 `idempotency_keys` 테이블에 24시간 보관해 같은 키의 재요청은 저장된 응답을 그대로 돌려준다(더블 탭·재시도로 예약이 두 번 생기지 않게).

### 2.2 의존성 (pom.xml 에 이것만. 추가 시 이 표와 `PLAN.md` 에 이유 한 줄)
| 의존성 | 용도 |
|---|---|
| `spring-boot-starter-web` | REST |
| `spring-boot-starter-validation` | 요청 DTO `@NotBlank` 등 |
| `spring-boot-starter-data-jpa` + `org.postgresql:postgresql` | DB |
| `org.flywaydb:flyway-core` + `flyway-database-postgresql` | 마이그레이션 |
| `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` | Supabase JWT 검증 |
| `spring-boot-starter-actuator` | `/actuator/health` (배포 헬스체크) |
| `io.livekit:livekit-server` | LiveKit 토큰·Ingress·Egress·RoomService(mute)·웹훅 검증 (Java 서버 SDK) |
| `com.google.firebase:firebase-admin` | FCM 푸시 (B6) |
| `spring-boot-starter-test` · `spring-security-test` · `org.testcontainers:postgresql` · `org.testcontainers:junit-jupiter` | 테스트 |

버전: Java **21**, Spring Boot **4.1.1** (B1 생성 시점의 start.spring.io 기본 GA — 계획 초안의 4.0.x 대신. 실제 버전 문자열에 `.RELEASE` 접미사는 없다). 3.5 는 2026-06 에 OSS 지원이 끝났으므로 새 프로젝트에 쓰지 않는다. 4.x 는 스타터·테스트 의존성 이름이 3.x 와 다르므로 **start.spring.io 가 생성한 `pom.xml` 의 이름을 그대로 쓴다**(위 표의 이름은 기능 설명용). Spring Framework 7 · Spring Security 7 · Hibernate 7 기준으로 코드를 쓴다(Security 는 람다 DSL 만, `RestClient` 사용). LiveKit·Firebase 는 Maven Central 최신 안정 버전을 B0 에서 확인해 pom 에 고정하고 이 표에 적는다.

### 2.3 백엔드 컴퓨터에 필요한 것 (프론트엔드 컴퓨터와 다른 기기여도 된다)
프론트엔드와 백엔드는 **같은 git 저장소, 다른 컴퓨터**에서 작업한다. 프론트엔드 컴퓨터에는 Java 가 없어도 된다(§3-12 의 검증 명령은 `backend/` 가 있을 때만 돈다). 백엔드 컴퓨터는 아래만 갖추면 되고, OS 는 상관없다.

| 필요 | 방법 | 확인 |
|---|---|---|
| JDK 21 | Temurin 21 (adoptium.net) 또는 OS 패키지 | `java -version` → 21 |
| Maven | 필요 없음 — `backend/mvnw` (start.spring.io 가 넣어 줌) | `./mvnw -v` |
| Docker | **필수에 가깝다**. 테스트 DB(Testcontainers Postgres 16)용. 없으면 통합 테스트가 dev Supabase 에 붙어 느리고 격리가 안 됨 (§11 대안) | `docker ps` |
| Supabase | CLI 불필요. 대시보드에서 프로젝트 2개(`ansimclean-dev`, `ansimclean-prod`, 리전 **Northeast Asia (Seoul)**) | 프로젝트 URL·키 |
| LiveKit Cloud | 프로젝트 1개, API key/secret, **Ingress + Egress** 활성 | `LIVEKIT_URL` |
| 국내 문자 API | Solapi 계정 + 발신번호 사전등록(통신사 규정) + API key/secret. 다른 업체면 `SmsSender` 구현체만 추가 | 테스트 발송 1건 |
| 바디캠 | 기기 사양 확인: **RTMP 푸시 지원 여부**(§8), 설정 방법, 배터리/네트워크 상태를 읽을 API 유무 | §13-19 |
| Firebase | 프로젝트 1개(두 앱 등록), 서비스 계정 JSON | B6 |
| 터널 | 로컬 개발 중 LiveKit 웹훅·실기기 테스트용 `cloudflared` 또는 `ngrok` | 공개 URL |

두 컴퓨터가 만나는 지점은 **`backend/docs/api.md`(계약)** 와 **dev 서버 URL**(백엔드 컴퓨터가 터널로 열거나 B7 전에 dev 배포) 뿐이다. 프론트엔드 컴퓨터는 `VITE_API_URL` 만 그 주소로 바꾸면 된다. 서로의 커밋을 `git pull` 로 받는다(원격 저장소가 아직 없으므로 B0 에서 GitHub 비공개 저장소를 만든다 — push 는 사용자 승인 후).

### 2.4 기능 패키지 사이의 연결 규칙 — 도메인 이벤트 (구조의 핵심)
기능 패키지가 서로를 **직접 부르지 않게** 한다. 예약 서비스가 푸시·LiveKit·문자·정산을 직접 호출하면 (1) 트랜잭션이 커밋되기 전에 외부 호출이 나가 롤백 시 유령 푸시가 생기고 (2) 외부 장애가 DB 저장까지 실패시키며 (3) 패키지가 서로 얽혀 유지보수가 어렵다. 그래서:

- **상태를 바꾸는 Service 는 DB 만 바꾸고 이벤트를 발행한다** (`ApplicationEventPublisher.publishEvent(new BookingAssigned(bookingId, managerId))`). 이벤트 클래스는 `common/events/` 에 record 로 모아 둔다: `OffersDispatched` · `BookingAssigned` · `BookingStarted` · `BookingPaused` · `BookingResumed` · `BookingCompleted` · `BookingCancelled` · `BookingRescheduled` · `SeriesEnding` · `CameraOnline` · `CameraOffline` · `RecordingReady` · `OtpRequested` · `InquirySubmitted`.
- **외부 효과(푸시·LiveKit·문자·Storage)는 아웃박스로 보낸다.** 이벤트는 같은 트랜잭션에서 `outbox_events` 행으로 저장되고(§4), `OutboxDispatcher`(5초, §2.5)가 `pending` 행을 핸들러(`push/PushHandler`, `live/LiveHandler`, `auth/SmsHandler`)에 전달한다. 성공 → `sent`, 실패 → `attempts+1`, `next_attempt_at = now + 2^attempts 분`(최대 6회) → `failed`(운영자 로그). `@TransactionalEventListener(AFTER_COMMIT)` 는 **지연을 줄이려고 디스패처를 즉시 깨우는 용도**로만 쓴다 — 커밋 직후 프로세스가 죽어도 행이 남아 있어 유실되지 않는다. 롤백된 트랜잭션의 행은 함께 사라진다.
- **DB 안에서 끝나는 후속 처리(정산 생성·체크리스트 복사·이력 기록)는 같은 트랜잭션 안에서 Service 가 다른 기능의 Service 를 호출**한다(`SettlementService.createFor(booking)`). 즉 "같은 트랜잭션 = 직접 호출, 외부 효과 = 이벤트".
- **핸들러는 멱등**(같은 이벤트가 두 번 와도 결과가 같게: 푸시는 `outbox_events.id` 를 FCM collapse key 로, LiveKit 호출은 현재 상태를 읽고 필요할 때만). 아웃박스와 별개로 **상태 기반 재조정**도 둔다(§2.5 `LiveReconciler`): 이벤트를 놓쳐도 DB 상태가 "해야 할 일" 을 말해 준다. 예: `bookings.status=done ∧ live_sessions.status≠ended` → 다시 `stop`.
- 허용되는 의존 방향: `managerhome → booking, offer, schedule (읽기 전용)` · `offer → booking (Service)` · `job → booking, live(Service 아님, 이벤트)` · `settlement ← booking (이벤트가 아니라 같은 tx 직접 호출)` · `live → booking (읽기: 상태 확인)`. 순환 금지. ArchUnit 테스트(`ArchitectureTest`) 로 강제한다.

### 2.5 스케줄러 한눈에 (각 기능 패키지에 클래스 하나씩. 전부 `Asia/Seoul`, 단일 인스턴스 전제 §13-11 — 2026-09-27 코드의 `@Scheduled` 13개와 맞춤)
| 클래스 | 주기 | 하는 일 |
|---|---|---|
| `OfferExpirer` | 30초 | `pending ∧ expires_at<now` → `expired` · 정지·미승인·탈퇴 매니저의 `pending` → `cancelled`(문제 목록 6번); 그 예약에 pending 0 이면 재매칭 1회 → 없으면 운영자 로그. 두 단계는 서로 막지 않는다 |
| `MatchingRetry` | 매시 05분(08:05~20:05) | ① 밤(21시~08시)에 미뤄 둔 새 콜을 보낸다(`releaseHeldDispatches`, 보통 08:05 첫 실행 — 문제 목록 2번, V046) ② 매칭 실패로 멈춘 예약(약속 3시간 전까지)에 지금 매칭 구간(마지막 일정 변경·방문 취소 이후)에 이 예약의 콜을 받은 적 없는 새 후보가 생겼으면 새 회차로 그들에게만 (사용자 결정 2026-09-27, V043). 두 단계는 서로 막지 않는다 |
| `SeriesDispatcher` | 매일 09:00 (2026-09-28 전 03:00) | 정기청소 회차 중 `service_date − dispatch_lead_days(7) ≤ 오늘` 인 `requested` 를 매칭(기존 매니저 우선 10분 → 일반) · 종료 7일 전 시리즈에 `SeriesEnding` |
| `StaleBookingCleaner` | 매일 03:10 | `requested/assigning ∧ service_date < 오늘` → `cancelled(reason=unassigned)` + 고객 푸시 · `scheduled ∧ service_date < 오늘−1` → `cancelled(reason=not_started)` + 운영자 로그 + 고객·매니저 푸시(거래성, 검수 4차) + 회차가 다 사라진 시리즈 닫기 (매니저 무단 미방문) |
| `OutboxDispatcher` | 5초 (+ 커밋 직후 즉시) | `outbox_events.pending ∧ next_attempt_at ≤ now` 를 핸들러로. 재시도·백오프·`failed` |
| `LateStartNotifier` | 1분 | `scheduled ∧ starts_at + tardyMinutes(30) ≤ now ∧ late_notified_at 없음`(약속 시각이 12시간 안) → 고객 푸시 '매니저가 늦어지고 있어요' 한 번 (자체 점검 #25, V037) |
| `LiveReconciler` | 1분 | **웹훅 유실 대비 LiveKit 조회**: `waiting_camera/live/paused` 세션마다 `ListParticipants(room)`(카메라 참가자 유무·트랙 muted) · `ListEgress(room)`(status·result) · `ListIngress`(state) 를 읽어 DB 와 다르면 웹훅과 같은 전이를 적용 · done 인데 라이브 미종료 → stop 재시도 · `mute_pending` 재시도 · `waiting_camera` 5분 초과 → 매니저 푸시 1회 · `pending_room` 적용 · `recording_status=recording` 인데 Egress 가 끝나 있으면 `ready/failed` 반영 |
| `ManagerPenaltyCleaner` | 매일 03:20 | 12개월 지난 패널티 만료 + 무사고 3개월 회복(-1, 남은 점수까지) |
| `RepairPhotoCleaner` | 매일 03:40 | 올린 뒤 24시간 동안 예약에 붙지 않은 수리 사진 삭제 (감사 M-04) |
| `RecordingRetention` | 매일 04:00 | `done + recording_retention_days(90)` 지난 녹화본 삭제 |
| `AuthCleaner` | 매일 04:10 | 24시간 지난 멱등 키, 1일 지난 인증 레코드 삭제, 속도 제한 키 정리 |
| `InquiryRetention` | 매일 04:30 | `inquiries.purge_after < now`(탈퇴 + 3년) 문의 삭제 (자체 점검 #31, V038) |
| `DeletedAccountPurger` | 매일 04:20 | `deleted_at + 5년`(서울 달력) 지난 계정의 남은 개인정보 파기 (결정 §13-22, 구현 2026-09-27): 고객 예약·정기청소의 주소(시도·시군구만 남김)·요청·주의사항·수리 상세·자유 입력 취소 사유, 결제 수단, 매니저 근무시간·근무 불가·패널티·신원/교육 기록(지금은 탈퇴 때 지운다 — 그 전에 탈퇴한 계정 몫), 약관 동의, 남은 문의. 예약·정산·결제 **행은 남긴다**(상대방 이력·법정 기록, 비운 뒤엔 누구인지 알 수 없다). 끝나면 `profiles.purged_at`(V040) |

---

## 3. 백엔드 코드 규칙 (B0 에서 `CLAUDE.md` §11 로 그대로 옮긴다)

1. **경계 고정.** Controller(입출력) → Service(규칙·트랜잭션) → Repository(DB). 역방향 참조 금지. 다른 기능의 Repository 를 직접 쓰지 않는다(그 기능의 Service 를 부른다). **외부 효과는 트랜잭션 안에서 호출하지 않는다 — 아웃박스 행으로 남기고 디스패처가 보낸다(§2.4).** 실패할 수 있는 외부 효과는 반드시 재조정 스케줄러가 있다(§2.5).
2. **응답 모양은 `docs/api.md` 가 유일한 기준.** 필드 이름은 앱 `mock.js` 의 키와 글자 하나까지 같다. 바꾸려면 `api.md` → 백엔드 → `api/*.js` 를 한 커밋에서 같이 바꾼다.
3. **오류는 `ApiException` 만 던진다.** HTTP 상태·코드는 `ErrorCode` enum 에서만 정한다(§5.0). 스택트레이스·SQL 이 응답에 나가면 안 된다.
4. **DB 스키마는 Flyway SQL 로만 바꾼다.** `spring.jpa.hibernate.ddl-auto=validate` 고정(엔티티와 DB 가 다르면 기동 실패 = 원하는 동작). 마이그레이션 파일은 커밋 후 절대 수정하지 않는다. 되돌리기도 새 버전으로.
5. **비밀값은 환경변수로만.** `application*.yml` 에 값 금지(`${VAR}` 참조만). `.env.example` 에 이름만. 커밋 전 `git diff | grep -i secret` 습관.
6. **상태 전이는 엔티티 메서드로.** `booking.start()` 처럼 허용된 전이만 메서드로 두고, 잘못된 전이는 `ApiException(INVALID_STATE)`. 서비스에서 `setStatus` 금지.
7. **시간·금액·표시 문자열** — `Instant` 저장 / `KoreanFormat` 표시 / `long` 원. §2.1.
8. **권한은 두 겹.** `SecurityConfig` 에서 경로 접두사(`/api/v1/customer/**` → `ROLE_CUSTOMER`, `/manager/**` → `ROLE_MANAGER`) + Service 에서 소유권(`booking.customerId == currentUser.id`, 아니면 `NOT_FOUND` — 존재 여부를 노출하지 않는다).
9. **테스트는 기능마다 3층.** Service 단위(Mockito, 규칙·상태기계) / Controller 슬라이스(`@WebMvcTest`, JSON 모양·권한) / 통합(`@SpringBootTest` + Testcontainers Postgres, 핵심 흐름 8개 §11). 새 엔드포인트는 셋 다 없으면 보고 불가.
10. **로그에 개인정보 금지.** 전화번호·주소·토큰은 마스킹(`KoreanFormat.maskPhone`). 요청 로그는 `method path status ms userId` 만.
11. **한 항목 = 한 커밋** (`feat(backend): … (단계 B3)`), 회차마다 §14 형식으로 보고 후 정지. 프론트 규칙과 같다.
12. **검증 명령 (보고 전 필수. 백엔드 컴퓨터에서 실행. 프론트엔드 컴퓨터는 아래 마지막 줄만)**
    ```bash
    cd backend && ./mvnw -q verify            # 컴파일 + 단위·슬라이스·통합 테스트 (Docker 필요)
    ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=local &  # 기동 후
    curl -s localhost:8080/actuator/health    # {"status":"UP"}
    cd .. && npm test && npm run lint && npm run check:tokens && npm run build   # 프론트 계약이 깨지지 않았는지
    ```

---

## 4. 데이터 모델 (Flyway `V001__schema.sql` — 아래 SQL 이 그대로 파일이 된다)

원칙: Postgres `enum` 타입 대신 `text + check` (값 추가가 마이그레이션 한 줄), 모든 PK 는 `uuid`(`gen_random_uuid()`), 모든 시각은 `timestamptz`, 모든 테이블 `created_at`/`updated_at`, **RLS 는 켜고 정책은 없음**(= anon/authenticated 로 접근 불가, Spring 은 `service_role`/직접 접속이라 RLS 를 우회한다). `auth.users` 는 Supabase 소유 — 건드리지 않고 `profiles.id` 로만 참조한다.

```sql
-- V001__schema.sql
create extension if not exists pgcrypto;

-- ── 계정 ──────────────────────────────────────────────────────────────
create table profiles (
  id            uuid primary key,                          -- = auth.users.id. FK 를 걸지 않는다: 탈퇴 시 Supabase 사용자는 지우고 이 행은 익명화해 남긴다(예약·정산 이력 보존)
  role          text not null check (role in ('customer','manager')),
  name          text not null,
  nickname      text,
  phone         text not null,                             -- E.164 (+8210...). 휴대폰 하나 = 살아 있는 계정 하나 = 역할 하나
  avatar_path   text,                                      -- Storage 경로 (profile-images/<id>.webp)
  signup_completed_at timestamptz,                         -- /auth/signup/complete 시각. null 이면 가입 중 (profileComplete=false)
  deleted_at    timestamptz,                               -- 탈퇴 시각. 익명화 후 채움
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);
create unique index profiles_phone_live on profiles(phone) where deleted_at is null;

create table idempotency_keys (                            -- 쓰기 요청 중복 방지 (§2.1, 알고리즘은 §6.9)
  key              text not null,
  user_id          uuid not null,
  request_hash     text not null,                          -- SHA-256(method + path + body). 다르면 IDEMPOTENCY_MISMATCH
  status           text not null check (status in ('processing','done')),
  response_status  int,
  response_body    jsonb,
  created_at       timestamptz not null default now(),
  primary key (key, user_id)
);

create table terms (                                       -- 약관 (문안은 법무 검토 후 여기서 교체)
  id        text not null,                                 -- service · privacy · location · marketing · ux · recording
  audience  text not null check (audience in ('customer','manager')),
  label     text not null,                                 -- "(필수) 서비스 이용약관"
  title     text not null,
  body      text not null,
  required  boolean not null,
  version   int not null default 1,
  sort      int not null,
  primary key (id, audience)
);

create table term_agreements (
  user_id    uuid not null references profiles(id) on delete cascade,
  term_id    text not null,
  version    int not null,
  agreed_at  timestamptz not null default now(),
  primary key (user_id, term_id)
);

create table notification_settings (                       -- 고객: push·marketing / 매니저: push·call·settlement
  user_id  uuid not null references profiles(id) on delete cascade,
  key      text not null,
  enabled  boolean not null default true,
  primary key (user_id, key)
);

create table phone_verifications (                        -- 휴대폰 인증번호 (Spring 이 만들고 검증. 코드 원문은 저장하지 않는다)
  id            uuid primary key default gen_random_uuid(),
  phone         text not null,                             -- E.164
  role          text not null check (role in ('customer','manager')),
  code_hash     text not null,                             -- SHA-256(code + server salt)
  expires_at    timestamptz not null,                      -- 발송 + 5분
  attempts      int not null default 0,                    -- 5회 틀리면 폐기
  verified_at   timestamptz,
  token         text unique,                               -- 검증 성공 시 발급하는 불투명 토큰 (가입 완료까지 15분)
  consumed_at   timestamptz,                               -- 가입에 쓰이면 채움 (재사용 금지)
  created_at    timestamptz not null default now()
);
create index phone_verifications_phone on phone_verifications(phone, created_at desc);

create table push_tokens (
  user_id    uuid not null references profiles(id) on delete cascade,
  platform   text not null check (platform in ('ios','android','web')),
  token      text not null,
  updated_at timestamptz not null default now(),
  primary key (user_id, token)
);

-- ── 고객 ──────────────────────────────────────────────────────────────
create table customer_housing (                            -- Auth/ProfileHousing
  customer_id   uuid primary key references profiles(id) on delete cascade,
  housing_type  text not null check (housing_type in ('apartment','studio','house','other')),
  size          text,                                      -- 평수 (자유 입력, sizeUnknown 이면 null)
  size_unknown  boolean not null default false,
  rooms         text,
  bathrooms     text,
  pet           text not null check (pet in ('yes','no')),
  updated_at    timestamptz not null default now()
);

create table addresses (
  id             uuid primary key default gen_random_uuid(),
  customer_id    uuid not null references profiles(id) on delete cascade,
  label          text not null default '기타',
  road_address   text not null,
  detail_address text,
  sigungu        text,                                     -- '강남구' (주소 검색 API 결과에서. 직접 입력이면 서버가 정규식으로 추출, 실패 시 null)
  sido           text,                                     -- '서울특별시'
  is_default     boolean not null default false,
  created_at     timestamptz not null default now()
);
create index addresses_customer on addresses(customer_id);
create unique index addresses_one_default on addresses(customer_id) where is_default;

-- ── 매니저 ────────────────────────────────────────────────────────────
create table manager_profiles (
  manager_id             uuid primary key references profiles(id) on delete cascade,
  approved               boolean not null default false,   -- 신원 확인·교육 완료 후 운영자가 true (콜을 받으려면 필요)
  rating                 numeric(2,1) not null default 0,
  completed_count        int not null default 0,
  active_since           date,
  areas                  text[] not null default '{}',     -- 활동 지역 '시도 시군구' 전체 문자열: '서울특별시 강남구', '부산광역시 해운대구' (구 이름만 쓰면 다른 도시의 '중구' 와 섞인다)
  sido                   text,                               -- 대체 매칭용 (예약의 sigungu 가 null 일 때 시/도만 비교)
  identity_verified_at   timestamptz,
  background_check_at    timestamptz,
  insurance_expires_on   date,
  education              jsonb not null default '[]',      -- [{ "label": "기본 청소 교육", "completedOn": "2024-03-01" }]
  bank_name              text,
  account_number_masked  text,                             -- 원본 계좌번호는 저장하지 않는다 (정산 대행사에 위임, §13)
  account_holder         text,
  updated_at             timestamptz not null default now()
);

create table manager_hours (                               -- 기본 근무시간. weekday 0=월 … 6=일
  manager_id  uuid not null references profiles(id) on delete cascade,
  weekday     smallint not null check (weekday between 0 and 6),
  start_time  time,
  end_time    time,
  off         boolean not null default false,
  primary key (manager_id, weekday)
);

create table manager_unavailable (                         -- 근무 불가 시간
  id          uuid primary key default gen_random_uuid(),
  manager_id  uuid not null references profiles(id) on delete cascade,
  date        date not null,
  all_day     boolean not null default false,
  start_time  time,
  end_time    time,
  repeat      text not null default 'none' check (repeat in ('none','weekly','monthly')),
  reason      text,
  created_at  timestamptz not null default now()
);
create index manager_unavailable_lookup on manager_unavailable(manager_id, date);

create table manager_devices (                             -- 바디캠 1대 = 매니저 1명. LiveKit Ingress 와 1:1
  manager_id     uuid primary key references profiles(id) on delete cascade,
  name           text not null,                            -- '안심캠 #AC-00231' (운영자가 등록)
  serial         text unique,                              -- 기기 일련번호
  ingress_id     text unique,                              -- LiveKit Ingress id (등록 시 생성, 재사용)
  stream_url     text,                                     -- rtmp://…/live  (기기에 한 번 설정)
  stream_key     text,                                     -- Ingress stream key. 앱 화면에는 마스킹해서 보여준다
  stream_status  text not null default 'offline' check (stream_status in ('offline','online')),  -- ingress_started/ended 웹훅
  last_online_at timestamptz,
  pending_room   text,                                     -- 활성 중이라 바꾸지 못한 방 이름. ingress_ended 때 적용
  last_report    jsonb,                                    -- { battery, network } 기기 API 가 있으면 (§13-19). 없으면 null
  reported_at    timestamptz
);

create table outbox_events (                               -- 외부 효과의 원장 (§2.4). 업무 트랜잭션과 같이 커밋된다
  id               uuid primary key default gen_random_uuid(),
  type             text not null,                          -- BookingAssigned · OffersDispatched · …
  payload          jsonb not null,
  status           text not null default 'pending' check (status in ('pending','sent','failed')),
  attempts         int not null default 0,
  next_attempt_at  timestamptz not null default now(),
  last_error       text,
  created_at       timestamptz not null default now(),
  sent_at          timestamptz
);
create index outbox_pending on outbox_events(status, next_attempt_at) where status = 'pending';

create table livekit_events (                              -- 웹훅 중복·순서 방어: 같은 event id 는 한 번만 처리
  id           text primary key,
  event        text not null,
  received_at  timestamptz not null default now()
);

-- ── 카탈로그 (참조 데이터, V002 에서 seed) ─────────────────────────────
create table service_categories (
  key        text primary key,                             -- home · office · appliance · move-in
  label      text not null,                                -- '가정/원룸 청소'
  promo      text not null check (promo in ('recurring','placeholder')),
  promo_text text,
  sort       int not null
);
create table catalog_prices (
  category_key text not null references service_categories(key),
  frequency    text not null check (frequency in ('recurring','oneTime')),
  base_amount  bigint not null,                            -- 원
  primary key (category_key, frequency)
);
create table catalog_options (
  category_key text not null references service_categories(key),
  key          text not null,
  kind         text not null check (kind in ('free','paid')),
  label        text not null,
  price        bigint not null default 0,
  icon         smallint not null,                          -- ASSETS.bookingOptionIcons[category][icon]
  sort         int not null,
  primary key (category_key, key)
);
create table catalog_notice_chips (
  category_key text not null references service_categories(key),
  key          text not null,
  label        text not null,
  icon         text not null,                              -- NoticeChip 아이콘 키
  sort         int not null,
  primary key (category_key, key)
);
create table catalog_frequency_copy (
  category_key text not null references service_categories(key),
  frequency    text not null,
  description  text not null,
  primary key (category_key, frequency)
);
create table checklist_templates (                         -- 작업 체크리스트 템플릿 (예약 배정 시 복사)
  category_key text not null references service_categories(key),
  group_name   text not null,
  label        text not null,
  sort         int not null,
  primary key (category_key, group_name, label)
);
create table catalog_reasons (                             -- 콜 거절·촬영 중단 사유 (Figma 문구)
  kind  text not null check (kind in ('reject','pause')),
  label text not null,
  sort  int not null,
  primary key (kind, label)
);
create table app_settings (                                -- 운영 값. 코드에 상수로 두지 않는다
  key   text primary key,                                  -- platform_fee_rate · offer_ttl_minutes · free_option_limit · time_slots · cancel_deadline_hours
  value jsonb not null
);

-- ── 정기청소 시리즈 (frequency=recurring 이면 매월 같은 날짜·시각에 예약을 만든다, §6.8 — 결정 2026-09-22) ──
create table booking_series (
  id              uuid primary key default gen_random_uuid(),
  customer_id     uuid not null references profiles(id),
  category_key    text not null references service_categories(key),
  weekday         smallint not null check (weekday between 0 and 6),
  service_time    time not null,
  road_address    text not null,
  detail_address  text,
  free_options    text[] not null default '{}',
  paid_options    text[] not null default '{}',
  notices         text[] not null default '{}',
  request         text,
  status          text not null check (status in ('active','ended','cancelled')),
  term_start      date not null,                           -- 첫 방문일
  term_end        date not null,                           -- 마지막 회차 날짜 (정기청소 = 매월 1회 × series_term_months, §6.8)
  visits_planned  int not null,                            -- = series_term_months (기본 6)
  ending_notified_at timestamptz,
  created_at      timestamptz not null default now(),
  cancelled_at    timestamptz
);
create index booking_series_active on booking_series(status, term_end);

-- ── 예약 = 작업 (고객이 만들고 매니저가 수행하는 같은 것) ────────────────
create table bookings (
  id              uuid primary key default gen_random_uuid(),
  series_id       uuid references booking_series(id),      -- 정기청소면 채움
  customer_id     uuid not null references profiles(id),
  manager_id      uuid references profiles(id),
  category_key    text not null references service_categories(key),
  frequency       text not null check (frequency in ('recurring','oneTime')),
  service_date    date not null,
  service_time    time not null,
  road_address    text not null,
  detail_address  text,
  sigungu         text,                                    -- 매칭용 (addresses.sigungu 와 같은 규칙)
  sido            text,
  free_options    text[] not null default '{}',
  paid_options    text[] not null default '{}',
  notices         text[] not null default '{}',
  request         text,
  repair_type     text,                                    -- Repair 화면에서 왔을 때 (appliance·furniture·plumbing …). 수리 전용 모델은 §13-24
  base_amount     bigint not null,
  options_amount  bigint not null default 0,
  total_amount    bigint not null,
  platform_fee    bigint not null,                         -- 생성 시점의 수수료율로 고정. offers.pay_amount = total_amount − platform_fee, 정산도 이 값
  duration_minutes int not null,                           -- 카테고리 기본 소요(app_settings.duration_hours) 스냅샷. 겹침 검사·일정 표시에 사용
  starts_at       timestamptz not null,                    -- service_date + service_time (Asia/Seoul) → 겹침 검사·취소 기한의 기준
  ends_at         timestamptz not null,                    -- starts_at + duration_minutes
  status          text not null check (status in ('requested','assigning','scheduled','in_progress','paused','done','cancelled')),
  payment_status  text not null default 'unpaid' check (payment_status in ('unpaid','authorized','paid','refunded')),  -- 결제 구조만 (PG 연동 전 항상 unpaid)
  assigned_at     timestamptz,
  started_at      timestamptz,
  ended_at        timestamptz,
  cancelled_at    timestamptz,
  cancel_reason   text,
  dispatch_round  int not null default 1,                  -- 오퍼 회차 (offers.round)
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);
create index bookings_customer on bookings(customer_id, status);
create index bookings_manager_date on bookings(manager_id, service_date);
create index bookings_status_date on bookings(status, service_date);
create unique index bookings_one_active_per_manager on bookings(manager_id) where status in ('in_progress','paused');
-- 같은 매니저에게 시간이 겹치는 예약 두 건이 배정될 수 없다 (동시 수락 경합의 최종 방어). btree_gist 필요
create extension if not exists btree_gist;
alter table bookings add constraint bookings_manager_no_overlap
  exclude using gist (manager_id with =, tstzrange(starts_at, ends_at) with &&)
  where (manager_id is not null and status in ('scheduled','in_progress','paused'));

create table booking_status_history (
  id          bigserial primary key,
  booking_id  uuid not null references bookings(id) on delete cascade,
  from_status text,
  to_status   text not null,
  actor_id    uuid,                                        -- null = 시스템(스케줄러)
  reason      text,
  at          timestamptz not null default now()
);
create index booking_status_history_booking on booking_status_history(booking_id);

create table offers (                                      -- 매니저에게 보낸 콜
  id            uuid primary key default gen_random_uuid(),
  booking_id    uuid not null references bookings(id) on delete cascade,
  manager_id    uuid not null references profiles(id),
  round         int not null default 1,                    -- 예약의 dispatch_round. 일정 변경·재매칭마다 +1 → 같은 매니저에게 다시 보낼 수 있다
  status        text not null check (status in ('pending','accepted','rejected','expired','cancelled','superseded')),  -- superseded: 일정 변경으로 무효가 된 accepted
  pay_amount    bigint not null,                           -- 매니저 수령 예정액 = total - platform_fee
  expires_at    timestamptz not null,
  responded_at  timestamptz,
  reject_reason text,
  created_at    timestamptz not null default now(),
  unique (booking_id, manager_id, round)
);
create index offers_manager_pending on offers(manager_id, status, expires_at);
create unique index offers_one_accepted on offers(booking_id) where status = 'accepted';

create table job_checklist_items (
  id          uuid primary key default gen_random_uuid(),
  booking_id  uuid not null references bookings(id) on delete cascade,
  group_name  text not null,
  label       text not null,
  done        boolean not null default false,
  done_at     timestamptz,
  sort        int not null
);
create index job_checklist_booking on job_checklist_items(booking_id);

create table job_pauses (
  id          uuid primary key default gen_random_uuid(),
  booking_id  uuid not null references bookings(id) on delete cascade,
  reason      text not null,
  paused_at   timestamptz not null default now(),
  resumed_at  timestamptz
);

create table live_sessions (
  booking_id        uuid primary key references bookings(id) on delete cascade,
  room_name         text not null unique,                  -- 'booking-<uuid>'
  status            text not null check (status in ('created','waiting_camera','live','paused','ended')),  -- waiting_camera: 시작 눌렀지만 바디캠 스트림이 아직 안 들어옴
  ingress_id        text,                                  -- 이 방에 묶인 바디캠 Ingress (manager_devices.ingress_id)
  camera_online_at  timestamptz,
  camera_track_sid  text,                                  -- mute 대상 (ingress_started 웹훅에서)
  mute_pending      boolean not null default false,        -- 카메라가 아직 안 붙었을 때 pause 를 누르면 true → 붙는 즉시 mute
  started_at        timestamptz,
  ended_at          timestamptz,
  egress_id         text,
  recording_status  text not null default 'none' check (recording_status in ('none','recording','uploading','ready','failed')),
  recording_path    text,                                  -- Storage 경로 recordings/<booking_id>.mp4
  duration_seconds  int,
  viewer_count      int not null default 0
);

create table settlements (
  id              uuid primary key default gen_random_uuid(),
  booking_id      uuid not null unique references bookings(id),
  manager_id      uuid not null references profiles(id),
  service_amount  bigint not null,
  platform_fee    bigint not null,
  other_fee       bigint not null default 0,
  final_amount    bigint not null,
  status          text not null check (status in ('pending','complete')),
  settled_at      timestamptz,
  created_at      timestamptz not null default now()
);
create index settlements_manager on settlements(manager_id, created_at desc);

-- ── 결제 (구조만. PG 연동은 사용자가 나중에 PaymentGateway 구현체로) ────
create table payment_methods (
  id           uuid primary key default gen_random_uuid(),
  customer_id  uuid not null references profiles(id) on delete cascade,
  provider     text not null,                              -- 'toss' | 'portone' … (PG 결정 후)
  billing_key  text not null,                              -- PG 가 준 빌링키 (카드번호는 절대 저장하지 않는다)
  label        text not null,                              -- '신한카드 1234'
  is_default   boolean not null default false,
  created_at   timestamptz not null default now()
);
create index payment_methods_customer on payment_methods(customer_id);

create table payments (
  id                 uuid primary key default gen_random_uuid(),
  booking_id         uuid not null references bookings(id),
  payment_method_id  uuid references payment_methods(id),
  provider           text not null,
  provider_tx_id     text unique,                          -- PG 거래 id
  amount             bigint not null,
  status             text not null check (status in ('pending','authorized','paid','failed','refunded')),
  failure_reason     text,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);
create index payments_booking on payments(booking_id);

-- ── 지원 ──────────────────────────────────────────────────────────────
create table notices (
  id           uuid primary key default gen_random_uuid(),
  audience     text not null check (audience in ('customer','manager','all')),
  title        text not null,
  body         text not null,
  published_at timestamptz not null default now()
);
create table faqs (
  id        uuid primary key default gen_random_uuid(),
  audience  text not null check (audience in ('customer','manager')),
  question  text not null,
  answer    text not null,
  sort      int not null
);
create table inquiries (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references profiles(id) on delete cascade,
  content     text not null,
  status      text not null default 'open' check (status in ('open','answered')),
  answer      text,
  created_at  timestamptz not null default now(),
  answered_at timestamptz
);

-- ── RLS: 전부 켜고 정책 없음 (앱 키로는 아무것도 못 읽는다) ──────────────
do $$ declare t text; begin
  for t in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table %I enable row level security', t);
  end loop;
end $$;

-- updated_at 자동 갱신
create or replace function set_updated_at() returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end $$;
do $$ declare t text; begin
  for t in select tablename from pg_tables where schemaname='public' and tablename in ('profiles','customer_housing','manager_profiles','bookings') loop
    execute format('create trigger %I_updated_at before update on %I for each row execute function set_updated_at()', t, t);
  end loop;
end $$;
```

`V002__seed_catalog.sql` 은 **`apps/customer/src/api/mock.js` 의 `BOOKING_CATEGORIES` · `BOOKING_OPTIONS` · `NOTICE_CHIPS` · `FREQUENCY_DESCRIPTIONS` · `TIME_SLOTS` · `FREE_OPTION_LIMIT` 와 `apps/manager/src/api/mock.js` 의 `REJECT_REASONS` · `PAUSE_REASONS` · `ACTIVE_JOB.checklist`(템플릿) 를 글자 그대로** insert 한다. 가격표(`catalog_prices`)는 Figma 에 없다 → §13. `V003__seed_terms.sql` 은 두 앱 `TERMS`/`MANAGER_TERMS` + `TERM_BODIES`. `V004__seed_support.sql` 은 `FAQ`·`NOTICES`(두 앱). seed 는 마이그레이션이므로 운영 반영 뒤 문구 변경은 새 버전(`V00N__update_terms.sql`)으로.

Storage 버킷(대시보드에서 만들고 `docs/runbook.md` 에 기록): `recordings`(비공개, MP4), `profile-images`(비공개, WebP). 둘 다 정책 없음 — Spring 이 service_role 로 서명 URL 을 만든다.

---

## 5. API 계약 (`backend/docs/api.md` 의 초안. 앱 함수 96개 전부 여기 있다)

공통: base `https://<api-host>/api/v1`, 요청/응답 JSON(UTF-8), 인증은 `Authorization: Bearer <accessToken>`. 날짜 `YYYY-MM-DD`, 시각 `HH:mm`, 시각(절대) ISO-8601 UTC. 목록은 배열 그대로(페이지네이션은 공지·정산 목록만 `?limit=&before=` — 처음엔 상한 100).

### 5.0 오류 형식과 코드 (`common/error`)
```json
{ "error": { "code": "BOOKING_NOT_CANCELLABLE", "message": "방문 전날까지만 취소할 수 있어요" } }
```
`message` 는 **화면에 그대로 보여도 되는 한국어**다. `ErrorCode` enum (HTTP 상태 포함):

| code | HTTP | 언제 |
|---|---|---|
| `VALIDATION` | 400 | `@Valid` 실패. `message` 는 첫 필드 오류 |
| `UNAUTHENTICATED` | 401 | 토큰 없음·만료·서명 불일치 (앱은 refresh 1회 후 로그인으로) |
| `FORBIDDEN` | 403 | 역할 불일치 (고객 토큰으로 `/manager/**`) |
| `NOT_FOUND` | 404 | 없는 리소스 **또는 남의 리소스** (존재를 알리지 않는다) |
| `INVALID_STATE` | 409 | 허용되지 않는 상태 전이 (`bookings.status` 기준) |
| `OFFER_EXPIRED` · `OFFER_TAKEN` | 409 | 콜 만료 / 다른 매니저가 먼저 수락 |
| `BOOKING_NOT_CANCELLABLE` · `BOOKING_NOT_CHANGEABLE` | 409 | 취소·변경 기한 초과 |
| `SLOT_UNAVAILABLE` | 409 | 날짜·시간 선택 불가 (주말·과거·마감) |
| `PHONE_TAKEN` | 409 | 가입 시 이미 있는 번호 |
| `PHONE_NOT_FOUND` | 404 | 비밀번호 재설정 시 가입되지 않은 번호 |
| `LOGIN_FAILED` | 401 | 번호/비밀번호 불일치 (앱 문구 "휴대폰 번호 또는 비밀번호를 확인해주세요") |
| `OTP_INVALID` · `OTP_EXPIRED` · `OTP_RATE_LIMITED` | 400/400/429 | 인증번호 |
| `PASSWORD_TOO_SHORT` | 400 | 8자 미만 |
| `LIVE_NOT_AVAILABLE` | 409 | 진행 중이 아닌 예약의 라이브 |
| `RECORDING_NOT_READY` | 404 | 녹화본 아직 없음 |
| `UPSTREAM` | 502 | Supabase/LiveKit/FCM 호출 실패 (원인은 로그에만) |
| `INTERNAL` | 500 | 그 외(예상하지 못한 예외·설정 누락). `message` 는 "잠시 후 다시 시도해주세요 (문의 번호 xxxxxxxx)" + `requestId` — 기준은 `backend/docs/api.md` §5.0 |

앱 `http.js` 는 `ApiError { status, code, message }` 를 던지고, 화면의 `useAsync` 가 이미 `ScreenError` 로 보여준다(추가 작업 없음). 폼 화면(로그인 등)은 `code` 로 분기한다.

### 5.1 인증 (`auth/`) — 두 앱 공용. `role` 은 `'customer' | 'manager'`
| 앱 함수 | 메서드 · 경로 | 요청 | 응답 | 비고 |
|---|---|---|---|---|
| `requestPhoneVerification(phone)` | `POST /auth/phone/otp` | `{ phone: "12345678", role }` | `{ sent: true, expiresIn: 300 }` | 서버가 `+8210…` 로 정규화 → 이미 살아 있는(`deleted_at is null`) `profiles` 가 있으면 role 과 무관하게 `PHONE_TAKEN`(휴대폰 하나 = 계정 하나) → 6자리 난수 생성, `phone_verifications` 에 해시 저장 → `SmsSender.send(phone, "[안심클린] 인증번호 123456 (5분 안에 입력)")`. 같은 번호 60초 1회, 시간당 5회(`OTP_RATE_LIMITED`). local 프로필은 문자 대신 콘솔 출력 |
| `verifyPhone(phone, code)` **(새 함수)** | `POST /auth/phone/verify` | `{ phone, code, role }` | `{ verificationToken, expiresIn: 900 }` | 최신 미만료 레코드와 해시 비교. 틀리면 `attempts+1`, 5회면 `OTP_INVALID` 로 폐기, 만료면 `OTP_EXPIRED`. 번호당 검증 시도 시간당 10회(`OTP_RATE_LIMITED`). 성공 시 불투명 토큰(32바이트 base64url) 발급. 앱은 가입 초안(`ansimclean.signup`)에 둔다 |
| `saveProfileInfo(profile)` | `POST /auth/signup` (인증 없음) | 고객 `{ verificationToken, name, nickname, password }` / 매니저 `{ verificationToken, name, password }` | `{ accessToken, refreshToken, expiresIn, user: { id, role, name, profileComplete: false } }` | 토큰 유효·미사용 확인 → 비밀번호 8자 미만 `PASSWORD_TOO_SHORT` → Supabase admin `POST /auth/v1/admin/users { phone, password, phone_confirm: true }` → `profiles` insert(role) → `consumed_at` → Supabase `grant_type=password` 로 세션 발급. 순서: (a) Supabase 에 같은 phone 의 사용자가 이미 있으면(이전 시도의 잔재) admin 으로 찾아 비밀번호만 갱신해 재사용 (b) 없으면 생성 (c) `profiles` insert — 실패하면 (b)에서 만든 사용자 삭제 (d) 토큰 발급. 이 엔드포인트는 재시도해도 같은 결과가 나오게(멱등) 만든다 |
| `saveProfileAddress(address)` | `PUT /customer/profile/address` | `{ roadAddress, detailAddress }` | `{ id, label: "집", roadAddress, detailAddress }` | `addresses` 에 `is_default=true` 로 |
| `saveProfileHousing(housing)` | `PUT /customer/profile/housing` | `{ housingType, size, sizeUnknown, rooms, bathrooms, pet }` | 같은 모양 | |
| `agreeTerms(ids)` / `agreeManagerTerms(ids)` | (로컬 초안) → `completeSignUp` 때 전송 | — | — | 사용자가 아직 없을 때 체크하므로 앱이 `sessionStorage ansimclean.signup.terms` 에 두었다가 완료 때 보낸다 |
| `completeSignUp()` / `completeManagerSignUp()` | `POST /auth/signup/complete` (Bearer) | `{ agreedTermIds: [...] }` | `{ profileComplete: true, user: { id, role, name } }` | 필수 약관 누락 → `VALIDATION`. `term_agreements` 기록, `profiles.signup_completed_at=now`(이후 `profileComplete` 의 유일한 기준), 알림 설정 기본 행. 매니저는 `manager_profiles(approved=false)` + 기본 근무시간 월~금 09–18 생성 |
| `requestPasswordReset(phone)` | `POST /auth/password/otp` | `{ phone, role }` | `{ sent: true, expiresIn, resendAfter }` | 비밀번호 재설정 1단계. 그 role 의 살아 있는 계정이 없으면 `PHONE_NOT_FOUND`. 확인은 `/auth/phone/verify` 재사용 (사용성 검토 2026-09-22) |
| `resetPassword({ verificationToken, password })` | `POST /auth/password/reset` | `{ verificationToken, password }` | 로그인과 같은 응답 | 비밀번호 재설정 2단계. Supabase admin 비밀번호 변경 → 토큰 소비 → 새 세션 → 다른 기기 세션 폐기(`scope=others`) |
| `loginWithPhone({ phone, password })` | `POST /auth/login` | `{ phone, password, role }` | `{ accessToken, refreshToken, expiresIn, user: { id, role, name } }` | Supabase `POST /auth/v1/token?grant_type=password`. 실패·역할 불일치 모두 `LOGIN_FAILED`(구분하지 않는다). 시간당 10회 |
| (http.js 내부) | `POST /auth/refresh` | `{ refreshToken }` | 같은 모양 | Supabase `grant_type=refresh_token`. 실패 → 앱은 로그아웃 처리 |
| `logout()` | `POST /auth/logout` (Bearer) | — | `{ ok: true }` | Supabase `POST /auth/v1/logout`(그 refresh 토큰 폐기). 실패해도 앱은 토큰을 지운다 |
| (http.js · RequireAuth) | `GET /auth/me` | — | `{ id, role, name, profileComplete }` | 앱 시작 시 1회. 401 이면 로그아웃 |
| `deleteAccount()` **(새 함수, 설정 › 회원 탈퇴)** | `DELETE /auth/account` (Bearer) | `{ password }` | `{ ok: true }` | 앱스토어 심사 필수(계정 생성이 있으면 앱 안에서 탈퇴 가능해야 함). 진행 중(`scheduled+`) 예약이 있으면 `INVALID_STATE`("진행 중인 예약을 먼저 취소해주세요"). 처리: `profiles` 익명화(name '탈퇴한 사용자', phone `deleted-<id>`, `deleted_at`) → 주소·주거·알림·푸시 토큰 삭제 → Supabase admin `DELETE /auth/v1/admin/users/{id}`. 예약·정산 이력은 남는다(법정 보존) |
| `getTerms()` / `getManagerTerms()` | `GET /terms?audience=customer\|manager` | — | 고객 `[{ id, label, required }]` / 매니저 `{ terms: [...], note }` | 인증 불필요 |
| `getTerm(id)` / `getManagerTerm(id)` | `GET /terms/{id}?audience=` | — | `{ id, title, body }` | |
| `getAuthCopy(screen)` (매니저) | 로컬 유지 (`mock.js AUTH_COPY`) | — | — | 문구는 앱 리소스. 서버 호출 없음 |
| `checkPassword(pw)` | 로컬 유지 (8자 규칙) | — | — | 서버는 `saveProfileInfo` 에서 다시 검사 |
| `signUpWith(method)` | 로컬 유지 | — | — | **소셜은 결정 전까지 전부 `phone` 흐름** (§13-1). `SignUp.jsx` 의 `start()` 가 `method` 를 `'phone'` 으로 보낸다 |
| `getHousingOptions()` | 로컬 유지 (`HOUSING_TYPES`·`PET_OPTIONS`) | — | — | 아이콘 키가 앱 에셋 |

### 5.2 고객 (`/customer/**`, `ROLE_CUSTOMER`)
| 앱 함수 | 메서드 · 경로 | 요청 | 응답 (= mock 모양) |
|---|---|---|---|
| `getHome()` | `GET /customer/home` | — | `{ slides: HOME_SLIDES 모양 }` (`app_settings.home_slides`) |
| `getRepair()` | `GET /customer/repair` | — | `{ hero: REPAIR_HERO 모양 }` (`app_settings.repair_hero`) |
| `getReservations()` | `GET /customer/reservations` | — | `{ header, tabs, reservations: [{ id, tab, category, title, status, date, time, rows: [{label,value}×3] }] }`. `tab`·`status`·`rows` 는 서버가 §6.1 규칙으로 만든다 |
| `changeReservation(id, { date, time })` | `PATCH /customer/reservations/{id}/schedule` | `{ date, time }` | 예약 1건 (위 모양). `label` 은 서버가 만든다(앱 인자 무시) |
| `cancelReservation(id)` | `POST /customer/reservations/{id}/cancel` | `{ reason?, wholeSeries?: bool }` | 예약 1건 — 정기청소면 `wholeSeries=true` 로 시리즈까지 취소(앱의 취소 시트에 "이번 회차만 / 정기청소 전체" 선택을 추가, §9.2-5) |
| `getBookingCategory(c)` | `GET /catalog/categories/{c}` | — | `{ label, promo, promoText? }` · 모르면 404 |
| `getFrequencyOptions()` | 로컬 유지 (`FREQUENCY_OPTIONS`) | | |
| `getTimeSlots()` | `GET /catalog/time-slots?category=&date=` | — | `{ am: [...], pm: [...], unavailable: ["13:00"] }` (`date` 있으면 마감된 시각 표시 — B3 는 빈 배열, 매니저 가용성 기반은 §13-6) |
| `getAddressReuseOptions()` | 로컬 유지 | | |
| `searchAddress(q)` | `GET /catalog/address?q=` | — | `{ query, results: [{ id, main, sub }] }` — **주소 API(§13-4) 연동 전에는 서버가 빈 배열** |
| `saveBookingStep()` | 로컬 유지 (초안은 sessionStorage) | | 서버 저장 없음 |
| `getBookingOptions(c)` | `GET /catalog/categories/{c}/options` | — | `{ free: [{key,label,icon}], paid: [{key,label,price:"+3,000원",icon}], freeLimit }` |
| `getNoticeChips(c)` | `GET /catalog/categories/{c}/notice-chips` | — | `{ chips: [{key,label,icon}], copy: NOTES_COPY }` (copy 는 `app_settings.notes_copy`) |
| `getConfirmCopy(c)` | `GET /catalog/categories/{c}/confirm-copy` | — | `{ copy, frequencyDescriptions: { recurring, oneTime }, seriesTermMonths: 6 }` — `seriesTermMonths` = `app_settings.series_term_months`(주기 화면 "N개월 동안 N회 방문해요" 의 N, 문제 목록 25번). 자세한 모양은 `backend/docs/api.md` 가 기준 |
| `getSavedAddress()` | `GET /customer/addresses/default` | — | `{ roadAddress, detailAddress }` · 없으면 404 |
| `getBookingQuote(c, draft)` **(새 함수, 확인 화면 진입 시)** | `POST /customer/bookings/quote` | `{ category, frequency, date, time, freeOptions[], paidOptions[] }` | `{ perVisit: { base, options, total, label: "85,000원" }, visits: [{ date, label: "2026년 11월 1일 (일)" }] (정기 4~5개 / 1회 1개), visitsPlanned, totalAmount, totalLabel: "340,000원", paymentNote: "결제는 청소 완료 후 진행돼요" }` — 저장 없음. 확인 화면 "요금" 행(§9.2-7) |
| `createBooking(c, draft)` | `POST /customer/bookings` (+ `Idempotency-Key`) | `{ category, frequency, date, time, roadAddress, detailAddress, freeOptions[], paidOptions[], notices[], request, saveAddress? }` — 청소 예약은 `repairType` 을 보내지 않는다(서버 본문의 `repairType` 은 수리 예약 `createRepairBooking` 용) | `{ id, category, frequency, date, time, roadAddress, detailAddress, serviceLabel, cleaner: "배정 후 알려드려요", managerAssigned: bool, matchingFailed: bool /* 매칭에 실패해 멈춘 예약 — 후보가 없으면 생성 응답부터 true, 밤에 아침으로 미룬 예약은 아침 매칭 전까지 false */, seriesId?, visits: [{ id, date, label }], perVisitLabel, totalAmount, totalLabel }` — 서버가 다시 견적을 계산해 저장하므로 앱이 본 견적과 다를 수 없다. 무료 옵션 3개 이상·모르는 key·주말·과거 → `VALIDATION`/`SLOT_UNAVAILABLE`. 자세한 모양·오류는 `backend/docs/api.md` 가 기준 |
| `getBooking(id)` | `GET /customer/bookings/{id}` | — | 위와 같은 모양 (완료 화면 새로고침용) |
| `getCompleteRows()` | 로컬 유지 (`COMPLETE_ROWS`) | | |
| `getLiveSession(bookingId)` | `GET /customer/bookings/{id}/live` | — | `{ manager: { name, description, avatar }, startedAt: "2025. 09. 11. 14:32", viewers, status: { title, description }, reassurance, playback: { currentLabel, totalLabel, ratio }, live: { state: "waiting\|live\|paused\|ended", available: bool /* state==live */, recordingReady: bool } }` — `status.title` 은 상태별 고정 문구(`waiting` "카메라 연결을 기다리고 있어요" · `live` Figma 문구 · `paused` "촬영이 잠시 중단됐어요" · `ended` "청소가 끝났어요"), `startedAt = bookings.started_at`. **영상만 송출**(결정 §13-23)이라 소리는 없다 — `avatar` 는 서명 URL(없으면 `"managerAvatar"` 키) |
| `getSettings()` | `GET /customer/settings` | — | `{ header, profile: { name: "김하늘 님", phone: "010-1234-5678", avatarSrc }, groups, notifications: { push, marketing } }` |
| `updateNotificationSetting(key, enabled)` | `PUT /customer/notifications/{key}` | `{ enabled }` | `{ key, enabled }` |
| `getProfileForm()` / `updateProfile(form)` | `GET` / `PATCH /customer/profile` | `{ name, nickname }` (`phone` 변경은 OTP 재인증 필요 → §13-7, 지금은 무시) | `{ name, nickname, phone: "12345678" }` |
| `getAddresses()` / `addAddress()` / `removeAddress(id)` | `GET` / `POST /customer/addresses` / `DELETE /customer/addresses/{id}` | `{ label, roadAddress, detailAddress }` | `[{ id, label, roadAddress, detailAddress }]` / 1건 / `{ id }` (기본 주소 삭제 시 다음 주소가 기본) |
| `getPaymentMethods()` | `GET /customer/payment-methods` | — | `[{ id, label, isDefault }]` — `payment_methods` 테이블에서. PG 연동 전에는 행이 없어 `[]` (구조만, §6.4) |
| `getFaq()` · `getNotices()` · `getNotice(id)` · `submitInquiry()` | §5.5 | | |

### 5.3 매니저 (`/manager/**`, `ROLE_MANAGER`)
| 앱 함수 | 메서드 · 경로 | 요청 | 응답 (= mock 모양) |
|---|---|---|---|
| `getHome()` | `GET /manager/home` | — | `{ manager: { name }, today: { dateLabel: "9월 15일 (화)", count }, inProgress: null \| { title, elapsed, since, jobId }, newCalls: null \| { count, expiry: "4:32 후 만료", summary }, remaining: [ScheduleItem] }` — §6.6 |
| `getCalls()` | `GET /manager/offers` | — | `{ tabBadge, calls: [{ id, expiry, expiresAt, title, location, pay, eta, note }], availability: { status, detail } }` — pending 만, 만료 임박순. `expiresAt`(ISO) 은 앱이 카운트다운을 그리고 싶을 때 쓴다(지금 화면은 `expiry` 만 씀) |
| `getCall(id)` | `GET /manager/offers/{id}` | — | CallItem + `customerRequest` |
| `getRejectReasons()` / `getPauseReasons()` | `GET /catalog/reasons?kind=reject\|pause` | — | `string[]` |
| `rejectCall(id, reason)` | `POST /manager/offers/{id}/reject` | `{ reason }` | `{ id, reason }` |
| (CallCard 수락 — 지금은 `navigate` 만) **`acceptCall(id)` 새 함수** | `POST /manager/offers/{id}/accept` | — | `{ jobId }` — 앱은 `/job/{jobId}` 로. `OFFER_EXPIRED`/`OFFER_TAKEN` 이면 목록으로 |
| `getJob(id)` | `GET /manager/jobs/{id}` | — | `{ id, status, startLabel: "10:00 시작", address: [2줄], info: [{label,value}×3], pay, scope[], requests[] }` |
| `getJobContact(id)` | `GET /manager/jobs/{id}/contact` | — | `{ phone: "010-1234-5678", mapQuery }` — 배정된 매니저에게만, 작업 당일 ±1일에만(그 외 404) |
| `getEquipmentStatus()` | `GET /manager/device/status` | — | `[{ label: "카메라", value: "연결됨"\|"연결 안 됨", status }, { "배터리", "82%"\|"확인 필요" }, { "네트워크", "양호"\|"확인 필요" }, { "실시간 전송", "준비 완료"\|"대기 중" }]` — 카메라·전송은 `manager_devices.stream_status`(Ingress 웹훅), 배터리·네트워크는 `last_report`(기기 API 없으면 "확인 필요", status `issue` 아님 `unknown`→앱은 `issue` 로 표시) |
| `getActiveJob()` | `GET /manager/jobs/active` | — | `{ id, title, elapsed, startedAt, pausedSeconds, since, equipment[], requests[], checklist: [{ group, items: [{id,label,done}] }] }` · 없으면 404 |
| `setChecklistItem(jobId, itemId, done)` | `PUT /manager/jobs/{jobId}/checklist/{itemId}` | `{ done }` | `{ jobId, itemId, done }` |
| `startJob(id)` **새 함수** (JobPreparing "청소 시작") | `POST /manager/jobs/{id}/start` | — | `{ jobId, startedAt }` — `scheduled` 이고 작업 날짜가 오늘일 때만. 서버 상태에 `preparing` 은 없다(준비 화면은 앱 화면일 뿐). 라이브는 별도(§5.4) |
| `pauseJob(jobId, reason)` | `POST /manager/jobs/{id}/pause` | `{ reason }` | `{ jobId, reason, since: "14:32부터 중단됨" }` — **선행 조건: `live_sessions.muted=true` 또는 카메라 미연결**. 영상이 나가는 중이면 `INVALID_STATE`("영상을 먼저 중단해주세요") → 순서를 서버가 강제한다. 멱등 |
| `getPausedJob()` | `GET /manager/jobs/active/pause` | — | `{ since, note }` |
| `resumeJob(jobId)` | `POST /manager/jobs/{id}/resume` | — | `{ jobId }` — 멱등. 앱 순서: `resumeJob` → `live.resume()`(영상이 나가는데 상태가 일시정지인 순간이 없게). `live.resume` 이 실패하면 상태는 진행 중, 영상은 막힌 채 → 앱이 문구로 재시도, `LiveReconciler` 도 `in_progress ∧ muted ∧ mute_pending=false` 를 1분마다 unmute 재시도 |
| `completeJob(jobId)` | `POST /manager/jobs/{id}/complete` | — | `{ jobId }` (정산 생성, §6.5) |
| `getCompletedJob()` | `GET /manager/jobs/last-completed` | — | `{ title, subtitle, summary: [4], payout: { label, amount, note } }` |
| `getSettlement()` | `GET /manager/settlements` | — | `{ monthly: { label, amount }, stats: [3], items: [{ id, date, status, title, amount }] }` |
| `getSettlementDetail(id)` | `GET /manager/settlements/{id}` | — | `{ id, title, subtitle, breakdown: [3], final: { label, value } }` |
| `getSchedule()` | `GET /manager/schedule?from=&to=` | — | `{ items: [오늘], week: [{ dateKey, label, items }] }` — 기본 오늘~+6일 |
| `getAvailability()` | `GET /manager/availability` | — | `{ defaultHours: [{ day: "월", hours: "09:00 - 18:00", off? }×7], unavailable: [{ id, date: "9월 18일", label, time }] }` |
| `updateDefaultHours(rows)` | `PUT /manager/availability/hours` | `[{ weekday: 0..6, start: "09:00", end: "18:00", off }]` | `defaultHours` 모양 |
| `getRepeatOptions()` | 로컬 유지 (`REPEAT_OPTIONS`) | | 앱은 `['반복 없음','매주','매월']` ↔ `none\|weekly\|monthly` 로 바꿔 보낸다 |
| `addUnavailable({...})` | `POST /manager/availability/unavailable` | `{ date, allDay, start, end, repeat, reason }` | `{ id, date, label, time, repeat }` |
| `getProfile()` | `GET /manager/profile` | — | `{ name: "김○○ 매니저", stats: [{평점},{완료 청소}], menu: PROFILE.menu 그대로 }` |
| `getProfileDetail(key)` | `GET /manager/profile/{key}` (info·area·identity·education·account) | — | `{ title, rows? , chips?, note? }` |
| `getEquipmentDevice()` | `GET /manager/device` | — | `{ name, connection: "정상 연결"\|"연결 안 됨", status: [4], stream: { url, keyMasked: "****-**-1234" } }` — 기기 설정용. 기기 미등록이면 404(운영자가 등록, §13-8) |
| `getManagerNotifications()` / `updateManagerNotification()` | `GET /manager/notifications` / `PUT /manager/notifications/{key}` | `{ enabled }` | `{ rows, values }` / `{ key, enabled }` |
| `getSupportCopy()` | 로컬 유지 | | |
| `submitSupportInquiry()` · `getManagerNotices()` · `getManagerNotice()` | §5.5 | | |

### 5.4 라이브 (`live/`) — 앱의 `liveClient.js` 만 부른다. 영상은 바디캠 → LiveKit Ingress
| 호출 | 메서드 · 경로 | 요청 | 응답 |
|---|---|---|---|
| 매니저 `start(jobId)` | `POST /live/sessions/{bookingId}/start` | — | `{ roomName, cameraOnline: bool, cameraBusy: bool, preview: { url, token } }` — 오늘의 `scheduled\|in_progress` 인 자기 작업만 (Job/Preparing 화면에서 미리 불러 "카메라 연결됨" 을 확인하고, 청소 시작 후에도 같은 세션). `cameraBusy=true` 면 바디캠이 다른 방(대기 방)에 송출 중이라 앱이 "바디캠을 껐다가 다시 켜주세요" 를 보여준다(§8.1). 서버가 그 매니저의 Ingress 를 이 방에 묶고(§8) Egress 녹화를 시작한다. `preview` 는 매니저가 자기 바디캠 화면을 보는 viewer 토큰(TTL 6시간) |
| 매니저 (화면이 3초마다) | `GET /live/sessions/{bookingId}` | — | `{ status: created\|waiting_camera\|live\|paused\|ended, cameraOnline, muted, viewers, startedAt }` — 바디캠이 붙으면 `live` |
| 매니저 `pause()` / `resume()` | `POST /live/sessions/{bookingId}/pause` · `/resume` | — | `{ status, muted: true }` — **영상만 책임진다**(작업 상태는 `job/*/pause`). 서버가 `mutePublishedTrack` 을 부른 뒤 `getParticipant` 로 트랙 `muted` 를 최대 3초 폴링해 **확인된 뒤에만 200**. 확인 못 하면 `mute_pending=true` 로 남기고 `LIVE_PAUSE_UNCONFIRMED`(502) → 앱은 일시정지 화면으로 가지 않는다. 카메라가 안 붙어 있으면(`waiting_camera`) 트랙이 없으므로 `mute_pending=true` 로 즉시 200(막을 영상이 없다). 둘 다 멱등(이미 그 상태면 200) |
| 매니저 `stop()` | `POST /live/sessions/{bookingId}/stop` | — | `{ ok }` — Egress 정지, Ingress 를 방에서 분리(다음 작업에서 재사용), 방 종료 |
| 고객 `join(bookingId)` | `POST /live/sessions/{bookingId}/token` | `{ role: "viewer" }` | `{ url, token, roomName }` — 자기 예약이 `in_progress\|paused` 일 때만(`LIVE_NOT_AVAILABLE`). TTL 2시간, `canPublish=false` |
| 고객 녹화본 | `GET /live/sessions/{bookingId}/recording` | — | `{ url, durationSeconds }` (서명 URL 6시간, `RECORDING_URL_TTL_SECONDS`) · 준비 전 `RECORDING_NOT_READY` |
| 바디캠 기기 | (LiveKit Ingress RTMP 주소 — API 아님) | 기기에 `stream_url` + `stream_key` 한 번 설정 | — |
| LiveKit → 서버 | `POST /live/webhooks/livekit` | LiveKit 웹훅(서명 검증) | 204 — `ingress_started/ended` → `manager_devices.stream_status`·`live_sessions.camera_online_at`·`status=live`, `egress_ended` → `recording_status=ready`, `participant_joined/left`(identity `customer-`) → `viewer_count`, `room_finished` → `ended` |

### 5.5 지원 (`support/`, 두 앱)
| 앱 함수 | 메서드 · 경로 | 응답 |
|---|---|---|
| `getFaq()` | `GET /support/faq?audience=` | `[{ id, question, answer }]` |
| `getNotices()` / `getManagerNotices()` | `GET /support/notices?audience=` | `[{ id, title, date: "2026.09.10" }]` (audience + all, 최신순) |
| `getNotice(id)` / `getManagerNotice(id)` | `GET /support/notices/{id}` | `{ id, title, date, body }` |
| `submitInquiry({ content })` / `submitSupportInquiry()` | `POST /support/inquiries` | `{ id }` — 공백만이면 `VALIDATION`, 2,000자 상한 |
| `push.registerToken` **새 함수** | `PUT /push/token` `{ platform, token }` | `{ ok }` — 앱 시작·로그인 후 (B6) |

---

## 6. 도메인 규칙 (`backend/docs/domain.md`. 구현자는 이 절을 그대로 코드로 옮긴다)

### 6.1 예약 상태기계 (`bookings.status`, `Booking` 엔티티의 메서드만 바꾼다)
```
requested ──(매칭 시작, 생성 직후)──▶ assigning ──(매니저 수락)──▶ scheduled ──(매니저 "청소 시작", 당일)──▶ in_progress ◀──resume── paused
   │                                     │                            │                                       │ pause ▲
   └──────────── cancel ─────────────────┴──────── cancel ────────────┘                                       └───────┘
                                                                                                              │ complete
                                                                                                              ▼
                                                                                                            done
```
`preparing` 은 **서버 상태가 아니다**. Job/Preparing 은 앱 화면이고, 서버는 `scheduled` 인 채로 라이브 세션(§8)만 미리 연다. (이전 초안의 `preparing` 상태는 화면에서 뒤로 가면 되돌릴 방법이 없어 삭제했다.)

| 전이 | 메서드 | 누가 | 조건 | 부수 효과 |
|---|---|---|---|---|
| (생성) → `requested` → `assigning` | `BookingService.create` | 고객 | 날짜 ≥ 내일, 평일, `time` 이 `time_slots` 안, 무료 옵션 ≤ `free_option_limit`, 모든 option/notice key 가 카탈로그에 있음 | 금액 계산(§6.4) · 정기청소면 `booking_series` 생성(§6.8) · `OfferService.dispatch(booking)` · 이력 기록 |
| `assigning` → `scheduled` | `booking.assign(managerId)` | 매니저(offer accept) | 수락한 offer 가 `pending` 이고 만료 전 | 같은 round 의 다른 pending offer 전부 `cancelled` · 체크리스트 템플릿 복사 → `job_checklist_items` · `live_sessions` **upsert**(없으면 생성, 있으면 `created` 로 리셋) · 고객 푸시 "매니저가 배정됐어요" |
| `scheduled` → `in_progress` | `booking.start()` | 매니저 | 작업 날짜가 오늘(Asia/Seoul) · 그 매니저에게 다른 `in_progress/paused` 가 없음 (부분 유니크 인덱스가 최종 방어) | `started_at=now` · 라이브 세션은 `live/start` 가 별도로 (`waiting_camera` → 바디캠이 붙으면 `live` + 고객 푸시, §8.1) |
| `in_progress` → `paused` | `booking.pause(reason)` | 매니저 | reason 이 `catalog_reasons(kind=pause)` 안 | `job_pauses` insert (라이브 mute 는 `live/pause`, §8.1) |
| `paused` → `in_progress` | `booking.resume()` | 매니저 | — | 마지막 `job_pauses.resumed_at=now` |
| `in_progress`/`paused` → `done` | `booking.complete()` | 매니저 | — | `ended_at=now` · 열린 pause 닫기 · `LiveService.stop`(Egress 정지·방 삭제, §8.1) · 정산 생성(§6.5) · `manager_profiles.completed_count+1` · 고객 푸시 |
| `requested/assigning/scheduled` → `cancelled` | `booking.cancel(reason, actor)` | 고객 | **`starts_at − now ≥ cancel_deadline_hours(24)`** (하나의 규칙. 내일 14시 예약은 오늘 14시까지) 아니면 `BOOKING_NOT_CANCELLABLE` | pending offer `cancelled` · 배정된 매니저 푸시 · `wholeSeries` 면 시리즈와 미래 예약 전부 취소 |
| 일정 변경 (`schedule`) | `booking.reschedule(date,time)` | 고객 | `requested/assigning/scheduled` 이고 같은 규칙(`starts_at − now ≥ 24h`), 새 일정도 규칙(내일 이후·평일·슬롯) 통과 | `starts_at/ends_at` 재계산 → `scheduled` 였으면 → `assigning` 으로 되돌리며 **기존 `accepted` 오퍼를 `superseded` 로**, 남은 `pending` 은 `cancelled`, `manager_id/assigned_at` 을 비우고 체크리스트를 지우고, `dispatch_round+1` → **기존 매니저에게 새 round 의 우선 오퍼 10분**(수락하면 다시 `assign`, 거절·만료면 일반 매칭). `live_sessions` 는 **PK 가 booking_id 이므로 재생성하지 않고 `status=created` 로 리셋**(`assign` 은 항상 upsert) · `BookingRescheduled` 이벤트(기존 매니저 푸시) · 이력 기록 |
| 그 밖의 전이 | — | — | `INVALID_STATE` | |

Reservations 화면 매핑(서버가 만든다): `tab` = `done`→`done`, `cancelled`→`cancelled`, 나머지 `upcoming`. `status` = `assigning/requested`→`assigning`(매니저 배정 중), `scheduled`→`scheduled`(방문 예정), `in_progress/paused`→`inProgress`, `done`, `cancelled`. `rows` = `[일정: KoreanFormat.dateTimeLong, 주소: road_address, 매니저: 매니저 이름+" 매니저" | "배정 후 알려드려요" | (취소이고 미배정) "배정 전 취소"]`. 오늘 진행 중이면 일정 값은 `"오늘 " + 오전/오후 h시`. 정기청소 예약은 `title` 뒤에 `" · 정기"` 를 붙이지 않는다(Figma 문구 유지) — 대신 `series: true` 필드를 추가로 내려 앱 취소 시트가 선택지를 보여준다.

### 6.2 매칭 (`offer/OfferService`) — "콜"
1. `dispatch(booking)`: 작업 구간 = `[service_time, service_time + duration_minutes)`. 후보 = `manager_profiles.approved=true` ∧ **`areas` 에 `bookings.sido || ' ' || bookings.sigungu` 포함**(sigungu 가 null 이면 `manager_profiles.sido = bookings.sido` — 주소 검색 API 연동 전 임시) ∧ 그 요일 `manager_hours.off=false` 이고 **시작 시각이 `[start,end]` 안**(종료 시각에 시작하는 마지막 슬롯도 포함, 끝나는 시각은 근무 종료를 넘어도 된다 — 매니저가 콜을 보고 수락할지 정한다, 근무 불가·겹치는 작업은 작업 전체로 본다, 사용자 결정 2026-09-27) ∧ `manager_unavailable`(repeat=weekly 는 같은 요일, monthly 는 같은 일자로 펼쳐서) 과 겹치지 않음 ∧ 그 매니저의 같은 날 `scheduled/in_progress/paused` 예약 구간과 겹치지 않음. 후보 0명이면 `assigning` 으로 두고 운영자 알림(로그 WARN + §13-8). **`exclude` 인자**로 재매칭 때 이미 거절·만료한 매니저를 뺀다.
2. 후보 **전원**에게 `offers(pending, expires_at = now + offer_ttl_minutes(기본 30))`, `pay_amount = bookings.total_amount − bookings.platform_fee`(스냅샷, §6.4). 저장 후 `OffersDispatched` 이벤트 → 커밋 뒤 푸시 "새 청소 요청". **우선 오퍼**(`dispatchPreferred(booking, managerId, ttl=10분)`): 일정 변경·정기 회차는 이 매니저 한 명에게만 먼저 보내고, 거절·만료 시 `dispatch(booking, exclude=[managerId])`. 일정 변경의 우선 오퍼는 콜 푸시를 따로 보내지 않는다 — '예약 일정이 변경됐어요 … 새 콜을 확인해주세요' 가 안내한다(문제 목록 15번, 2026-09-28).
3. `accept(offerId, managerId)`: 한 트랜잭션에서 `select … for update` 로 **manager_profiles(그 매니저 행) → booking → offer** 순서로 잠근다(잠금 순서 고정 — 데드락 방지. 매니저 행을 먼저 잠그므로 같은 매니저의 동시 수락은 직렬화되고, 겹침 검사가 그 뒤에 돈다). 최종 방어는 `bookings_manager_no_overlap` 제약(§4) — 위반 시 `OFFER_TAKEN`. 그 다음 — offer 가 `pending` 이 아니면 `OFFER_TAKEN` / `expires_at < now` 면 `OFFER_EXPIRED` / booking 이 `assigning` 이 아니면(취소됨 포함) `OFFER_EXPIRED` / 그 매니저가 그 시간에 근무하지 않으면(콜을 받은 뒤 휴무·근무 불가를 넣었거나 근무시간을 바꿈 — 1번 후보와 같은 기준 `AvailabilityService.isAvailable`) `INVALID_STATE` "근무 일정과 맞지 않는 시간이라 수락할 수 없어요. 근무 일정 관리에서 확인해주세요"(문제 목록 7번, 사용자 결정 ① 2026-09-28) / 그 매니저의 겹치는 작업이 그 사이 생겼으면 `OFFER_TAKEN`. 통과하면 `booking.assign(managerId)`(체크리스트 복사·live_sessions 생성 포함), 나머지 pending → `cancelled`, `BookingAssigned` 이벤트.
4. `reject(offerId, reason)`: `rejected`. 모두 거절·만료되면 `redispatch`(같은 후보 제외, 1회) → 그래도 없으면 1번의 운영자 알림. 이미 닫힌 콜의 거절: 내가 거절한 콜은 200(저장된 사유, 멱등), 내가 수락한 콜은 `INVALID_STATE`, 다른 이유로 닫힌 콜(다른 매니저 수락·고객 취소·일정 변경·방문 취소)은 `OFFER_TAKEN`, 만료됐거나 만료 시각이 지난 콜은 `OFFER_EXPIRED` — 앱이 '이미 마감된 요청이에요' 를 보인다(문제 목록 10번, 사용자 결정 B 2026-09-28).
   **밤에는 새 콜을 아침으로 미룬다**(문제 목록 2번, 사용자 결정 ③ 2026-09-28): 밤 = 서울 08시 전·21시부터(`MatchingRetryWindow.isNight` — 자동 재매칭 시간대 08:05~20:05 와 같은 상수). 밤에 생긴 새 콜 보내기(예약 생성·일정 변경·방문 취소 뒤 재매칭·거절·만료 뒤 다음 회차·정기 회차 우선 콜 — `dispatch`·`dispatchPreferred`·`onRescheduled` 로 가는 자동 경로 전부)는 콜을 만들지 않고 예약에 보류 표시(`bookings.dispatch_held_at`·`dispatch_held_manager_id`·`dispatch_held_rescheduled`, V046)만 남기고, 아침 첫 실행(`MatchingRetry` 08:05)의 `releaseHeldDispatches` 가 보낸다 — 우선 콜(정기 다음 회차·일정 변경의 원래 매니저)은 아침에도 그 매니저에게 먼저 우선 콜로(정기 직전 매니저는 밤사이 그 정기청소를 취소했는지 다시 본다), 아니면 지금 매칭 구간에서 이미 받은 매니저를 빼고 후보 전원에게. 일정 변경 전 매니저 안내('예약 일정이 변경됐어요')도 밤에는 적지 않고 아침에 우선 콜과 함께 적는다(밤사이 두 번 바꿔도 한 번). **급한 예약**(미루면 약속까지 3시간(`MIN_LEAD`)보다 적게 남음, 예: 밤 22시에 만든 다음 날 10시 예약)은 기다리지 않고 지금 보내고(`holdUntilMorning`), 미룬 뒤 일정 변경으로 급해지면 그때 보낸다. 운영자 재매칭(`redispatchByAdmin`)은 미루지 않는다(보류를 지운다). 예약이 취소되면 보류도 지운다. 보류 중 고객 카드는 배정 전 문구('배정 후 알려드려요') 그대로이고 매칭 실패 표시도 없다. 밤에 회차가 모두 닫혀 최대 회차에 닿았으면 매칭 실패 안내는 그대로 간다. 매니저 콜 카드의 '근무 시간 외' 문구는 바꾸지 않는다.
5. `@Scheduled(fixedDelay=30s)` `expireOffers()`: `pending ∧ expires_at < now` → `expired`, 그 예약에 pending 이 0 이면 4번과 같은 재시도. 이어서 정지·승인 취소·탈퇴로 수락할 수 없게 된 매니저의 `pending` → `cancelled`(`closeBlockedOffers`) 후 같은 재시도 — 그 콜이 만료될 때까지 '대기' 로 세어져 재매칭이 늦어지지 않게. 정지·미승인 매니저의 콜 탭·홈에는 콜 카드를 싣지 않는다(`pendingFor` 가 빈 목록 — 콜 탭은 정지·승인 대기 안내 카드, 문제 목록 6번).
6. 표시: `expiry` = `KoreanFormat.untilExpiry(expires_at)` → `"4:32 후 만료"`(m:ss), 만료 후는 목록에서 빠진다. `title` = `"9월 18일 목요일 오후 2:00"`, `location` = `"서울 강남구 역삼동 · 아파트 기본청소"`(주소 앞 3어절 · 카테고리 라벨), `pay` = `"85,000원"`, `eta` = 거리 계산 전까지 **고정 문구 "이동 시간은 배정 후 안내"** (§13-9), `note` = 첫 notice 라벨 (`"특이사항: 반려동물이 있어요"`), `customerRequest` = `request`.

### 6.3 작업 화면 값 (`job/JobMapper`)
- `getJob`: `startLabel = "HH:mm 시작"`, `address = [road_address, detail_address]`, `info = [고객: 성 + "○○"(마스킹), 청소 유형: 카테고리 라벨, 예상 소요: bookings.duration_minutes / 60 + "시간"(예약 때 고정한 값 — 콜 카드·일정 시간대와 같게, 자체 점검 #26)]`, `pay = offer.pay_amount`, `scope = free_options+paid_options 라벨`, `requests = notices 라벨 + request`.
- `getActiveJob`: 매니저의 `in_progress/paused` 1건. `elapsed = HH:mm:ss(now − started_at − 일시정지 합)`(앱이 1초마다 다시 부르지 않도록 `startedAt`·`pausedSeconds` 도 같이 내려 앱 타이머가 계산. 화면은 이미 `elapsed` 를 쓰므로 초기값으로 채워 준다), `since = "14:02부터 진행 · 실시간 촬영 중"`, `equipment` = 3행(카메라 = `stream_status` online→"정상"/offline→"연결 안 됨"(issue), 배터리·네트워크 = `last_report` 있으면 값, 없으면 "확인 필요").
- `getCompletedJob`: 마지막 `done` 1건. `summary = [청소 시간 "14:02 - 17:25", 총 소요 "3시간 23분", 체크리스트 "9개 중 9개 완료"(전부면 success), 촬영 기록 (recording_status ready→"정상 업로드" success / recording→"업로드 중" default / failed→"업로드 실패" issue)]`, `payout = { "예상 정산", final_amount, "정산은 다음 정산일에 지급됩니다." }`.

### 6.4 가격 (`catalog/PricingService`, 숫자는 전부 `app_settings`/`catalog_prices`)
`base = catalog_prices[category][frequency]` · `options = Σ catalog_options.price (paid 만)` · `total = base + options` · `platform_fee = round(total × platform_fee_rate)`(`app_settings.platform_fee_rate`, 기본 0.10) · `manager_pay = total − platform_fee`. 예약 생성 시 `bookings.base_amount/options_amount/total_amount/platform_fee/duration_minutes` 에 **고정**(카탈로그·수수료율이 바뀌어도 과거 예약·오퍼·정산은 안 바뀐다). 오퍼의 `pay_amount` 와 정산의 `final_amount` 는 반드시 이 스냅샷에서 계산한다(같은 예약에서 다른 숫자가 나올 수 없게). **결제는 구조만**(사용자 결정): `payment/PaymentGateway` 인터페이스 `{ registerBillingKey(customerId, providerPayload) → PaymentMethod; charge(booking, paymentMethod) → Payment; refund(payment, amount) }` 와 `NoopPaymentGateway`(항상 `PENDING`, 로그만). `BookingService.create` 는 `paymentGateway.charge` 를 **호출하지 않고** `payment_status='unpaid'` 로 둔다 — PG 연동 시 `booking.complete()` 뒤(또는 예약 시, §13-5) 에 호출 지점 한 곳만 추가하면 된다. 그 자리는 `BookingService` 에 `// PAYMENT: PG 연동 시 여기서 paymentGateway.charge(...)` 주석으로 표시.

### 6.5 정산 (`settlement/`)
`booking.complete()` 와 같은 트랜잭션에서 `SettlementService.createFor(booking)` → `settlements(pending, service_amount=bookings.total_amount, platform_fee=bookings.platform_fee, other_fee=0, final=total−platform_fee)`. `booking_id unique` 라 중복 생성이 DB 에서 막힌다. `status=complete` 는 운영자가 지급 후 바꾼다(관리 API 는 범위 밖 → SQL/대시보드, §13-8). 화면 값: `monthly.amount` = 이번 달(Seoul) `final_amount` 합, `stats` = [완료 업무 = 이번 달 done 수, 정산 예정 = pending 합, 정산 완료 = complete 합(success)], `items` = 최근 50건 `{ date: "9월 14일", status: settlement.status, title: 구 + " " + 카테고리 짧은 라벨, amount }`, detail `breakdown = [서비스 금액, 플랫폼 수수료 -x, 기타 -y]`, `final`.

### 6.6 매니저 홈 (`managerhome/ManagerHomeService` — 읽기 조합만)
`today.dateLabel = "9월 15일 (화)"`, `today.count` = 오늘 `scheduled+in_progress+paused+done` 수, `inProgress` = 활성 작업(§6.3, `title="청소 진행 중"`, `since="14:02부터 진행 · 강남구 역삼동"`), `newCalls` = pending offer 가 있으면 `{ count, expiry: 가장 이른 만료, summary: "가장 가까운 콜 · 9월 18일 오후 2:00 · 85,000원" }`, `remaining` = 오늘 남은 `scheduled` 를 시각순 `{ id, time: "16:30 - 18:30"(시작 + duration), status: "scheduled", customer: "박○○ 고객 · 해운대구 우동", service: 카테고리 라벨 }`. `availability.status` = 지금이 근무시간 안이면 "콜 수신 중" 아니면 "근무 시간 외", `detail = "오늘 09:00 - 18:00 근무 설정됨" | "오늘 휴무"`.

### 6.8 정기청소 시리즈 (`booking/BookingSeriesService`) — **매월 1회 × `series_term_months`회** (결정 2026-09-22, 그 전에는 '한 달 동안 매주')
- 정기청소 = **첫 방문일부터 매월 같은 날짜·같은 시각에 1회씩**, `app_settings.series_term_months` 회(기본 6 = 6개월 6회). 없는 날짜(31일 등)는 그 달 마지막 날로, **주말이면 다음 평일**로 민다. `frequency=recurring` 예약 생성 시 `booking_series(active, term_start=첫 방문일, term_end=마지막 회차 날짜, weekday=첫 방문 요일 기록용, visits_planned)` 를 만들고, **그 기간의 회차 전부를 즉시 `bookings(requested, series_id)` 로 생성**한다(고객 "내 예약" 에 바로 보인다). 요금은 회차마다 개별(§6.4).
- 매칭 시점: 첫 회차는 즉시 `dispatch`. 나머지는 `SeriesDispatcher`(§2.5, 매일 09:00 — 2026-09-28 전 03:00)가 **방문 7일 전**에 매칭하되, 직전 회차를 수행한 매니저에게 **우선 오퍼 10분** → 거절·만료 시 일반 매칭. 밤에 생긴 매칭은 §6.2 의 밤 규칙대로 아침 08:05 에(문제 목록 2번).
- 종료: `term_end` 가 지나면 시리즈 `ended`. 자동 연장 없음. `term_end − 7일` 에 `SeriesEnding` 이벤트 → 고객 푸시 "정기청소가 곧 끝나요. 다음 달도 예약해 보세요"(연장은 새 시리즈 생성 = 같은 예약 플로우).
- 취소: 회차 하나 = 그 예약만(기한 규칙 §6.1). `wholeSeries=true` = 시리즈 `cancelled` + 아직 시작 전인(`requested/assigning/scheduled`, 기한 안) 미래 회차 전부 취소. 기한(방문 N시간 전, N = `app_settings.cancel_deadline_hours`, 기본 24)이 지난 회차는 남고 앱 시트에 "N시간 안에 시작하는 방문은 취소할 수 없어요" 문구로 알린다(N 은 예약 목록 응답의 `changeDeadlineHours` — 고정 글자 '24시간' 이었다, 문제 목록 25번).
- 회원 탈퇴는 `active` 시리즈가 있으면 `INVALID_STATE`.
- 앱 변경: 예약 취소 시트에 "이번 회차만 / 정기청소 전체" 라디오(§9.2-5).

### 6.9 멱등 처리 알고리즘 (`common/web/Idempotent.run`, 예약 생성·콜 수락·작업 시작/완료·문의에 적용)
```
run(key, userId, requestHash, action):           -- 전체가 하나의 DB 트랜잭션 (REQUIRES_NEW 아님, 업무와 같은 tx)
  insert idempotency_keys(key, userId, requestHash, 'processing')
    on conflict (key, userId) do nothing returning *
  if inserted:                                   -- 처음 온 요청
    result = action()                            -- 업무 저장 (같은 tx)
    update idempotency_keys set status='done', response_status, response_body   -- 같은 tx
    commit                                       -- 업무 + 키가 원자적으로 저장된다
    return result
  else: row = select … for update              -- 두 번째 이후
    if row.request_hash != requestHash: 422 IDEMPOTENCY_MISMATCH
    if row.status == 'processing': 409 IN_PROGRESS   -- 첫 요청이 아직 커밋 전
    return row.response_status, row.response_body
```
- **커밋 전에 서버가 죽으면** 업무 저장과 키가 함께 롤백된다 → 재시도가 처음처럼 실행된다(중복 없음). **커밋 뒤에 죽으면** 키가 `done` 으로 남아 있다 → 재시도는 저장된 응답을 받는다(중복 없음). "저장 성공 → 응답 기록 전 종료" 는 둘이 한 트랜잭션이라 생기지 않는다.
- 동시에 같은 키가 두 번 오면 `insert … on conflict do nothing` 이 하나만 통과시키고 나머지는 `IN_PROGRESS`.
- 키 없이 온 요청은 멱등 처리 없이 그냥 실행한다(웹 개발 편의). 앱은 항상 보낸다(§9.4).

### 6.7 한국어 표시 (`common/format/KoreanFormat` — 전부 정적 메서드, 단위 테스트 필수)
| 메서드 | 입력 → 출력 | 쓰는 곳 |
|---|---|---|
| `dateLabel(LocalDate)` | `2026-09-15` → `"9월 15일 (화)"` | 매니저 홈·정산 |
| `dateLong(LocalDate)` | → `"2026년 9월 15일"` | |
| `dateTimeLong(LocalDate, LocalTime)` | → `"2026년 11월 1일 (일) 오전 9시"` | Reservations 일정 |
| `dateTimeCall(LocalDate, LocalTime)` | → `"9월 18일 목요일 오후 2:00"` | 콜 카드 |
| `timeKo(LocalTime)` | `14:00` → `"오후 2시"`, `14:30` → `"오후 2시 30분"` | (앱 `formatTimeKo` 와 같은 규칙) |
| `timeRange(LocalTime, LocalTime)` | → `"09:00 - 18:00"` | |
| `hms(Duration)` | → `"01:23:42"` | elapsed |
| `durationKo(Duration)` | → `"3시간 23분"` | |
| `won(long)` | `85000` → `"85,000원"`, 옵션 가격은 `"+3,000원"` (`wonSigned`) | |
| `untilExpiry(Instant)` | → `"4:32 후 만료"` (음수면 `"만료됨"`) | |
| `sinceLabel(Instant, String)` | → `"14:32부터 중단됨"` | |
| `phoneDisplay(String e164)` | `+821012345678` → `"010-1234-5678"`, `phoneLocal` → `"12345678"`, `toE164("12345678")` → `+821012345678`(8자리 숫자만 허용) | |
| `maskName(String)` | `"김하늘"` → `"김○○"` | 매니저에게 보이는 고객 이름 |
| `noticeDate(Instant)` | → `"2026.09.10"` | 공지 |
| `liveStartedAt(Instant)` | → `"2025. 09. 11. 14:32"` | 라이브 |
시간대는 항상 `AppClock.zone()` = `Asia/Seoul`. 요일 배열 `["일","월","화","수","목","금","토"]`(앱과 동일).

---

## 7. 인증 상세 (`auth/` + `common/security`)

### 7.1 Supabase Auth 를 쓰는 방식 (OTP 는 Spring)
- 앱 → Spring 만. Spring 이 Supabase Auth REST 를 **service_role 키**로 호출한다 (`common/supabase/SupabaseAuthClient`, Spring `RestClient`). 엔드포인트: `/auth/v1/admin/users`(생성·비밀번호 변경·삭제), `/auth/v1/token?grant_type=password|refresh_token`, `/auth/v1/logout`.
- **휴대폰 인증번호는 Spring 이 만든다** (`auth/OtpService`): 6자리 `SecureRandom`, `phone_verifications` 에 `SHA-256(code + OTP_SALT)` 저장, 5분 만료, 5회 오답 폐기, 검증 성공 시 15분짜리 `verificationToken`. 문자는 `common/sms/SmsSender` 로 보낸다 — 기본 구현 `SolapiSmsSender`(REST, `SMS_API_KEY/SECRET/SENDER`), 업체를 바꾸면 구현체 하나만 추가하고 `application.yml` 의 `sms.provider` 를 바꾼다. local 프로필은 `LoggingSmsSender`(콘솔 출력 — 실기기 테스트 때만 진짜 발송).
- **검증 결과 (2026-09-19, dev 프로젝트 `ckvanmhgepdlxvfhdxes` 에 직접 호출)**: `POST /auth/v1/token?grant_type=password` 에 `phone` 을 주면 Phone provider 가 꺼진 상태에서 **422 `phone_provider_disabled` ("Phone logins are disabled")** 로 거부된다. 같은 요청을 `email` 로 하면 정상 경로(`400 invalid_credentials`). 즉 **휴대폰을 Supabase 의 로그인 식별자로 쓰려면 Phone provider 를 켜야 한다** — 문자는 우리가 Solapi 로 보내므로 Supabase 의 SMS 발송 기능은 쓰지 않는데도 그렇다. 선택지는 §7.1.1.
- Supabase 대시보드 설정(B0, `runbook.md` 에 기록): §7.1.1 에서 고른 방식에 따른 provider 설정, **"Allow new users to sign up" 끔**(현재 `disable_signup:false` — anon key 로 아무나 가입할 수 있으므로 반드시 끈다), JWT 만료 3600s, Refresh token rotation 켬, JWT Signing Keys 비대칭(ES256). **anon key 는 어디에도 배포하지 않는다**.

### 7.1.1 로그인 식별자 — 결정 필요 (§13-25)
| 안 | 방식 | 장점 | 단점 |
|---|---|---|---|
| **A (권장)** | 대시보드에서 **Phone provider 켜기**(SMS 업체 설정은 채우지 않거나 더미). 우리는 `/auth/v1/otp` 를 **부르지 않는다** — 인증번호는 Spring+Solapi. Supabase 는 `phone`+비밀번호 로그인·JWT·refresh 만 | `auth.users.phone` 에 진짜 번호가 들어가 데이터가 정직하다. 계약·코드 변경 없음 | provider 가 켜져 있어 이론상 `/otp` 호출 경로가 열린다(가입 차단 + SMS 미설정이라 실제 발송은 불가). 대시보드가 SMS 자격증명을 요구해 저장이 안 될 수 있음 |
| B | Supabase 식별자를 **합성 이메일**(`+821012345678@ansimclean.invalid`)로. 번호는 우리 `profiles` 가 원본. admin API 로 `email_confirm:true` 생성 → 메일 발송 없음 | Phone provider·SMS 설정 전혀 불필요 | `auth.users` 에 가짜 이메일이 남는다. 나중에 실제 이메일 로그인을 붙이면 이전 필요 |
| C | Supabase Auth 를 쓰지 않고 **Spring 이 BCrypt + 자체 JWT(RS256)** 발급, refresh 토큰 테이블 직접 관리 | 외부 의존 없음, 데이터 정직 | 보안에 민감한 코드(키 관리·회전·폐기)를 직접 만든다. 약 150줄 추가 |
세 안 모두 **앱과 API 계약은 그대로**다(§5.1). **2026-09-19 A 로 결정** — 대시보드는 Twilio 자격증명을 요구했지만 더미 값으로 저장됐고, 가입이 꺼져 있어 `/otp`·`/signup` 은 `signup_disabled` 로 막힌다(문자 발송 경로 없음). Supabase 는 휴대폰+비밀번호 로그인·JWT·refresh 만 맡는다.
- 앱 토큰 보관: `session.js` — `localStorage`(`ansimclean.<app>.auth` = `{ accessToken, refreshToken, expiresAt, user }`). Capacitor 앱에서는 WebView 저장소가 앱 샌드박스 안이라 우선 이대로, 나중에 `@capacitor/preferences` 로 옮기는 항목을 §13-10 에.

### 7.2 Spring 의 JWT 검증 (`SupabaseJwtDecoderConfig`)
- Supabase 프로젝트의 **JWT Signing Keys** 를 비대칭(ES256, 기본값)으로 쓰고 `spring.security.oauth2.resourceserver.jwt.jwk-set-uri = ${SUPABASE_URL}/auth/v1/.well-known/jwks.json`, `issuer = ${SUPABASE_URL}/auth/v1`, `audience = authenticated`. (구형 프로젝트가 HS256 이면 `NimbusJwtDecoder.withSecretKey(SUPABASE_JWT_SECRET)` — `application.yml` 의 `auth.jwt.mode = jwks|secret` 로 고른다.)
- 검증 후 `CurrentUser { id(sub), role, profileComplete }`: role 은 토큰이 아니라 **`profiles.role` 을 DB 에서 읽는다**(요청당 1쿼리, 1분 캐시). `profiles` 가 없거나 `deleted_at` 이 있으면 401. `signup_completed_at` 이 null 이면 `/auth/signup/complete`·`/auth/me`·`/auth/logout`·`/customer/profile/{address,housing}` 만 허용(가입 이어가기), 나머지는 403.
- `SecurityConfig`: `permitAll` = `/auth/phone/**`, **`POST /auth/signup`(정확히 이 경로 — 아직 토큰이 없는 신규 사용자)**, `/auth/login`, `/auth/refresh`, `/terms/**`, `/catalog/**`, `/support/faq`, `/support/notices/**`, `/live/webhooks/**`, `/actuator/health`. `/auth/signup/complete`·`/auth/me`·`/auth/logout`·`/auth/account` = 인증만. `/customer/**` = `ROLE_CUSTOMER`, `/manager/**` = `ROLE_MANAGER`, `/live/**`·`/support/inquiries`·`/push/**` = 인증 + 서비스에서 소유권. CSRF 끔(토큰 인증), 세션 stateless, CORS = `ALLOWED_ORIGINS`(§12).
- 속도 제한: `auth/OtpRateLimiter`(메모리, 번호당 60초 1회·시간당 5회) · `LoginRateLimiter`(번호당 시간당 10회). 단일 인스턴스 전제(§13-11).

### 7.3 가입 흐름 (앱 화면 순서 그대로)
```
고객: SignUp(전화) → TermsConsent(체크 → sessionStorage 초안) → ProfileInfo(휴대폰 "인증하기" → POST /auth/phone/otp → [새] 인증번호 필드 → POST /auth/phone/verify → verificationToken 을 초안에)
      → "다음" = POST /auth/signup(verificationToken, name, nickname, password) → 토큰 저장(profileComplete=false) → ProfileAddress = PUT /customer/profile/address → ProfileHousing = PUT /customer/profile/housing
      → SignUpComplete "지금 시작하기" = POST /auth/signup/complete(agreedTermIds) → profileComplete=true → /home
매니저: SignUpTerms(초안) → SignUpInfo(인증하기 → OTP → verify → "다음" = POST /auth/signup(verificationToken, name, password) → 토큰 저장) → SignUpComplete "시작하기" = POST /auth/signup/complete → /
```
- `RequireAuth` 의 `isSignedIn()` = `accessToken 있음 ∧ user.profileComplete`.
- **가입 재개(신규 가입과 분리)**: `GET /auth/me` 와 로그인 응답의 `user` 에 `signupStep` 을 넣는다 — 서버가 무엇이 있는지 보고 정한다: 고객: 기본 주소 없음 → `"address"`, 주거 정보 없음 → `"housing"`, `signup_completed_at` 없음 → `"complete"` / 매니저: `"complete"` / 완료면 `null`. 앱은 `signupStep` 에 맞는 화면(`/auth/profile-address` · `/auth/profile-housing` · `/auth/complete`, 매니저 `/auth/signup/complete`)으로 보내고 **OTP·기본정보 화면은 다시 거치지 않는다**(토큰이 있으므로).
- 이미 계정이 있는(가입 중이든 완료든) 번호로 `POST /auth/phone/otp` 를 부르면 `PHONE_TAKEN` + 문구 "이미 가입한 번호예요. 로그인해서 이어서 진행해주세요" → 앱은 로그인 화면으로. 로그인은 `profileComplete=false` 여도 성공하고 `signupStep` 으로 이어간다.
- 매니저 `approved=false` 여도 로그인 가능. 콜·일정은 비어 있고 홈 `today.count=0`. 승인 전 안내는 프로필 `신원 인증` 항목 값으로 보인다(`identity: 본인 인증 완료 / 범죄경력 조회 대기 …`).

---

## 8. 라이브 상세 (`live/`) — 바디캠 → LiveKit Ingress → 고객 시청 + Egress 녹화

### 8.0 매니저 휴대폰 송출 (`LIVE_SOURCE=app`, 기본 — 2026-10-10, §13-18 변경)
아래 §8.1~8.3 은 바디캠(`LIVE_SOURCE=ingress`) 구조다. 휴대폰 송출은 같은 방·같은 상태 기계를 쓰고 다른 점만 여기 적는다.
- **카메라 참가자 = 매니저 앱.** 앱이 `camera-<managerId>` identity 로 방에 들어가 후면 카메라 영상 트랙 하나를 publish 한다(영상만, §13-23). 그래서 일시정지 mute·확인 폴링·재조정기·녹화(방 합성)는 바디캠과 같은 코드를 쓴다.
- **start**: 기기 행(`manager_devices`)·Ingress 가 없어도 된다. Ingress 연결·잠금 단계를 건너뛰고, 응답의 `preview` 자리에 `publish: { url, token }` 을 준다 — 권한 roomJoin · canPublish(소스 카메라만) · canSubscribe=false · canPublishData=false, TTL 6시간.
- **카메라 붙음/끊김 = 웹훅 `track_published`/`track_unpublished`**(참가자 `camera-<managerId>`, 영상 트랙). 붙으면 바디캠의 `ingress_started` 와 같은 `cameraAttached`(녹화 시작·첫 연결 푸시), 끊기면 `cameraDetached`. 재조정기는 참가자 목록으로 같은 일을 한다.
- **장비 상태 화면**: 카메라 줄은 앱이 휴대폰 카메라 미리보기 결과로 그린다(서버의 바디캠 줄 대신). 앱은 `GET /live/sessions/{id}` 의 `source` 로 모드를 안다.
- **일시정지 재개(unmute)**: 서버가 클라이언트가 올린 트랙의 mute 를 풀려면 LiveKit 프로젝트의 원격 unmute 허용이 필요할 수 있다 — 실기기 확인 항목. 일시정지 중엔 고객 구독도 막혀 있다(`viewers_blocked`).
- 앱 쪽 구조: `apps/manager/src/live/sources/`(영상 입력 — 지금 `cameraVideoSource.js`) → `liveClient.js`(상태·publish) → `roomConnector.js`(LiveKit). 바디캠·네이티브 카메라가 다시 필요하면 `sources/` 에 파일을 더한다.

### 8.1 구성
- **바디캠 1대 = 매니저 1명 = LiveKit Ingress 1개(영구).** 운영자가 기기를 등록하면(`manager_devices`, §13-20) Spring 이 `IngressServiceClient.createIngress(RTMP_INPUT, name="device-<serial>", roomName="parking-<managerId>", participantIdentity="camera-<managerId>", participantName="<매니저 이름> 바디캠", enableTranscoding=true, reusable=true)` 로 Ingress 를 만들고 `ingress_id`·`stream_url`(`rtmp://<project>.livekit.cloud/x`)·`stream_key` 를 저장한다. 기기에는 이 주소와 키를 **한 번만** 설정한다(매니저 앱 `촬영 장비` 화면에 주소 + 마스킹된 키 + "운영자에게 전체 키 요청" 안내).
- **대기 방 규칙**: 작업이 없을 때 Ingress 는 항상 `parking-<managerId>` 방을 가리킨다. 이 방의 토큰은 아무에게도 발급하지 않고 Egress 도 없다 → 매니저가 실수로 카메라를 켜 두어도 아무도 못 보고 저장되지 않는다.
- **작업 시작(`POST /live/sessions/{id}/start`)**: (1) 오늘의 `scheduled|in_progress` 확인 (2) `IngressServiceClient.updateIngress(ingressId, roomName="booking-<id>")` — 바디캠이 **이미 대기 방으로 송출 중**이면 LiveKit 이 활성 Ingress 의 변경을 거부한다 → `cameraBusy=true` 로 응답하고 앱이 "바디캠을 껐다가 다시 켜주세요" 를 보여준 뒤 3초 폴링으로 재시도(서버는 폴링 요청마다 update 를 다시 시도). LiveKit Cloud 의 `RoomService.moveParticipant`(방 간 이동)가 프로젝트에서 쓸 수 있으면 그것으로 대체해 재시작 없이 옮긴다 — B5 에서 확인 (3) `RoomServiceClient.createRoom(name, emptyTimeout=6h, departureTimeout=6h)`(비어 있어도 사라지지 않게) (4) `live_sessions.status=waiting_camera`, `ingress_id` 저장 (5) 매니저 미리보기 viewer 토큰 반환. **Egress 는 여기서 시작하지 않는다** — 카메라가 붙기 전의 빈 화면을 녹화하지 않도록 `ingress_started` 에서 시작한다(§8.2). 매니저는 바디캠 전원/송출 버튼을 켠다.
- **바디캠이 붙으면** 웹훅 `ingress_started`(room=`booking-<id>`) → `camera_track_sid` 저장, `mute_pending` 이면 즉시 mute, **Egress 가 없으면 시작**(§8.2), `live_sessions.status=live`(단, 작업이 `paused` 면 `paused`), `camera_online_at`, `manager_devices.stream_status=online` → `CameraOnline` 이벤트 → 첫 연결이면 고객 푸시 "청소가 시작됐어요 · 실시간 영상 보기". 모든 웹훅은 `livekit_events.id` 로 중복을 걸러내고, 상태 전이는 "현재 상태에서 허용될 때만" 적용한다(순서가 뒤바뀌어 와도 안전). 앱의 `GET /live/sessions/{id}` 폴링(3초)이 `live` 를 보면 Job/InProgress 의 "실시간 촬영 중" 표시.
- **바디캠이 끊기면**(`ingress_ended` 인데 작업이 아직 `in_progress`) → `status=waiting_camera`, `stream_status=offline`, 매니저 푸시 "바디캠 연결이 끊겼어요". 작업 상태는 그대로(청소는 계속). 카메라 대기가 5분을 넘으면(기준은 끊긴 시각 — 작업을 시작하고 한 번도 붙지 않았으면 세션 시작, 일시정지 뒤 재개했으면 재개 시각) 재조정기(`LiveReconciler`)가 매니저에게 푸시 "바디캠이 아직 연결되지 않았어요 · 5분 넘게 연결되지 않았어요. 바디캠 전원과 네트워크를 확인해주세요" 를 대기 구간마다 한 번(`camera_wait_notified_at`) 보내고 WARN 로그를 남긴다(문제 목록 16번). 작업이 일시정지 중이거나 매니저가 영상을 멈춘 채면 보내지 않는다.
- **일시정지/재개**: `RoomServiceClient.mutePublishedTrack(room, "camera-<managerId>", camera_track_sid, true/false)`. 카메라가 아직 안 붙었으면(`waiting_camera`) `mute_pending=true` 만 두고 붙는 순간 적용. 영상만 송출(결정 §13-23)이라 트랙은 하나다. Egress 는 계속 돌지만 mute 구간은 검은 화면(고객·녹화 모두). **B5 첫날에 ffmpeg 스트림으로 Ingress 참가자 트랙의 서버측 mute 가 실제로 유지되는지 확인**한다. 안 되면 대안: 일시정지 = Egress 정지 + 고객 viewer 참가자 `removeParticipant` + viewer 토큰 거부, 재개 = 새 Egress 시작(녹화본이 여러 파일 → `recording_parts` 로 저장하고 재생 화면은 순서대로 이어 재생). 어느 쪽이든 **일시정지 구간은 고객이 볼 수도, 녹화될 수도 없어야 한다**(사유가 '화장실 이용' 인 경우). `job_pauses` 와 `status=paused` 는 `job/*/pause` 가 기록한다.
- **완료(`stop`)**: `EgressServiceClient.stopEgress(egressId)` → `RoomServiceClient.deleteRoom(room)` → Ingress 잠금 해제 → **바디캠이 이 방에 묶여 있을 때만**(`getIngress` 의 방이 이 방이거나 `pending_room` 이 이 방) `updateIngress(ingressId, roomName="parking-<managerId>")`(활성 상태면 실패해도 무시하고 다음 기기 재시작 때 적용되도록 `manager_devices.pending_room='parking-<managerId>'` 표시 → `ingress_ended` 웹훅에서 적용) → `status=ended`. 같은 매니저의 다른 작업이 진행 중이거나 바디캠이 다른 방에 묶여 있으면(조회 실패 포함) 바디캠은 건드리지 않는다 — 늦게 돈 정리가 다음 작업의 잠금을 풀거나 다음 작업 방의 바디캠을 대기 방으로 빼지 않게 (자체 점검 #1·#51, `LiveService.releaseDevice`).
- 매니저 미리보기: 매니저 앱 `liveClient.start()` 는 **송출하지 않고** `preview` 토큰으로 방에 viewer 로 들어가 `<VideoSurface>` 에 붙인다(Job/InProgress 의 작은 미리보기·Job/Preparing 의 "카메라 연결됨" 확인용). 휴대폰 카메라·마이크 권한은 더 이상 필요 없다 → `촬영 장비 › 카메라 테스트` 와 Info.plist/Manifest 의 CAMERA·RECORD_AUDIO 권한을 제거한다(B5 항목, PROGRESS 기록).
- **바디캠 없이 개발·검증하는 법**: `ffmpeg -re -stream_loop -1 -i sample.mp4 -c:v libx264 -preset veryfast -b:v 2M -c:a aac -f flv rtmp://<ingress-url>/<stream-key>` 또는 OBS 로 노트북이 바디캠 역할을 한다. B5 의 서버·앱·웹훅·녹화·일시정지 검증은 전부 이걸로 끝내고, 실제 기기는 '주소·키 설정 + §8.3 검증 5항목' 만 남는다. 기기 사양 답이 늦어도 B0~B4·B6~B8 은 영향이 없다.

### 8.2 녹화
`ingress_started` 처리 안에서(카메라가 실제로 붙었을 때) `startRoomCompositeEgress(room, EncodedFileOutput(MP4, S3Upload(endpoint=SUPABASE_STORAGE_S3_ENDPOINT, bucket=recordings, key=recordings/<bookingId>.mp4, accessKey/secret=Storage S3 access key, region=ap-northeast-2, forcePathStyle=true)), layout="single-speaker", videoOnly=true)`. Supabase Storage 는 S3 프로토콜을 지원한다(대시보드 › Storage › S3 access keys). `egress_id` 저장, `recording_status=recording`. 카메라가 끊겼다 다시 붙어도 Egress 는 하나(방 합성이라 계속 녹화, 끊긴 구간은 검은 화면). 웹훅 `egress_ended` → `ready`(+`duration_seconds`, `recording_path`) 또는 `failed`. 고객 재생: `GET /live/sessions/{id}/recording` → `SupabaseStorageClient.signedUrl("recordings", path, 21600)`(6시간 — 3~4시간 녹화본을 한 번에 끝까지 봐도 끊기지 않게, `LiveService.RECORDING_URL_TTL_SECONDS`) → 앱 `<video src>` + `currentTime` 으로 15초 탐색. HLS 는 필요해지면 `SegmentedFileOutput` 추가(§13-12). 보존 **완료 후 90일**(`app_settings.recording_retention_days`) 뒤 `@Scheduled` 일 1회 삭제 — 약관 `recording` 문안과 일치시킨다.

### 8.2.1 일시정지의 책임과 부분 실패 (한 곳에만 적는다)
| 단계 | 누가 | 실패하면 |
|---|---|---|
| ① `POST /live/…/pause` — 영상 차단 확인 | `live/LiveService.pause`(mute + 확인 폴링) | 앱은 시트에 "촬영을 중단하지 못했어요. 영상이 계속 전송되고 있으니 다시 시도" 를 보이고 **일시정지 화면으로 가지 않는다**. 서버는 `mute_pending=true` 로 두지만, 작업이 진행 중이면 `LiveReconciler` 는 그 요청을 버리고 영상을 되돌린다(매니저에게 '계속 전송' 으로 안내했으므로). 작업이 일시정지면 재시도해 막는다 |
| ② `POST /manager/jobs/…/pause` — 작업 상태 | `job/JobService.pause`(선행 조건 ①) | ①은 이미 됐으므로 영상은 막혀 있다(안전한 쪽). 서버는 **매니저가 재개할 때까지**(`live/resume` 또는 `jobs/resume`) 막아 둔다 — 바디캠이 끊겼다 다시 붙어도(`live_sessions.video_held`, V045, 2026-09-28 사용자 결정). 앱은 문구 후 재시도(멱등), 시트를 닫으면 `live/resume` 으로 영상을 다시 켠다 |
| ③ 화면 이동 | 앱 | — |
| 재개 ① `POST /manager/jobs/…/resume` | `JobService.resume` | 아무것도 안 바뀜, 재시도 |
| 재개 ② `POST /live/…/resume` | `LiveService.resume`(unmute + 확인) | 상태는 진행 중, 영상은 막힘 → 고객은 "잠시 중단" 화면 유지, 앱 문구 + `LiveReconciler` 재시도 |
원칙: **영상이 나가는데 상태가 '일시정지' 인 순간은 없다**(반대는 잠깐 허용). **매니저가 '멈췄다' 는 답을 받은 영상은 매니저가 재개하기 전에는 다시 나가지 않는다** — 재접속·재조정기·웹훅·Ingress 잠금·고객 상태 문구가 모두 `LiveService.holdsVideo`(작업 일시정지 ∨ `video_held`) 한 기준을 쓴다. `liveClient.pause()/resume()` 은 §5.4 의 live 엔드포인트만 부르고 작업 상태는 부르지 않는다 — 화면(`JobInProgress`·`JobPaused`)이 위 순서대로 둘을 부른다(2차 점검에서 그렇게 고쳤다).

### 8.3 바디캠 요구 사양 (§13-19 — 기기 확정 전 확인할 것)
| 필요 | 이유 | 없으면 |
|---|---|---|
| **RTMP(S) 푸시** (주소 + 스트림 키 설정 가능) | LiveKit Ingress 입력 | WHIP 지원 기기면 `WHIP_INPUT` 으로 동일 구조. **RTSP 만** 되는 기기면 중계 서버(ffmpeg 를 Spring 이 프로세스로 띄워 RTSP→RTMP)가 필요 — 별도 항목 |
| H.264 **영상만**(음성 트랙 없음 또는 끔), 720p 이상, 1.5–3 Mbps | 영상만 송출(결정 §13-23). 기기가 음성을 끄지 못하면 Ingress 는 받되 Egress `videoOnly` + 고객 토큰에서 오디오 구독 불가(`canSubscribe` 는 두고 서버가 오디오 트랙을 `mutePublishedTrack` 로 항상 mute) | 기기 설정에서 조정 |
| LTE/5G 또는 테더링 | 고객 집 Wi-Fi 에 의존하지 않음 | 매니저 휴대폰 핫스팟 |
| (선택) 배터리·네트워크 상태 API 또는 앱 SDK | 장비 화면의 배터리 %·네트워크 | "확인 필요" 표시 (이미 설계에 반영) |
| 연속 4시간 이상 송출 | 작업 길이 | 보조 배터리 |

실기기 검증 항목(B5 완료 기준): 바디캠 송출 → 매니저 미리보기 + 고객 시청 지연 3초 이내 · 일시정지(검은 화면)/재개 · 바디캠 전원 껐다 켜기(waiting_camera → live 복귀) · 완료 후 3분 안에 녹화본 재생 · 매니저 앱 백그라운드에서도 송출 유지(휴대폰과 무관하므로 당연히 유지 — 확인만).

---

## 9. 프론트엔드 변경 (`api/`·`live/` + 아래 §9.2 에 적은 화면. "화면 무변경" 이 아니다 — 목록 밖의 화면은 손대지 않는다)

### 9.0 계약의 기준은 `api/*.js` 함수, HTTP 응답이 아니다
화면이 아는 계약은 **`api/customer.js`·`api/manager.js` 의 함수 이름·인자·반환 모양**(`api/README.md`)이다. 서버 응답이 그 모양과 다르면 **어댑터 함수 안에서 변환**한다. "API 내부만 바꾸면 된다" 는 이 뜻이며, 아래 표처럼 변환이 필요한 곳을 전부 적는다(새로 생기면 이 표에 추가).

| 함수 | 서버 응답 | 어댑터가 하는 일 |
|---|---|---|
| `loginWithPhone` | 토큰 + user | 세션 저장 후 `{ ok: true, user }`. `LOGIN_FAILED` 는 **던지지 않고** `{ ok: false }` (화면이 문구를 고름). 그 외 오류는 던진다(화면의 `useAction` 이 잡는다) |
| `getSavedAddress` | 404 `NOT_FOUND` | `null` 로 바꿔 돌려준다(확인 화면은 null 이면 "주소를 입력해주세요") |
| `getJobContact(jobId)` | `{ phone, mapQuery }` | 그대로. 인자 `jobId` 필수(2차 점검에서 화면도 바뀜) |
| `resumeJob(jobId)` · `pauseJob(jobId, reason)` · `completeJob(jobId)` | | 그대로. 화면은 라이브 세션의 `jobId` 를 넘긴다(고정값 없음) |
| `getReservations` | 서버가 `tab/status/rows/series` 까지 만든 목록 | 그대로 |
| `createBooking(category, draft)` | `{ id, …, visits, amounts }` | 그대로 + `Idempotency-Key` 는 초안의 `submitKey` 를 헤더로(§9.4) |
| `getLiveSession(bookingId)` | `live.state` 포함 | 그대로 |
| `requestPhoneVerification` · `verifyPhone` | | `PHONE_TAKEN` 은 `{ sent: false, reason: 'taken' }` 로 (화면이 로그인 안내), 나머지는 던진다 |
| `addUnavailable({ repeat })` | | 앱 문구 `'반복 없음'|'매주'|'매월'` ↔ `none|weekly|monthly` 변환 |
| 모든 조회 | `{ error: { code, message } }` | `ApiError(status, code, message)` 로 던진다 → `useAsync` 가 `ScreenError`, `useAction` 이 `error` |

버튼 동작의 실패는 `useAsync` 가 아니라 **`useAction`**(2차 점검에서 추가) 이 받는다. 서버 연결 전에 화면이 이미 `useAction` 을 쓰는 곳: 예약 완료·취소, 로그인, 촬영 일시정지·재개·완료. B2~B4 에서 나머지 버튼(주소 추가·삭제, 문의 보내기, 콜 거절·수락, 근무시간 저장, 알림 토글)도 `useAction` 으로 바꾼다 — 이것도 "화면 변경" 이며 §9.2 목록에 포함한다.

### 9.1 새 파일 (두 앱 동일 내용, 복사해서 둔다 — 공용 패키지로 빼지 않는다. 앱마다 base URL·세션 키가 다르고, 유지보수자가 한 파일만 읽으면 되게)
```js
// apps/<app>/src/api/http.js — 서버 호출 한 곳. 화면은 이 파일을 모른다 (customer.js/manager.js 만 쓴다)
const BASE = import.meta.env.VITE_API_URL;               // .env.local: http://localhost:8080/api/v1
export class ApiError extends Error { constructor(status, code, message) { super(message); this.status = status; this.code = code; } }
async function request(method, path, body, { retry = true } = {}) { … Authorization: Bearer session.accessToken() … 401 → refresh 1회 → 재시도 … 응답 error → throw new ApiError … 네트워크 실패 → ApiError(0, 'NETWORK', '네트워크 연결을 확인해주세요') }
export const http = { get: (p) => request('GET', p), post: (p, b) => request('POST', p, b), put: …, patch: …, delete: … };
```
```js
// apps/<app>/src/api/session.js — 토큰 보관 (localStorage 'ansimclean.<app>.auth')
export const session = { get(), set({ accessToken, refreshToken, expiresAt, user }), clear(), accessToken(), refreshToken(), isSignedIn() /* accessToken && user.profileComplete */ };
```
- `customer.js`/`manager.js`: `isSignedIn = session.isSignedIn`. 각 함수 본문을 §5 표의 한 줄 호출로 교체. **함수 이름·인자·반환 모양은 그대로**(테스트 136개가 그 계약을 지킨다). DEV `?scenario=empty|error` 는 유지(mock 으로 분기).
- `mock.js` 는 남긴다: 로컬 유지 함수들(§5 "로컬 유지")이 쓰고, Vitest 는 `vi.mock('./http.js')` 로 mock 응답을 돌려준다(테스트에서 서버 없음).
- `.env.example` 에 `VITE_API_URL`, `VITE_LIVEKIT_URL`(토큰 응답의 `url` 을 쓰므로 없어도 됨). 프로덕션 빌드 CSP `connect-src` 는 `'self' https: wss:` (`scripts/vite-csp-plugin.js` 고정값 — API·LiveKit·Storage 호스트를 모두 허용한다. 호스트별로 좁히는 것은 B7 배포 때).

### 9.2 화면에서 바뀌는 3곳 (전부 `(코드에서 정함)` 표기 + PROGRESS 기록)
1. **인증번호 입력**: 고객 `ProfileInfo.jsx`·매니저 `SignUpInfo.jsx` — "인증하기" 탭 후 `PhoneField` 아래에 `TextField label="인증번호" inputMode="numeric" maxLength=6` 이 나타나고, 6자리 입력 시 `verifyPhone` → 성공하면 `success="인증되었어요"`, 실패 `error=code 별 문구`. "다음" 은 인증 성공 후 활성. (`PhoneField` 는 그대로)
2. **콜 수락**: `CallList.jsx`·`CallDetail.jsx` 의 `onAccept` 가 `navigate` 대신 `acceptCall(id)` → `{ jobId }` 로 이동. `OFFER_TAKEN/EXPIRED` 면 `Sheet` 문구 "이미 마감된 요청이에요" 후 목록 새로고침.
3. **청소 시작**: `JobPreparing.jsx` "청소 시작" = `startJob(id)` → `live.start(id)` 순서(작업 상태 먼저, 그 다음 라이브 세션·미리보기). `SignUp.jsx` 의 소셜 버튼은 결정(§13-1)대로 `start('phone')`.
4. **촬영 장비**(`Equipment.jsx`): 휴대폰 카메라 테스트 시트를 **바디캠 설정 정보**(RTMP 주소·마스킹 키·연결 상태·마지막 온라인)로 바꾼다. `liveClient.testCamera` 삭제. `JobPreparing` 의 장비 4행은 `GET /manager/device/status` 로.
7. **정기청소 주문 내용**(`BookingConfirm.jsx`): 확인 화면이 `POST /customer/bookings/quote` 로 견적을 받아 **"요금" `SummaryItem`** 을 추가한다 — 1회 금액(기본 + 유료 옵션), 정기청소면 회차 날짜 목록(4~5개)과 총 예정 금액, "결제는 청소 완료 후" 안내(결제 연동 전). 견적 없이는 완료 불가. 예약 완료 화면 카드에도 총 금액 행.
8. **라이브 화면**(§8 바디캠 구조에 필요한 표시): 매니저 `JobPreparing` 장비 4행을 `GET /manager/device/status` 로, "청소 시작" 후 `JobInProgress` 상단 카드에 라이브 상태 문구(카메라 연결 대기 중 / 실시간 촬영 중 / 일시정지) + 작은 미리보기(`VideoSurface`, 기존 컴포넌트) · 고객 `ServiceLivePortrait/Landscape` 는 `live.state` 별 문구(§5.2)와 `waiting` 일 때 검은 화면 위 안내 · 고객 **완료 탭 카드에 "녹화 영상 보기"**(`ReservationCard` 에 `onReplay` 추가, `recordingReady` 일 때만) → `/service/live?booking=&replay=1`.
9. **버튼 동작 전부 `useAction`**(§9.0): 실패 문구와 더블 탭 방지가 없는 버튼을 B2~B4 에서 화면별로 바꾼다.
6. **소리 없음**(결정 §13-23): 고객 라이브의 소리 켜기 시트(`LIVE_SETTINGS_COPY`, 코드에서 정한 것)는 제거하고, Figma 의 Mute 컨트롤은 그대로 그리되 `disabled` + PROGRESS 판단 항목(디자이너 확인).
5. **예약 취소 시트**(`Reservations.jsx`): 정기청소(`series: true`)면 "이번 회차만 / 정기청소 전체" `RadioOption` 2개 추가. **회원 탈퇴**: 고객 `Settings.jsx` 로그아웃 아래, 매니저 `Profile.jsx` 로그아웃 아래에 "회원 탈퇴" 텍스트 버튼 → 비밀번호 확인 시트 → `deleteAccount()` → 가입 화면. (앱스토어 심사 요건)

### 9.3 `liveClient.js` 채우기 (기존 `TODO(stage5)` 자리를 채운다. 화면·훅 변경 없음)
- `npm i livekit-client -w apps/customer -w apps/manager` (PLAN.md 패키지 표에 추가).
- 매니저 `start(jobId)`: `http.post(/live/sessions/{id}/start)` → `{ preview: { url, token } }` 로 `room.connect` → `TrackSubscribed` 에서 `attach(videoEl)`(미리보기) → 3초마다 `GET /live/sessions/{id}` 로 `cameraOnline` 을 읽어 `status = waiting_camera→'connecting' | live→'live'`. **`createLocalTracks`·`publishTrack` 은 쓰지 않는다** (영상은 바디캠). `pause()` = `http.post(/live/.../pause)` 후 상태 `paused`, `resume()` 반대. `stop()` = `http.post(/live/.../stop)` → `room.disconnect()`. `@capacitor/keep-awake` 는 불필요(휴대폰이 송출하지 않는다) — 추가하지 않는다.
- 고객 `join(bookingId)`: `getLiveSession(bookingId)` → `live.available` 이면 viewer 토큰 → `room.connect` → `TrackSubscribed` 에서 `attach(videoEl)` → `'live'`; `recordingReady` 면 `<video src=signedUrl>` 로 재생 모드(`seek/back15/forward15` 활성). `capture()` = canvas → `toBlob` → 사진첩 저장(결정 §13-15).
- CSP `connect-src`/`media-src` 에 API 호스트·`wss://*.livekit.cloud`·Supabase Storage 호스트.

### 9.4 멱등 키를 앱이 다루는 법
- 예약 확인 화면이 **처음 "완료" 를 누를 때** `crypto.randomUUID()` 로 `submitKey` 를 만들어 초안(`bookingDraft`)에 저장한다. 요청이 실패해 다시 누르면 **같은 키**를 다시 보낸다. 성공하면 초안과 함께 지워진다. 초안 내용을 바꾸면(수정하기 → 값 변경) `submitKey` 를 지운다(서버의 본문 해시 검사와 일치).
- 콜 수락·작업 시작·완료·문의 보내기는 화면 진입 시 키를 만들고 그 화면에 머무는 동안 재사용한다(`useRef`).
- `http.js` 는 `Idempotency-Key` 헤더가 있는 요청이 `409 IN_PROGRESS` 를 받으면 1초 뒤 같은 키로 한 번 더 시도한다.


---

## 10. 단계별 실행 계획 (B0 → B8. 회차마다 §14 형식으로 보고하고 **멈춘다**)

각 단계의 "만드는 파일" 은 전부 만들어야 완료다. "완료 기준" 은 전부 통과해야 보고할 수 있다. 커밋은 항목 단위, 형식 `<type>(backend|customer|manager|docs): … (단계 B N)`.

### B0 — 결정·환경·규칙 (코드 없음, 반나절)
- [ ] §13 의 결정 항목을 사용자에게 받는다(기본값으로 진행해도 되는 것은 기본값 명시).
- [ ] (백엔드 컴퓨터) JDK 21, Docker, Supabase 프로젝트 `ansimclean-dev`(Seoul, Phone/Email provider 끔, 가입 끔) + JWT signing key 확인 + Storage 버킷 2개 + S3 access key, LiveKit Cloud 프로젝트 + **Ingress·Egress** + 웹훅 URL(로컬은 `cloudflared` 터널), **Solapi 계정 + 발신번호 등록**, 바디캠 기기 사양 확인(§8.3) — RTMP 푸시가 안 되는 기기면 §13-19 로 되돌아온다.
- [ ] GitHub 비공개 저장소 생성(두 컴퓨터가 공유. push 는 사용자 승인 후).
- [ ] **Supabase Auth 검증(30분)**: dev 프로젝트에서 curl 로 `admin/users { phone, password, phone_confirm: true }` 생성 → `token?grant_type=password { phone, password }` 로그인 → refresh → logout 이 **Phone provider 설정(켬/끔, SMS 업체 미설정)** 에서 되는지 확인해 `runbook.md` 에 정확한 토글 상태를 적는다. 안 되면(휴대폰 비밀번호 로그인이 provider 에 묶여 있으면) 대안 B: Supabase Auth 를 쓰지 않고 Spring 이 BCrypt + 자체 JWT(RS256, JWKS 는 Spring 이 제공) 를 발급 — `SecurityConfig` 의 디코더만 바뀌고 앱·API 계약은 동일. 이 확인 결과를 B0 보고에 넣는다.
- [ ] `CLAUDE.md`: §1 표의 "서버" 행을 이 계획대로, §11 = 본 문서 §3 그대로, §0 시작 절차 4번 명령에 `cd backend && ./mvnw -q verify` 추가. `PLAN.md` §0 "서버도 JS" 행 수정 + §3 에 "단계 7 → BACKEND_PLAN.md" 링크. `PROGRESS.md` 에 "## 백엔드 (BACKEND_PLAN.md)" 절과 B0~B8 체크리스트 생성.
- 완료 기준: `java -version` 21 · `docker ps` 동작(또는 §11 대안 채택을 PROGRESS 에 기록) · Supabase/LiveKit/Solapi 값이 `backend/.env`(비커밋)에 있고 `.env.example` 에 이름이 있음.
- 커밋: `docs: 백엔드 계획 승인 반영 — CLAUDE.md §1·§11, PLAN.md, PROGRESS.md (단계 B0)`

### B1 — 뼈대 (하루)
- [ ] `curl https://start.spring.io/starter.zip -d bootVersion=<start.spring.io 의 최신 4.0.x GA> -d javaVersion=21 -d type=maven-project -d groupId=kr.ansimclean -d artifactId=api -d name=api -d packageName=kr.ansimclean.api -d dependencies=web,validation,data-jpa,postgresql,flyway,security,oauth2-resource-server,actuator,testcontainers -o api.zip` → `backend/` 로 풀기. `pom.xml` 에 §2.2 나머지 추가.
- [ ] `common/` 전부(§2): `ApiException`·`ErrorCode`·`GlobalExceptionHandler`·`ErrorResponse`, `CorsConfig`(`ALLOWED_ORIGINS`), `JacksonConfig`(`Instant` ISO, null 필드 포함, camelCase 유지), `AppClock`, `KoreanFormat`(전 메서드 + 테스트), `SecurityConfig`(§7.2, 아직 role 없이 인증만), `SupabaseJwtDecoderConfig`, `CurrentUser`.
- [ ] `db/migration/V001__schema.sql`(§4 그대로) · `V002__seed_catalog.sql` · `V003__seed_terms.sql` · `V004__seed_support.sql` · `V005__seed_settings.sql`(`app_settings` 전 키: `platform_fee_rate, offer_ttl_minutes, free_option_limit, time_slots, cancel_deadline_hours, duration_hours, recording_retention_days, home_slides, repair_hero, notes_copy, confirm_copy`).
- [ ] `application.yml`(`ddl-auto=validate`, Flyway on, `server.port=8080`, Supabase DB URL = **Session pooler 5432**(prepared statement 호환) `jdbc:postgresql://…pooler.supabase.com:5432/postgres?sslmode=require`), `application-local.yml`, `application-test.yml`(Testcontainers `postgres:16-alpine`, Flyway 로 같은 SQL 적용).
- [ ] `GET /actuator/health` · `GET /terms` · `GET /catalog/**`(읽기 전용, 인증 없음) 구현 — 카탈로그 응답이 `mock.js` 와 **JSON 으로 동일**한지 테스트(`CatalogControllerTest` 가 `apps/customer/src/api/mock.js` 의 값을 테스트 리소스로 복사해 비교).
- [ ] `server/` 삭제(루트 `package.json` workspaces·`vitest.config.js` include·`dev:server` 스크립트·`PROGRESS`/`CLAUDE` 언급 정리). `README.md` 루트에 백엔드 실행 한 줄.
- [ ] `backend/README.md`, `docs/api.md`(§5 복사), `docs/domain.md`(§6), `docs/runbook.md`(§12 골격).
- 완료 기준: `./mvnw verify` 초록(Testcontainers) · 로컬 기동 후 `curl /catalog/categories/home/options` = mock 과 같음 · dev Supabase 에 Flyway 5개 적용됨(`flyway_schema_history`) · 프론트 `npm test` 136 그대로.
- 커밋 3개: `feat(backend): Spring Boot 뼈대·공통(오류·보안·포맷) (단계 B1)` / `feat(backend): Flyway 스키마·카탈로그 seed·카탈로그 API (단계 B1)` / `chore: Node server/ 제거, 백엔드 문서 (단계 B1)`

### B2 — 인증 + 앱 연결 (2일)
- [ ] `auth/`: `OtpService`(생성·해시·검증·verificationToken, `phone_verifications`), `common/sms/{SmsSender, SolapiSmsSender, LoggingSmsSender}`, `SupabaseAuthClient`(admin 생성/삭제/비밀번호·password grant·refresh·logout, 오류 → `UPSTREAM`/`LOGIN_FAILED` 매핑), `AuthController`·`AuthService`(§5.1 전부, 가입 보상 처리), `OtpRateLimiter`·`LoginRateLimiter`, `profile/ProfileService.createForSignup`(알림 설정 기본 행 포함), 매니저 가입 시 `manager_profiles`+`manager_hours` 기본값, **회원 탈퇴(`DELETE /auth/account`, 익명화)**, `common/web/Idempotent`(§6.9), `common/outbox/{OutboxService, OutboxDispatcher}`(§2.4), `signupStep` 재개(§7.3).
- [ ] `SecurityConfig` 에 role 매핑(`profiles.role` 조회 + 캐시).
- [ ] 앱: `http.js`·`session.js`, `customer.js`/`manager.js` 의 인증 함수 교체 + `verifyPhone` 추가, 화면 변경 §9.2-1, `RequireAuth` 는 `isSignedIn` 그대로, 앱 시작 시 `GET /auth/me`(`App.jsx` 에서 1회, 실패 시 세션 삭제), 로그아웃 후 `/signup`(고객)·`/auth/login`(매니저).
- 완료 기준: 통합 테스트 `AuthFlowIT`(Supabase Auth·Solapi 는 `WireMock` 대역: otp(코드는 DB 해시로 검증) → verify → signup → complete → login → me → refresh → logout, 오답 5회 폐기, 토큰 재사용 거부) · 실제 dev Supabase + 실제 휴대폰 문자로 고객·매니저 가입 1회씩 성공(스크린샷) · 잘못된 비밀번호 5회 → 여전히 `LOGIN_FAILED`, 11회 → 429 · 프론트 테스트 초록(인증 테스트는 `http` mock).
- 커밋: `feat(backend): Supabase Auth 위임 — OTP·가입·로그인·refresh (단계 B2)` / `feat(customer,manager): http·session 어댑터, 인증 API 연결, 인증번호 입력 (단계 B2)`

### B3 — 고객 (2일)
- [ ] `profile/`(주소·주거·알림·프로필), `booking/`(§6.1 생성·조회·변경·취소, `PricingService`, 예약 목록 매핑), `catalog/time-slots`, `customer/home·repair·settings`, **정기청소 시리즈(§6.8, 스케줄러 포함)**, **`payment/`(구조만: `PaymentGateway`·`NoopPaymentGateway`·`PaymentMethodController` 빈 목록·`payments` 엔티티, 호출 지점 주석)**. `OfferService.dispatch` 는 B4 에서 오므로 B3 에서는 인터페이스만 두고 `assigning` 까지.
- [ ] 앱 `customer.js` 나머지 함수 교체. `Reservations.test.jsx` 등 기존 화면 테스트는 `http` mock 으로 통과.
- 완료 기준: `QuoteTest`(1회·정기 견적 금액·회차 날짜) · `IdempotencyIT`(같은 키 재요청 1건, 본문 다르면 422, 동시 요청 하나만 통과) · `BookingFlowIT`(생성 → 목록 upcoming/assigning → 변경 → 취소 → cancelled 탭, 기한 초과 409, 무료 옵션 3개 400, 주말 409, 같은 `Idempotency-Key` 재요청은 예약 1건, 정기청소 생성 → 매월 1회 회차 전부가 즉시 `requested` 로 생김 → `SeriesDispatcher` 실행 → 7일 안의 회차만 매칭(우선 오퍼) → 전체 취소 시 시작 전 회차 전부 취소) · `KoreanFormatTest` 전 메서드 · 브라우저: 고객 앱 예약 9화면 → 완료 → 내 예약에 보임 → 취소.
- 커밋: `feat(backend): 고객 프로필·주소·설정 API (단계 B3)` / `feat(backend): 예약 생성·조회·변경·취소 + 가격 (단계 B3)` / `feat(customer): api 어댑터 실서버 연결 (단계 B3)`

### B4 — 매니저 (3일)
- [ ] `offer/`(§6.2 dispatch·accept·reject·expire 스케줄러), `job/`(§6.1 prepare/start/pause/resume/complete, 체크리스트, contact, device), `schedule/`, `settlement/`(§6.5), `managerhome/`(§6.6), `manager/profile*`.
- [ ] 앱 `manager.js` 교체 + 화면 변경 §9.2-2·3. `JobInProgress.test.jsx` 는 `http` mock.
- 완료 기준: `MatchingIT`(고객 예약 → 후보 매니저 2명 offer → A 수락 성공, B 수락 `OFFER_TAKEN`, 만료 스케줄러 → expired → 재매칭 · **같은 매니저가 겹치는 예약 A·B 를 동시에 수락 → 하나만 성공(제약 위반은 `OFFER_TAKEN`)** · 일정 변경 → accepted 가 superseded, round 2 우선 오퍼 → 재수락 → live_sessions 1행 유지) · `OutboxIT`(핸들러 실패 → 재시도 → sent, 6회 실패 → failed) · `JobFlowIT`(prepare → start → pause → resume → complete → settlement pending, 다른 날짜 작업 start 409, 동시에 두 작업 409) · 브라우저: 고객 앱에서 예약 → 매니저 앱 콜 도착 → 수락 → 청소 시작 → 완료 → 정산 화면 → 고객 앱 완료 탭.
- 커밋: `feat(backend): 콜 매칭·수락·만료 (단계 B4)` / `feat(backend): 작업 상태기계·체크리스트·정산 (단계 B4)` / `feat(backend): 매니저 홈·일정·근무시간·프로필 (단계 B4)` / `feat(manager): api 어댑터 실서버 연결, 콜 수락·청소 시작 (단계 B4)`

### B5 — 라이브 (2일 + 바디캠 실기기 1일)
- [ ] `live/`(§8: 기기 등록 시 Ingress 생성 `DeviceService.register`(운영자용 SQL 이 아니라 Spring 내부 서비스 + B0 에서 정한 관리 방식으로 호출), `start/stop/pause/resume`, 고객 토큰, 웹훅 6종, 녹화본 서명 URL, 보존 스케줄러), `SupabaseStorageClient`, `manager/device/status`.
- [ ] 앱 `liveClient.js` 두 개 채우기(§9.3), `Equipment.jsx` 바디캠 설정 화면(§9.2-4), `livekit-client`, CSP, 매니저 앱 카메라·마이크 권한 문구 제거(Info.plist·AndroidManifest — 단계 6 규칙대로 그 파일만).
- 완료 기준: `LiveSessionIT`(start → `ingress_started` 웹훅 → live → pause(mute 호출 + 확인 폴링 검증, 미확인 시 502 와 `mute_pending`) → `jobs/pause` 가 muted 전에는 409 → resume → stop(egress stop·room delete 호출) · 고객 토큰 상태별 409 · 웹훅 서명 실패 401 · `ingress_ended` 중 `waiting_camera` · **웹훅 없이 `LiveReconciler` 가 LiveKit 조회만으로 live/ready 를 맞춤**) · **바디캠 실기기**(§8.3 검증 5항목) 영상/스크린샷 · `npm run cap:sync` 후 두 앱 빌드.
- 커밋: `feat(backend): LiveKit Ingress(바디캠)·Egress 녹화·웹훅·녹화본 URL (단계 B5)` / `feat(live): liveClient 실제 연결(미리보기·시청), 촬영 장비 화면 (단계 B5)`

### B6 — 지원·푸시 (1일)
- [ ] `support/`(FAQ·공지·문의·약관 본문), `push/`(FCM 토큰 등록, `PushService.send(userId, title, body, data)` — 실패는 로그만, 흐름을 막지 않는다), §6 의 푸시 지점 6개 연결. 앱: `@capacitor/push-notifications` 등록 + `registerToken`.
- 완료 기준: 실기기에서 새 콜·배정·시작·완료 푸시 4종 수신.
- 커밋: `feat(backend): 지원 API·FCM 푸시 (단계 B6)` / `feat(customer,manager): 푸시 토큰 등록 (단계 B6)`

### B7 — 배포 (1일)
- [ ] `ansimclean-prod` Supabase(마이그레이션은 Flyway 가 기동 시 적용), 컨테이너 이미지 `./mvnw spring-boot:build-image`(Buildpacks, Dockerfile 없음), 호스팅 = **Fly.io Tokyo 리전 1대**(§13-16) + 환경변수, `ALLOWED_ORIGINS=capacitor://localhost,https://localhost,ionic://localhost`, HTTPS, `/actuator/health` 체크, 로그(JSON) 수집. 앱 `.env.production` 의 `VITE_API_URL`, `cap:sync`, 스토어 빌드.
- 완료 기준: prod `/actuator/health` UP · 실기기 prod 로 가입→예약→매칭→라이브 1회 · `runbook.md` 완성(배포·롤백·마이그레이션 실패 시·키 회전).
- 커밋: `chore(backend): 프로덕션 설정·이미지 빌드·runbook (단계 B7)`

### B8 — 점검 (프론트 점검과 같은 형식, 1일)
- [ ] 보안: 남의 리소스 404, 토큰 없이 전 엔드포인트 401, role 교차 403, 웹훅 위조, 속도 제한, 로그 마스킹, 의존성 취약점(`./mvnw org.owasp:dependency-check-maven:check`), Supabase RLS 켜짐(anon key 로 `select` 시도 → 0행).
- [ ] 정합성: 상태기계 전 전이 테스트 표(허용/거부) 100%, 동시성(수락 경합 · 동시 시작) 테스트, 시간대(서버 UTC 로 띄워도 표시가 Seoul).
- [ ] 문서: `api.md`/`domain.md` 가 코드와 일치(엔드포인트 목록 자동 덤프 `GET /actuator/mappings` 와 대조).
- 커밋: `test/docs(backend): 점검 결과 반영 (단계 B8)`

---

## 11. 테스트 전략 (`src/test`, 기능 패키지와 같은 구조)

| 층 | 도구 | 무엇을 | 예 |
|---|---|---|---|
| 단위 | JUnit 5 + Mockito | Service 규칙·상태기계·`KoreanFormat`·`PricingService`·rate limiter | `BookingTest`(전이 표 전부: 8 상태 × 7 동작 = 허용/`INVALID_STATE`), `KoreanFormatTest`(§6.7 예시 값 그대로), `OfferServiceTest`(후보 선정 조건 7개) |
| 슬라이스 | `@WebMvcTest` + `spring-security-test` | JSON 모양(필드 이름 = mock.js), 검증 400, 권한 401/403 | `ReservationsControllerTest`: 응답이 `RESERVATIONS[0]` 모양(테스트 리소스 `mock-shapes/*.json` 은 앱 `mock.js` 에서 B1 에 복사, 이후 계약 변경 시 같이 수정) |
| 통합 | `@SpringBootTest` + Testcontainers Postgres + WireMock(Supabase Auth·LiveKit·FCM 대역) | 핵심 흐름 8개 (`*IT`) | `AuthFlowIT` `BookingFlowIT` `MatchingIT` `JobFlowIT` `LiveTokenIT` `SettlementIT` `SupportIT` `WebhookIT` |
| 계약 | Vitest (프론트, 기존 136개 유지) | `api/*.js` 가 §5 경로·메서드·본문으로 부르는지 (`vi.mock('./http.js')` 로 호출 인자 검사) | `customer.api.test.js`, `manager.api.test.js` (새로 2개) |
| 실기기 | 수동 체크리스트 (`docs/runbook.md`) | 가입·예약·매칭·라이브·푸시 | B2·B4·B5·B6 완료 기준 |

- 통합 테스트는 `./mvnw verify` 의 `failsafe` 단계(`*IT`)에서 돈다. Docker 가 없으면 `-DskipITs` 로 건너뛰되 **PROGRESS 에 "IT 미실행" 을 적고 보고**한다(조용히 넘기지 않는다). 대안: `SPRING_PROFILES_ACTIVE=it-supabase` 로 dev Supabase 의 별도 스키마(`test_<timestamp>`)에 Flyway 적용 후 실행, 끝나면 스키마 drop.
- 테스트 시각은 `AppClock` 을 `Clock.fixed(2026-09-15T05:00:00Z /* Seoul 14:00 */)` 로 고정. 모든 표시 문자열 테스트가 mock.js 의 값과 글자 그대로 같아야 한다.
- 커버리지 목표: Service·Entity·format **90%+** (JaCoCo `check` 로 강제, Controller·Config 는 제외). 숫자보다 "전이 표 100%" 가 중요하다.

---

## 12. 배포·운영 (`docs/runbook.md` 의 골격)

### 환경변수 (`.env.example` — 값 없이 이 목록 그대로)
```
SPRING_PROFILES_ACTIVE=local|prod
SERVER_PORT=8080
ALLOWED_ORIGINS=capacitor://localhost,https://localhost,ionic://localhost,http://localhost:5174,http://localhost:5175
DB_URL=jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require   # Session pooler
DB_USER=postgres.<project-ref>
DB_PASSWORD=
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=            # 서버에만. 앱·git 금지
SUPABASE_JWT_MODE=jwks|secret
SUPABASE_JWT_SECRET=                  # secret 모드일 때만
SUPABASE_STORAGE_S3_ENDPOINT=https://<project-ref>.supabase.co/storage/v1/s3
SUPABASE_STORAGE_S3_ACCESS_KEY=
SUPABASE_STORAGE_S3_SECRET_KEY=
SUPABASE_STORAGE_S3_REGION=ap-northeast-2
LIVEKIT_URL=wss://<project>.livekit.cloud
LIVEKIT_API_KEY=
LIVEKIT_API_SECRET=
SMS_PROVIDER=solapi|logging
SMS_API_KEY=
SMS_API_SECRET=
SMS_SENDER=01000000000                # 사전등록한 발신번호
OTP_SALT=                             # 32바이트 이상 난수
FIREBASE_SERVICE_ACCOUNT_JSON=        # base64 한 줄 (B6)
```
- 앱 origin: Capacitor iOS 는 `capacitor://localhost`, Android 는 `https://localhost`(`androidScheme: https`), 웹 개발은 5174/5175.
- DB 접속은 **Session pooler(5432)**. Transaction pooler(6543)는 JPA 의 prepared statement 와 충돌한다. 커넥션 풀 `HikariCP maximumPoolSize=10`(Supabase 무료/프로 플랜 한도 안).
- 배포: `./mvnw -DskipTests spring-boot:build-image -Dspring-boot.build-image.imageName=ansimclean/api:<git-sha>` → 레지스트리 → Fly.io(`fly deploy`, Tokyo `nrt`, 1 머신 shared-cpu-1x 1GB, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`). 헬스체크 `/actuator/health`. 스케줄러(`@Scheduled`)가 있으므로 **인스턴스는 1개**(§13-11 전까지).
- 마이그레이션: 기동 시 Flyway 자동. 실패하면 기동 실패 → 이전 이미지로 롤백(`fly releases rollback`), SQL 은 새 버전으로 수정 후 재배포. 운영 DB 는 Supabase 일일 백업 + 위험한 마이그레이션 전 수동 백업.
- 로그: JSON 한 줄(`logback-spring.xml`), 개인정보 마스킹(§3-10), Fly 로그 → 필요 시 외부 수집. 지표는 `/actuator/metrics`(외부 노출 금지 — `management.endpoints.web.exposure.include=health`).
- 키 회전: Supabase service_role·LiveKit secret·Solapi 키는 환경변수만 바꾸고 재배포. 바디캠 스트림 키 회전 = Ingress 재생성 + 기기 재설정(runbook 절차). JWT 서명키 회전은 Supabase 가 JWKS 로 알아서(앱 영향 없음).
- 장애 대응 표: Supabase Auth 다운 → 로그인만 실패(`UPSTREAM`), 기존 토큰은 JWKS 캐시로 계속 검증됨 / 문자 API 다운 → 가입·인증만 실패(`UPSTREAM`), 로그인은 정상 / LiveKit 다운 → 라이브만 실패, 작업 상태는 진행 가능 / 바디캠 끊김 → `waiting_camera`, 매니저 푸시 / Egress 실패 → `recording_status=failed`, 완료 화면 "업로드 실패", 운영자 확인.

---

## 13. 결정이 필요한 항목 (B0 에서 사용자에게 묻는다. 기본값으로 진행 가능한 것은 표시)

| # | 항목 | 선택지 | 기본값(답 없으면) |
|---|---|---|---|
| 1 | 소셜 로그인(카카오·구글·네이버) | (a) 지금은 전부 휴대폰 흐름 (b) Supabase OAuth(구글·카카오; 네이버는 미지원 → 별도) B8 뒤 | **결정: (a)** (2026-09-19) |
| 2 | OTP SMS 발송사 | 국내 문자 API | **결정: 국내 문자 API** (2026-09-19). 업체 기본 **Solapi(구 CoolSMS)** — 다른 업체(알리고·NHN Cloud·NCP SENS)를 원하면 이름만 알려주면 `SmsSender` 구현체가 바뀐다. OTP 는 Spring 이 직접 생성·검증(§7.1) |
| 3 | 가격표 (`catalog_prices`) | 카테고리 × 주기 8개 금액. Figma 에 없음 | **결정: 임시 숫자로 시작** (2026-09-19). `V002` seed: home 80,000/100,000 · office 120,000/150,000 · appliance 60,000/70,000 · move-in 150,000/180,000 (정기/1회). 운영 전 `app_settings` 가 아니라 새 마이그레이션으로 교체 |
| 4 | 주소 검색 API | 행안부 도로명주소 API(무료, 키 발급) / 카카오 로컬 API | **행안부**, B3 뒤 별도 항목 |
| 5 | 결제(PG) | 토스페이먼츠 / 포트원 / 없음 | **결정: 구조만** (2026-09-19) — `payments`·`payment_methods` 테이블, `PaymentGateway` 인터페이스 + Noop 구현, 호출 지점 주석. PG·결제 시점은 사용자가 나중에 직접 추가 |
| 6 | 시간대 마감 | (a) 매니저 가용성으로 시각 비활성 (b) 시각은 항상 열려 있고 매칭 실패 시 안내 | **(b)** — 2026-09-27 다시 확인: 끝나는 시각이 기본 근무 종료(18시)를 넘는 늦은 슬롯도 막지 않는다('매니저가 콜을 받아야 성립', 자체 점검 #16). 매칭은 **시작 시각만** 근무시간 `[start,end]` 안이면 콜을 보낸다(끝나는 시각은 넘어도 됨, 근무 불가·겹치는 작업은 작업 전체로 본다, §6.2-1) |
| 7 | 휴대폰 번호 변경 | OTP 재인증 화면 필요 | 후속 (지금은 변경 불가) |
| 8 | 운영자 화면 | 매니저 승인·정산 완료 처리·후보 0명 알림을 (a) Supabase 대시보드 SQL 로 (b) 간단한 관리 API + 관리자 role | **(a)**, (b) 는 별도 계획 |
| 9 | 콜 `eta`(이동 시간) | 위치 권한 + 거리 API 필요 | 고정 문구 |
| 10 | 앱 토큰 저장소 | localStorage / `@capacitor/preferences` / 보안 저장소 플러그인 | localStorage → B7 전 preferences (**완료 2026-09-22** — 네이티브는 Preferences, 웹은 localStorage, `session.hydrate()`) |
| 11 | 서버 인스턴스 수 | 1대(메모리 rate limit·스케줄러) / 여러 대(Redis 필요) | **1대** |
| 12 | 녹화본 형식 | MP4 만 / HLS 도 | **MP4** |
| 13 | 고객 화면의 실시간 상태(일시정지 등) | 30초 폴링 / LiveKit 데이터 채널 / Supabase Realtime | **폴링** |
| 14 | 매니저 앱 백그라운드 송출 | 앱이 백그라운드로 가면 WebView 카메라가 멈춘다 — Foreground Service(Android)·iOS 제약 | **다시 열림 (2026-10-10, #18 변경)**. 지금은 화면 꺼짐 방지(Keep Awake)만. 백그라운드 송출은 네이티브 작업이 필요해 별도 결정 |
| 15 | 캡처 저장 위치 | 사진첩(iOS `NSPhotoLibraryAddUsageDescription` 이미 있음) / 앱 폴더 | 사진첩 |
| 16 | 호스팅 | Fly.io(Tokyo) / Railway / AWS(직접) | **Fly.io** |
| 17 | 정산 계좌 원본 보관 | 저장 안 함(정산 대행) / 암호화 저장 | **저장 안 함** |
| 18 | 촬영 장비 | 매니저 휴대폰 = 카메라 / 별도 카메라 기기(`안심캠`) | ~~결정: 별도 바디캠 (2026-09-19) → §8 을 Ingress 구조로 다시 씀~~ → **결정: 매니저 휴대폰 (2026-10-10 사용자 결정 "바디캠이 아닌 스마트폰으로")**. `app.livekit.source`(`LIVE_SOURCE`) 기본 `app`, Ingress 코드는 `ingress` 로 남김(§8.0) |
| 19 | **바디캠 기기 사양** | 모델명·RTMP 푸시 지원·H.264/AAC·상태 API 유무 (§8.3) | **공장 답변 대기 (2026-09-19)**. B5 실기기 검증 전까지만 필요. 그 전엔 ffmpeg/OBS 로 대체(§8.1). RTMP 가 안 되면 중계 서버 항목이 추가된다 |
| 22 | 매니저 방문 취소·미방문 | — | **결정: 점수제** (2026-09-22, `backend/docs/manager-policy.md`). 24시간 전 월 2회 무료·이후 1점 / 24~4시간 1점 / 미방문 3점 / 지각(30분 초과) 0.5점. 3점 경고·5점 7일 정지·8점 30일 정지·10점 심사. 12개월 롤링 + 무사고 3개월마다 1점 회복. 증빙은 운영자 면제(`POST /admin/penalties/{id}/waive`, X-Admin-Token — 점수와 정지 해제가 한 트랜잭션, 문제 목록 3번). 숫자는 `app_settings.manager_penalty` |
| 21 | 정기청소 단위 | — | **결정: 매월 1회** (2026-09-22 사용자 정정. 그 전 2026-09-19 결정은 '한 달 동안 매주'였다). 첫 방문일부터 매월 같은 날짜에 1회씩 `series_term_months` 회(기본 6)를 한 번에 생성, 주말이면 다음 평일, 자동 연장 없음(종료 7일 전 안내). 회차 매칭은 방문 7일 전 (§6.8) |
| 22 | 회원 탈퇴 후 이력 보존 기간 | — | **결정: 5년** (2026-09-19). `DeletedAccountPurger`(§2.5) 가 5년 뒤 남은 개인정보를 파기 — 행은 지우지 않고 개인정보 칸을 비운다 (구현 2026-09-27, 사용자 결정) |
| 26 | 앱의 회원 탈퇴 안내 문구·정기청소 활성 시 탈퇴 차단 문구 | — | 기본값 사용(DeleteAccountSheet). 디자이너 확인 |
| 25 | **로그인 식별자** (§7.1.1) | A: Phone provider 켜기 / B: 합성 이메일 / C: Spring 자체 JWT | **결정: A** (2026-09-19). 대시보드에서 Phone provider 를 켜고 Twilio 칸에 더미 값(`ACxxx…`/`MGxxx…`)을 넣어 저장, phone confirmations 끔, 가입 끔. 확인: phone+password grant 가 `invalid_credentials`(관문 통과), `/signup`·`/otp` 는 `signup_disabled` |
| 24 | 수리 서비스 주문 모델 | 지금은 청소 예약 플로우를 빌려 쓰고 `repair_type` 만 남긴다(Figma 임시 연결). 수리 전용 카테고리·가격·매니저(기사) 모델은 디자인 확정 후 | **답 필요**(급하지 않음) — 그 전까지 수리 예약은 `repair_type` 이 있는 청소 예약으로 처리 |
| 23 | 바디캠 음성 | — | **결정: 영상만** (2026-09-19). Egress `videoOnly`, 고객 소리 설정 제거, Mute 컨트롤 disabled(§9.2-6). 약관 `recording` 문안 "영상만 녹화" |
| 20 | 바디캠 등록 절차 | 운영자가 SQL 로 `manager_devices` 삽입 + Spring 내부 API 로 Ingress 생성 / 관리 API | 결정 8 과 함께 (기본: 서버의 관리자 전용 엔드포인트 `POST /admin/devices` + `ADMIN_TOKEN` 헤더, B5) |

---

## 14. 보고 형식 (프론트 `CLAUDE.md` §6 과 같다)
1. 한 줄 결론 (끝낸 것 · 다음 단계) 2. 만든 파일 목록 3. **API 계약 표와 `docs/api.md` 차이(없으면 "없음")** 4. 검증 결과 표(§3-12 명령 + 실기기 항목) 5. 완료 기준 체크리스트 6. 판단한 항목(→ `PROGRESS.md`) 7. 결정 대기 항목 8. 커밋 목록. 보고 후 멈춘다.

---

## 부록 A. Opus 5 가 이 문서로 작업할 때의 체크리스트 (품질을 같게 만드는 장치)
- [ ] 시작 전 `CLAUDE.md` §0 절차 + 이 문서 §3·§5·§6 을 읽었다. "§5 에 없는 엔드포인트" 나 "§6 에 없는 규칙" 을 만들 때는 먼저 §13 에 추가하고 사용자에게 물었다.
- [ ] 응답 JSON 을 만들 때 `apps/*/src/api/mock.js` 의 같은 상수를 열어 **키 이름·표시 문자열 형식**을 대조했다.
- [ ] 새 상태 전이·규칙은 엔티티 메서드 + 단위 테스트(허용/거부 둘 다) 로 넣었다.
- [ ] 마이그레이션 SQL 은 §4 와 같은 스타일(text+check, timestamptz, RLS enable)이다. 커밋된 마이그레이션은 수정하지 않았다.
- [ ] 비밀값이 코드·yml·문서·커밋 메시지에 없다.
- [ ] 보고 전 §3-12 명령을 전부 돌렸고, 실패는 출력 그대로 보고에 넣었다.
- [ ] 회차 하나 끝나면 멈췄다. 다음 단계를 먼저 시작하지 않았다.

---

## 15. 검토 이력
- **2026-09-19 (2차 검토, 결정 5개 반영 후)** — 고친 허점:
  1. Spring Boot 3.5 는 OSS 지원 종료(2026-06) → **4.0.x** 로. 스타터 이름은 start.spring.io 생성 pom 을 따른다.
  2. `preparing` 서버 상태 삭제 (뒤로 가기로 되돌릴 수 없고 부분 유니크 인덱스에 걸려 다음 작업을 막았다).
  3. **정기청소(recurring)가 단일 예약으로 취급되던 문제** → `booking_series` + 일 1회 생성 스케줄러 + 전체/회차 취소(§6.8).
  4. **회원 탈퇴 없음**(앱스토어 심사 요건) → `DELETE /auth/account`, `profiles` 익명화·`deleted_at`, `auth.users` FK 제거(탈퇴 시 FK 충돌 방지), 전화번호 부분 유니크.
  5. 더블 탭·재시도 중복 → `Idempotency-Key` + `idempotency_keys`.
  6. 바디캠 Ingress: **대기 방(parking)** 규칙, 활성 Ingress 변경 거부 처리(`cameraBusy`), `reusable=true`, 완료 시 대기 방 복귀.
  7. 일시정지의 서버측 mute 가 Ingress 참가자에 통하는지 **B5 첫날 검증** + 대안(Egress 분할 + viewer 제거) 명시. 일시정지 구간은 시청·녹화 모두 불가가 요건.
  8. Supabase 휴대폰+비밀번호 로그인이 Phone provider 토글에 묶여 있을 가능성 → **B0 에서 curl 검증**, 안 되면 Spring 자체 JWT 대안(계약 불변).
  9. 인증번호 검증 시도에도 시간당 한도. 알림 설정 기본 행을 가입 때 생성.
  10. 일정 변경·정기 회차는 **기존 매니저에게 먼저 offer**(10분) 후 일반 매칭.
  11. JPA 배열·jsonb 매핑은 Hibernate 내장(`@JdbcTypeCode`)만 — 라이브러리 추가 금지 명시.
  12. `getCalls.expiresAt`·`getActiveJob.startedAt/pausedSeconds` 추가(앱 카운트다운·타이머용, 화면 계약은 유지).
  13. 바디캠 사양 미확정이어도 진행 가능함을 §8.1(ffmpeg 대체)·§13-19 에 명시. 새 결정 21~23 추가.
- **2026-09-19 (3차, 논리 구조·연결 검토)** — 결정 21·22·23 반영 + 고친 것:
  1. **트랜잭션 안에서 외부 호출**(푸시·LiveKit·문자)이 나가던 구조 → 도메인 이벤트 + `AFTER_COMMIT` 리스너로 분리, 실패는 재조정 스케줄러가 재시도 (§2.4·§2.5). 패키지 의존 방향을 ArchUnit 으로 강제.
  2. **가격 스냅샷 불일치 가능성**: 오퍼 `pay_amount`·정산 `final_amount` 가 각자 계산 → `bookings.platform_fee`·`duration_minutes` 를 생성 시 고정하고 모두 거기서 계산.
  3. **매칭 시간 겹침**: "같은 시각" 만 보던 것 → 작업 구간(`duration_minutes`)으로 겹침 검사, 근무시간 안에 구간 전체가 들어가야 함, 근무불가 반복(주/월) 펼침.
  4. **주소에서 구(區) 추출 실패**(부산 도로명 주소엔 '구' 가 없음) → `sigungu/sido` 컬럼(주소 API 결과 저장), null 이면 시/도 매칭.
  5. **일정 변경 시 매니저 필드**가 남아 매니저 일정에 계속 보이던 문제 → `manager_id/체크리스트` 초기화 규칙 명시.
  6. **날짜가 지나도 남는 `assigning/scheduled`** → `StaleBookingCleaner`(미배정 취소 + 고객 푸시, 미방문 취소 + 운영자 로그).
  7. **Egress 를 빈 방에서 시작**하던 것 → 카메라가 붙는 `ingress_started` 에서 시작, 방은 `createRoom(emptyTimeout 6h)` 로 미리 생성.
  8. **카메라가 붙기 전 일시정지** → `mute_pending`. **웹훅 중복·역순** → `livekit_events` + "허용될 때만 전이".
  9. **수락 경합 잠금 순서**(booking → offer) 고정, 취소된 예약의 오퍼 수락은 `OFFER_EXPIRED`.
  10. **가입 중 계정의 권한**: `signup_completed_at` 을 `profileComplete` 의 유일한 기준으로, 가입 중에는 가입 경로만 허용. Supabase 사용자만 남고 `profiles` 가 없는 잔재 → 재사용 규칙(멱등).
  11. 휴대폰 중복 검사를 role 무관으로 통일(휴대폰 하나 = 계정 하나).
  12. 고객 라이브 응답에 `live.state`(waiting/live/paused/ended) 와 상태별 문구.
- **2026-09-19 (4차, 외부 검토 반영)** — 앱 버그 6건은 코드로 고침(PROGRESS "2차 점검"). 계획에서 고친 것:
  1. `POST /auth/signup` 은 `permitAll`(토큰이 없는 신규 사용자), `/auth/signup/complete` 만 인증. 가입 재개는 `signupStep`(서버가 판단) 으로 OTP·기본정보를 다시 거치지 않는다. 가입 중 번호의 OTP 요청은 `PHONE_TAKEN` + 로그인 안내.
  2. "API 내부만 바꾸면 된다" 를 §9.0 어댑터 변환 규칙(응답 → 함수 계약 변환표)과 §9.2 화면 변경 목록(9개)으로 바꿈. `useAction` 을 계약에 포함.
  3. 일시정지 책임을 §8.2.1 한 곳에: `live/pause` 는 영상만(확인 폴링 후 200), `jobs/pause` 는 muted 를 선행 조건으로 강제, 부분 실패 표. 재개는 작업 상태 → 영상 순서.
  4. 같은 매니저 겹침 동시 수락: 매니저 행 잠금(잠금 순서 manager → booking → offer) + `starts_at/ends_at` + gist 배타 제약.
  5. 재배정: `offers.round` + `superseded`, unique `(booking_id, manager_id, round)`, `live_sessions` upsert.
  6. AFTER_COMMIT 만으로는 유실 → `outbox_events` + `OutboxDispatcher`(재시도·백오프), `LiveReconciler` 가 LiveKit 을 조회해 웹훅 유실을 복구.
  7. 멱등 알고리즘 §6.9(업무와 키가 한 트랜잭션, processing/done, 본문 해시, 동시 요청) + 앱의 키 생성·재사용 규칙 §9.4.
  8. 정기청소 주문 내용: `POST /customer/bookings/quote` + 확인 화면 "요금" 행(회차 일정·회차 금액·총액), 생성 응답에 회차·금액.
  9. 취소·변경 기한을 `starts_at − now ≥ 24h` 하나로. 지역 매칭은 '시도 시군구' 전체 문자열 + `manager_profiles.sido`. B3 테스트 문구를 본문과 일치. B1 생성 명령 4.0.x. `repair_type` 컬럼 + 결정 24.
- **2026-09-19 (1차)** — 초안 작성, 결정 1·2·3·5·18 반영.
