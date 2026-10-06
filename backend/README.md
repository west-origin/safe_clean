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
JDK 21 만 있으면 된다 (`./mvnw` 가 Maven 을 받는다). macOS 에 JDK 가 없고 Android Studio 가 있으면 `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

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
