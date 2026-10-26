# Supabase 서울 이전 결과 (2026-10-16)

- 원본: `ckvanmhgepdlxvfhdxes` / `safe_clean_test` / 도쿄 / PostgreSQL 17.6.
- 대상: `qztjikulkicryvmgfuaw` / `safe_clean` / 서울 / PostgreSQL 17.11.
- 새 프로젝트가 비어 있음을 확인한 뒤 실제 원본 DB 카탈로그로 구조를 복원했다.
- 앱 테이블 40개, 658행 이전. 예약 41건, 프로필 7명, Flyway 이력 48건 포함.
- Auth 사용자 7명 및 비밀번호 해시·identities·sessions·refresh_tokens 등 156행 이전.
- `public`·`auth` 66개 테이블의 행 수와 정렬된 전체 행 체크섬이 원본과 일치.
- public 컬럼·제약조건 114개·인덱스 84개·RLS 상태 일치. 외래키 60개 참조 오류 0건.
- public.booking_status_history_id_seq와 auth.refresh_tokens_id_seq 현재값 보존.
- 원본에는 사용자 정의 역할·public view·enum/domain·Vault 데이터·Cron·public Realtime publication이 없었음.
- 원본 DB는 수정·삭제하지 않았다. `backend/.env`는 서울 프로젝트로 전환 완료했다.

## 권한

원본의 anon/authenticated 광범위 테이블 권한을 재부여하려던 요청은 자동 승인 검토에서 거부됐다.
대신 두 역할과 PUBLIC의 직접 테이블 접근 권한을 제외하고 서버용 service_role 권한을 복원했다.
앱은 Spring API만 사용하므로 이에 맞는 구성이다. 일반 역할의 public 테이블 권한은 0건이다.
업무 테이블 39개는 기존 RLS를 유지한다. Flyway 이력은 원본처럼 RLS 대신 권한 회수로 보호한다.

## 남은 작업

1. **Storage 파일 3개 복사 완료**: repair-photos 비공개 버킷을 생성하고 파일 3개(각 73바이트)를
   원본 경로 그대로 복사했다. 대상에서 다시 내려받은 파일의 SHA256이 원본과 모두 일치한다.
   대상 DB에서도 storage.objects 3개와 비공개 버킷 1개를 확인했다.
2. **Auth 설정 완료**: 사용자의 기존 방식 적용 승인 후 Phone 활성화, 신규 직접 가입 비활성화,
   Supabase 전화번호 추가 확인 비활성화를 저장했다. 기존 구조대로 Twilio에는 더미 값을 넣고
   문자 인증은 Spring 서버가 담당한다. API에서 phone=true, disable_signup=true,
   phone_autoconfirm=true를 확인했다. 실제 앱 로그인은 서버 재시작 후 별도 검증해야 한다.
3. **서버 연결 전환 완료**: 새 service_role 키의 대상 프로젝트와 Auth 관리 API·REST 접근 확인.
   대상 REST profiles 조회는 7행이다. 수정된 서울 Session pooler 주소로 실제 DB 접속을 확인했다.
   `backend/.env`의 DB URL(JDBC 형식·SSL 필수로 정리)·사용자·비밀번호·Supabase URL·service_role 키·
   JWKS 모드·Storage endpoint·region을 서울 값으로 전환했다. 전환된 backend/.env로 재접속해
   프로필 7명·예약 41건·Auth 사용자 7명·Flyway 48건을 확인했다.
   공식 Supabase Root 2021 CA로 클라이언트 TLS 암호화와 인증서 검증을 확인했다.
   `pg_stat_ssl`은 풀러 이후 DB 연결을 나타내므로 클라이언트 인증서 검증 결과와 구분했다.
   기존 환경파일은 git 제외 백업 폴더의 `backend-env-before-seoul.env`에 보존했다.
   Spring 서버 실행·실제 앱 로그인은 아직 검증하지 않았다. 재시작 후 반영된다.
   이후 Cloud Run 전환 작업에서 로컬 JDK 21을 찾아 백엔드 테스트 525개를 검증했다.

백업·복원 SQL·검증 체크섬·원본 사진은 git에서 제외된 루트 `.env.supabase-migration-20261016/`에 있다.
민감한 개인정보와 비밀번호 해시가 포함되므로 공유하거나 커밋하지 않는다.
Storage 복사 스크립트 `finish-storage.ps1` 실행·파일 검증 완료.
`verify-target-db.mjs`와 임시 pg@8.23.1 클라이언트를 git 제외 폴더에 준비했다.
직접 DB 호스트는 IPv6 주소만 반환하며 현재 환경에 IPv6 기본 경로가 없어 Session pooler가 필요하다.
로그인된 대시보드에서 Auth 설정 적용·저장 및 API 재검증을 완료했다.
실제 Spring 서버 재시작과 앱 로그인 검증이 남아 있다.

## 보안 검사 결과

오류 등급 항목은 없었다. RLS 정책 없음 39개는 서버만 접근하는 기존 설계다.
아래 경고는 별도 운영 점검 대상으로 기록했다. 이번 데이터 이전에서는 관련 설정을 임의 변경하지 않았다.

- [set_updated_at 함수의 고정되지 않은 search_path](https://supabase.com/docs/guides/database/database-linter?lint=0011_function_search_path_mutable)
- [public 스키마의 btree_gist 확장](https://supabase.com/docs/guides/database/database-linter?lint=0014_extension_in_public)
- [새 프로젝트의 유출 비밀번호 보호 비활성화](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection)
