# 코드 읽는 법 (안심클린)

코드 곳곳의 주석은 **그 파일에만 해당하는 것**(역할, 그렇게 한 이유, 지켜야 할 조건, 실패하면 어떻게 되는지)을 적는다.
여러 파일에 똑같이 나오는 기본 개념은 여기에 한 번만 적는다. 처음 보는 표시가 나오면 이 문서에서 찾는다.

규칙과 결정의 원문은 따로 있다. 주석과 원문이 다르면 원문과 코드를 먼저 확인한다.
- 작업 규칙: `CLAUDE.md` · 진행 기록과 결정 이력: `PROGRESS.md` · 화면 계획: `PLAN.md`
- 서버 사양: `BACKEND_PLAN.md` · API 계약: `backend/docs/api.md` · 업무 규칙: `backend/docs/domain.md` · 운영: `backend/docs/runbook.md`
- 주석에 붙은 번호(예: "자체 점검 #15", "감사 H-02", "검수 3차", "§8.2.1")는 `PROGRESS.md`·`BACKEND_PLAN.md` 에서 같은 번호를 찾으면 그때의 문제와 결정이 나온다.

---

## 1. 전체 구조와 요청이 흐르는 길

```
apps/customer   고객 앱 (React + Vite, Capacitor 로 iOS/Android 앱이 된다)
apps/manager    매니저 앱 (같은 구조)
packages/ui     두 앱이 같이 쓰는 컴포넌트·아이콘·토큰 (@ansimclean/ui)
backend         서버 (Java 21 + Spring Boot). DB 는 Supabase Postgres
```

화면에서 서버까지:
```
화면(screens/…jsx) → api/customer.js·manager.js 의 함수 → api/http.js (토큰·재시도)
  → 서버 Controller(주소를 받는 곳) → Service(업무 규칙) → Repository(DB) → Postgres
```
- 화면은 서버를 직접 부르지 않고 `api/*.js` 함수만 부른다. 개발 중에는 이 함수들이 `api/mock.js`(가짜 데이터)를 돌려줄 수 있다.
- 서버 응답의 모양(필드 이름)은 `backend/docs/api.md` 가 기준이고, 앱 `mock.js` 와 글자까지 같다.
- 푸시·문자·LiveKit 같은 **바깥 호출**은 업무 처리 도중에 바로 하지 않는다. 먼저 DB 에 "보낼 일"을 적고(아웃박스, 아래 3-6), 업무가 저장된 뒤 따로 보낸다.
- 라이브 영상은 매니저의 **바디캠 → LiveKit Ingress(영상 입구)** 로 들어가고, 고객 앱은 LiveKit 에서 시청만 한다. 매니저 앱은 영상을 송출하지 않는다.

---

## 2. 프런트엔드 공통 개념

### 2-1. 스타일
- **CSS Modules** (`X.module.css`): 파일 안의 클래스 이름이 빌드 때 겹치지 않는 이름으로 바뀐다. JSX 에서는 `styles.button` 처럼 쓴다.
- **토큰** `var(--color-…)`, `var(--spacing-…)`, `var(--radius-…)`: Figma 변수에서 만든 값(`packages/ui/src/tokens/`, 생성물이라 직접 고치지 않는다). 코드에 색·크기를 직접 쓰지 않는다 (`npm run check:tokens` 가 막는다).
- 글꼴 크기·굵기는 CSS 에 쓰지 않고 JSX 에서 `.text-body-default` 같은 전역 클래스를 붙인다.
- `cx(a, b, …)`: 클래스 이름 여러 개를 공백으로 이어 붙인다 (빈 값은 건너뛴다).
- Figma 테두리는 안쪽으로 그려지므로 크기가 고정된 요소는 `border` 대신 `box-shadow: inset 0 0 0 1px …` 를 쓴다.
- 터치 영역은 최소 44px(`--touch-target`). 눈에 보이는 크기가 작으면 `::before` 로 누를 수 있는 영역만 넓힌다.
- 화면 폭은 기기를 따라가고(폰 360~430), 480px 을 넘는 태블릿·PC 에서만 가운데로 모은다.

### 2-2. 화면이 데이터를 다루는 방법 (`packages/ui/src/utils`)
- `useAsync(() => getX(id), [id])`: 화면이 열릴 때(그리고 `[id]` 가 바뀔 때) 서버 함수를 부르고 `{ data, error, reload, refresh, setData }` 를 준다.
  보통 바로 아래에서 `if (error) return <ScreenError …/>; if (!data) return <ScreenLoading …/>;` 로 오류·로딩 화면을 먼저 보여 준다.
  `reload` = 처음부터 다시(로딩 화면을 거친다), `refresh` = 화면을 그대로 둔 채 새 값으로 바꾼다, `setData` = 서버 응답 전에 화면을 먼저 바꾼다(실패하면 되돌린다).
- `useAction(async () => …)`: 버튼 동작(저장·취소·일시정지…)용. `{ run, loading, error }` 를 주고, 처리 중에는 버튼이 `loading` 이 되어 두 번 눌리지 않는다. 화면 이동은 요청이 성공한 뒤에만 한다.
- `usePolling`: 일정 간격으로 다시 받아 온다. 푸시가 오면 앞당겨 받는다.
- 날짜 문자열 `'YYYY-MM-DD'` 는 `fromDateKey`/`toDateKey` 로만 바꾼다. `new Date('YYYY-MM-DD')` 는 UTC 로 읽혀 하루가 어긋날 수 있다.

### 2-3. React 기본
- `useState` = 화면이 기억하는 값, `useEffect` = 화면이 그려진 뒤 할 일(서버 호출·구독), `useLayoutEffect` = 화면에 보이기 직전에 할 일, `useRef` = 다시 그리지 않고 기억하는 값.
- 라우트는 `routes.jsx` 에 있고 `lazy: page(() => import(…))` 로 화면 코드를 필요할 때 불러온다. 로그인이 필요한 화면은 `RequireAuth` 안에 있다.
- `?scenario=empty` / `?scenario=error` 는 개발 중에만 빈 화면·오류 화면을 보는 주소 옵션이다.

### 2-4. 프런트 테스트 (Vitest + Testing Library)
- `render(<X />)` 로 그리고, `screen.getByRole('button', { name: '저장' })` 처럼 **사용자가 보는 역할·이름**으로 요소를 찾는다.
  `getBy…` = 지금 있어야 한다, `findBy…` = 나타날 때까지 기다린다(최대 3초), `queryBy…` = 없어도 된다(없으면 null).
- `vi.fn()` = 불린 횟수와 인자를 기록하는 가짜 함수, `vi.mock(…)` = 모듈을 가짜로 바꾼다, `userEvent`/`fireEvent` = 누르기·입력하기 흉내.

---

## 3. 서버(Java·Spring) 공통 개념

### 3-1. 계층
- **Controller** (`@RestController`): HTTP 주소를 받는 곳. `@GetMapping("/…")`·`@PostMapping` 이 주소, `@PathVariable` = 주소 안의 값, `@RequestBody` = 요청 본문(JSON), `@Valid` = 본문 검사를 돌린다. 규칙은 두지 않고 Service 를 부른다.
- **Service** (`@Service`): 업무 규칙과 트랜잭션. 다른 기능의 데이터가 필요하면 그 기능의 Service 를 부른다(남의 Repository 를 직접 쓰지 않는다).
- **Repository** (`interface … extends JpaRepository`): DB 읽기·쓰기. 메서드 이름만으로 쿼리가 만들어진다(예: `findByBookingIdAndStatus`). 복잡한 것은 `@Query` 로 SQL 을 직접 쓴다(`nativeQuery = true` = Postgres SQL 그대로). 데이터를 바꾸는 쿼리에는 `@Modifying` 이 붙는다.
- **Entity** (`@Entity`, `@Table`): DB 테이블 한 행을 자바 객체로 다룬다. 상태 바꾸기는 `booking.start()` 처럼 엔티티 메서드로만 하고, 허용되지 않는 변경이면 `INVALID_STATE` 오류를 던진다.
- **DTO** (`record …(…)`): 요청·응답 모양. `record` 는 값만 담는 짧은 클래스 선언이다(필드·생성자·getter 가 자동으로 생긴다).

### 3-2. 스프링이 객체를 만들어 넣어 주는 방식 (생성자 주입)
`@Service`·`@Component`·`@RestController` 가 붙은 클래스는 스프링이 하나씩 만들어 둔다. 생성자 인자에 다른 클래스를 적으면 스프링이 만들어 둔 것을 넣어 준다.
그래서 거의 모든 클래스의 생성자는 받은 값을 필드에 담기만 한다. 파일마다 따로 설명하지 않는다.

### 3-3. 트랜잭션 (`@Transactional`)
- 메서드 안의 DB 작업을 **한 묶음**으로 처리한다. 끝까지 성공하면 한꺼번에 저장(커밋)하고, **실행 중 예외(RuntimeException)가 밖으로 나가면 묶음 전체를 되돌린다(롤백).**
  이 프로젝트의 `ApiException` 도 RuntimeException 이라 기본으로는 되돌린다.
- **예외가 있다:** `@Transactional(noRollbackFor = ApiException.class)` 가 붙은 메서드는 `ApiException` 을 던져도 그때까지 바꾼 값을 저장한다 (예: 라이브 일시정지에서 "확인 실패" 를 알리면서도 재시도 표시는 남겨야 할 때). 주석이 "예외가 나면 되돌린다" 고 해도 그 메서드에 이 설정이 있는지 확인한다.
- 클래스에 붙이면 그 클래스의 public 메서드마다 적용된다. 메서드에 따로 붙인 설정이 있으면 그것이 우선한다.
- 이미 묶음 안에서 부르면 새로 열지 않고 **바깥 묶음에 합쳐진다**(안쪽에서 난 예외가 바깥 전체를 되돌릴 수 있다). `propagation = MANDATORY` 는 "반드시 바깥 묶음 안에서만 불러라", `REQUIRES_NEW` 는 "따로 새 묶음을 연다".
- `readOnly = true` = 읽기만 하는 묶음.
- **같은 클래스 안에서 자기 메서드를 부르면 그 메서드의 `@Transactional` 설정은 적용되지 않는다** (스프링이 바깥에서 오는 호출에만 끼어들기 때문). 그래서 건마다 따로 저장해야 하는 스케줄러는 `TransactionTemplate` 으로 묶음을 직접 연다.
- 묶음 안에서 엔티티 값을 바꾸면 `save` 를 부르지 않아도 커밋 때 DB 에 반영된다.

### 3-4. 잠금 (동시에 같은 행을 바꾸는 요청 막기)
- `lockForUpdate`, `findForUpdate`, SQL 의 `FOR UPDATE` = 그 행을 잠그고 읽는다. 다른 요청은 앞의 묶음이 끝날 때까지 기다린다. `FOR SHARE` = 읽기끼리는 같이 잡을 수 있지만 쓰기는 기다리게 한다.
- 여러 행을 잠글 때는 **항상 같은 순서**로 잠근다(예: 매니저 → 예약 → 콜, 또는 id 오름차순). 순서가 섞이면 서로 기다리다 멈춘다(데드락).
- `holdLive(userId)`: 탈퇴 처리와 겹치는 쓰기(예약 생성·문의·주소 저장 등)가 줄을 서게 한다. 탈퇴가 먼저 끝났으면 뒤의 쓰기는 `UNAUTHENTICATED`(401)로 끝나고 앱은 로그아웃한다. 쓰기가 먼저면 탈퇴 쪽이 방금 저장된 것을 보고 처리한다.

### 3-5. 오류
- 서버 코드는 `ApiException(ErrorCode.X, "화면에 보여도 되는 한국어")` 만 던진다. `GlobalExceptionHandler` 가 이것을 `{ code, message }` 응답으로 바꾸고 HTTP 상태는 `ErrorCode` 에서 정한다.
- 남의 리소스를 요청하면 "권한 없음" 대신 `NOT_FOUND` 를 준다 — 있는지 없는지도 알려 주지 않기 위해서다.

### 3-6. 아웃박스 (푸시·외부 호출을 안전하게 보내는 방법)
- 업무 처리 중에는 `outbox.publish(new 이벤트(…))` 로 **DB 에 "보낼 일" 한 행만 적는다**. 업무 데이터와 같은 묶음이라, 업무가 되돌려지면 이 행도 사라진다.
- 커밋된 뒤 `OutboxDispatcher` 가 행을 꺼내 알맞은 처리기(`OutboxHandler`, 예: `PushHandler`)에 넘긴다 (5초마다 + 커밋 직후 바로). 실패하면 간격을 늘려 다시 시도하고, 여러 번 실패하면 `failed` 로 남긴다 (횟수는 `OutboxDispatcher` 에 있다).
- 이벤트 모양은 `common/events` 의 record 들이다.

### 3-7. 같은 요청 두 번 막기 (멱등 키)
- 앱은 예약 생성·콜 수락·작업 시작/완료 같은 쓰기 요청에 `Idempotency-Key` 헤더(무작위 UUID)를 붙이고, 재시도할 때 **같은 키**를 보낸다.
- 서버의 `Idempotent.run(key, …)` 은 처음 온 요청만 실행하고 응답을 저장한다. 같은 키가 다시 오면 저장한 응답을 그대로 돌려준다. 같은 키로 다른 내용을 보내면 `IDEMPOTENCY_MISMATCH`, 첫 요청이 아직 처리 중이면 `IN_PROGRESS` 다.

### 3-8. 시간·금액
- 시각은 `Instant`(UTC)로 저장하고, "오늘"·"내일"·요일 같은 판단은 `AppClock`(Asia/Seoul) 으로만 한다. 테스트는 `AppClock` 을 고정된 시각으로 만들어 쓴다.
- 화면에 보이는 날짜·금액 문자열은 `KoreanFormat` 에서만 만든다. 금액은 `long`(원 단위).
- 예약할 때 금액·수수료·소요 시간을 예약 행에 고정해 두고, 콜·정산은 그 값으로만 계산한다.

### 3-9. 스케줄러 (`@Scheduled`)
- `@Scheduled(fixedDelay = …)` = 앞 실행이 끝난 뒤 그 시간만큼 쉬고 다시, `@Scheduled(cron = "초 분 시 일 월 요일", zone = "Asia/Seoul")` = 정해진 시각마다 스프링이 자동으로 부른다.
- 어떤 스케줄러가 있는지는 `BACKEND_PLAN.md` §2.5 표를 본다 (주석에 개수를 적지 않는다 — 늘어나면 틀린다).
- 실패할 수 있는 바깥 호출(LiveKit·푸시·저장소)에는 다시 맞추는 스케줄러가 따로 있다(예: `LiveReconciler` 1분마다).

### 3-10. 설정·비밀값·DB 변경
- `application.yml` 의 `${VAR}` 는 환경변수에서 읽는다. 비밀값은 `.env` 에만 있다(커밋하지 않는다).
- `app_settings` 테이블은 운영 중에 바꿀 수 있는 값(콜 유효 시간, 안내 문구 등)이다. 코드에서는 `AppSettingsService` 로 읽는다.
- DB 구조는 `backend/src/main/resources/db/migration/V번호__이름.sql` 로만 바꾼다(Flyway). **이미 커밋된 파일은 한 글자도 고치지 않는다** — 고치면 서버가 켜질 때 체크섬 검사에서 멈춘다. 되돌리기도 새 번호로 한다.

### 3-11. 권한
- `SecurityConfig`: `/customer/**` 는 고객, `/manager/**` 는 매니저만. 운영자 API(`/admin/**`)는 `X-Admin-Token` 헤더로 확인한다.
- 그 위에 Service 가 "이 예약이 이 사람 것인가" 를 한 번 더 확인한다.

### 3-12. 서버 테스트
- **단위 테스트** (`…Test.java`): Mockito 로 의존 객체를 가짜로 만든다. `when(x.f()).thenReturn(…)` = 이렇게 불리면 이 값을 돌려줘라, `verify(x).f(…)` = 정말 이렇게 불렸는지 확인.
- **컨트롤러 슬라이스** (`@WebMvcTest`): 서버 전체를 띄우지 않고 주소·JSON 모양·권한만 확인한다.
- **통합 테스트** (`…IT.java`, `@SpringBootTest` + Testcontainers): 진짜 Postgres 를 Docker 로 띄워 전체 흐름을 돌린다. Docker 가 없는 컴퓨터에서는 `-DskipITs` 로 건너뛴다.
- `ArchitectureTest` 는 패키지끼리 거꾸로 참조하지 않는지(순환 의존) 검사한다.

---

## 4. 주석을 읽고 쓸 때
- 좋은 주석은 **역할 → 왜 중요한지 → 지켜야 할 조건 → 실패하면 어떻게 되는지** 를 말한다. 이 네 가지가 있는 주석은 코드를 고칠 때 기준으로 삼아도 된다.
- 개수("스케줄러 N개")나 "쓰는 곳 전부" 목록은 코드가 바뀌면 금방 틀린다. 주석에 "예:" 가 붙은 사용처는 대표 예시일 뿐이다 — 전부 찾으려면 검색한다.
- 주석과 코드가 다르면 **코드가 실제 동작이다.** 어느 쪽이 의도인지는 `PROGRESS.md`·`BACKEND_PLAN.md` 의 결정 기록으로 확인한다.
