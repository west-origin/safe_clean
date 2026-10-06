# 안심클린 백엔드 (Java 21 + Spring Boot 4.1)

두 앱(고객·매니저)이 부르는 **유일한 API 서버**. 사양은 루트 `BACKEND_PLAN.md`, 규칙은 `CLAUDE.md` §11, 회차는 `PROGRESS.md` "백엔드" 절.
계약·규칙·운영 문서는 `docs/`: [api.md](docs/api.md) · [domain.md](docs/domain.md) · [runbook.md](docs/runbook.md).

## 실행
```bash
cp .env.example .env                  # 값 채우기 (docs/runbook.md "외부 서비스 설정")
./mvnw -q verify                      # 컴파일 + 단위·슬라이스 + 통합(*IT, Docker 필요)
./mvnw -q test                        # Docker 없이: 단위·슬라이스만
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
curl -s localhost:8080/actuator/health
```
JDK 21 만 있으면 된다 (`./mvnw` 가 Maven 을 받는다). Android Studio 에 들어 있는 JDK(jbr)는 버전이 올라가 21 이 아닐 수 있으니(2026-10 기준 25) 쓰기 전에 `java -version` 으로 확인한다.

### macOS 에서 실행
git 으로 넘어오지 않는 것이 두 가지다: **`backend/.env`** (`.gitignore`) 와 **IntelliJ 설정** (`.idea/`). 새 Mac 에서는 아래 순서대로.

1. **JDK 21 설치** — 둘 중 하나. 설치 후 `/usr/libexec/java_home -v 21` 이 경로를 출력하면 된다.
   - IntelliJ: *File → Project Structure → SDK → Add SDK → Download JDK* 에서 버전 21
   - Homebrew: `brew install openjdk@21` 후 안내대로 `sudo ln -sfn …/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`
2. **`.env` 준비** — 기존 `.env` 를 메신저·메일·git 이 아닌 경로(USB, 비밀번호 관리자 등)로 옮기거나, `cp .env.example .env` 후 값을 채운다. `.env` 는 반드시 `backend/` 안에 둔다 (설정이 작업 디렉터리 기준 `./.env` 를 읽는다).
3. **터미널에서 실행**
   ```bash
   cd backend
   JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   curl -s localhost:8080/actuator/health    # {"status":"UP"} 이면 성공
   ```
   `./mvnw: Permission denied` 가 나오면 `chmod +x mvnw` (git 에는 실행 권한으로 저장돼 있다).
4. **IntelliJ 에서 실행** — **`backend` 폴더를 프로젝트로 연다** (루트 `safe_clean` 이 아니라).
   - `pom.xml` 을 Maven 프로젝트로 바로 인식하고, `backend/.run/` 의 실행 구성 **ApiApplication (local)** 이 목록에 나타난다.
   - *Project Structure → Project → SDK* 를 JDK 21 로 지정한 뒤 ▶.
   - 저장소 루트를 열었다면 Maven 이 인식되지 않는다. 그때는 `backend/pom.xml` 을 우클릭 → *Add as Maven Project* 하고, 실행 구성의 *Working directory* 를 `backend` 로 바꾼다 (아니면 `.env` 를 못 찾아 `DB_URL` 오류로 기동 실패).

## 구조 (`src/main/java/kr/ansimclean/api/`)
```
ApiApplication.java
common/            기능과 무관한 공통 코드만 (비즈니스 로직 금지)
  config/          AppProperties(app.*) · CorsConfig · ClockConfig
  security/        SecurityConfig(경로별 권한) · SupabaseJwtDecoderConfig · JwtToCurrentUserConverter · CurrentUser · CurrentUserResolver
  error/           ErrorCode · ApiException · ErrorResponse · GlobalExceptionHandler  → { error: { code, message } }
  format/          KoreanFormat — 화면 문자열은 여기서만
  time/            AppClock (Asia/Seoul, 테스트에서 고정)
  web/             RequestLogFilter (method path status ms userId)
catalog/           카테고리·옵션·주의사항 칩·문구·사유·app_settings (읽기 전용)   ✅ B1
support/           약관 ✅ B1 · FAQ·공지·문의 (B6)
auth/ profile/ booking/ offer/ job/ live/ settlement/ schedule/ managerhome/ payment/ push/   (B2~B6)
```
기능 패키지 안은 `XController`(입출력) · `XService`(규칙·트랜잭션) · `XRepository`(JPA) · 엔티티 · `dto/`(record). 의존 방향은 `ArchitectureTest` 가 강제한다.

## DB
`src/main/resources/db/migration/` 의 Flyway SQL 이 전부다 (`ddl-auto=validate`). 커밋된 파일은 수정하지 않는다.
- `V001__schema.sql` 스키마 36 테이블 (BACKEND_PLAN.md §4)
- `V002~V005` seed — `node scripts/backend-mock-export.mjs seed` 로 앱 `mock.js` 에서 만들었다(B1 한 번). 문구 변경은 새 버전으로.
- 테스트 기대값 `src/test/resources/mock-shapes/*.json` 은 `node scripts/backend-mock-export.mjs shapes` 로 언제든 다시 만든다.

## 테스트
| 층 | 파일 이름 | 실행 |
|---|---|---|
| 단위 | `*Test` (Mockito) | `./mvnw test` |
| 슬라이스 | `*Test` + `@WebMvcTest` | `./mvnw test` |
| 통합 | `*IT` + Testcontainers Postgres | `./mvnw verify` (Docker) |
| 구조 | `ArchitectureTest` (ArchUnit) | `./mvnw test` |
