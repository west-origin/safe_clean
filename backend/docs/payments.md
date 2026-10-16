# 방문 회차 결제

2026-10-17 사용자 요청으로 기존 결제 골격을 토스페이먼츠 일회성 카드 결제로 확장했다.
예약 생성·매칭은 유지하며 고객이 예약 완료 또는 내 예약의 결제 화면에서 회차별로 결제한다.
정기청소 전체 선결제·자동결제·빌링키 발급은 구현 범위에 포함하지 않는다.

## 데이터와 경계

- `payments`: PG 주문번호 = UUID `id`, 예약 가격 스냅샷, PG paymentKey, 상태, 누적 환불액, 재조정 시각.
- `payment_refunds`: 취소된 예약의 잔액 전체 환불 의도. 결제별 한 행이며 UUID를 PG 멱등키로 재사용한다.
- `payment_methods`: 기존 빌링 골격만 보존한다. 이번 연동으로 카드를 저장하지 않는다.
- `bookings.payment_status`: 결제 원장과 같은 트랜잭션에서 갱신하는 요약. 부분 환불은 잔액이 있어 `paid`로 표시한다.
- `outbox_events`: `PaymentWorkRequested`를 업무 저장과 함께 기록한다. 결제 키는 payload에 복제하지 않는다.

Controller → CheckoutService(DB) → Repository를 따른다. CheckoutService → BookingService 방향만 허용한다.
잠금 순서는 예약 → 결제 → 환불. 결제를 잠그기 전에 예약 id만 projection으로 읽어 오래된 JPA 엔티티가 잠금 뒤 상태를 덮지 않도록 한다.
PaymentWorkHandler는 아웃박스 트랜잭션을 `NOT_SUPPORTED`로 중단하고 PG를 호출한다. DB 변경은 CheckoutService의 짧은 별도 트랜잭션으로 커밋한다.
새 결제/승인 요청은 ProfileService.holdLive 공유 잠금으로 탈퇴와 직렬화한다. PaymentDeletionGuard는 확인 중 승인·환불이 남은 고객의 탈퇴를 차단한다.
PG 호출 성공 후 프로세스가 죽거나 아웃박스의 sent 기록이 실패하면 조회 API로 완료를 확인하므로 새 청구를 만들지 않는다.

## API

모든 고객 API는 ROLE_CUSTOMER + 예약 소유권 검사(남의 자원은 404).

| 경로 | 요청 | 결과 |
|---|---|---|
| POST `/api/v1/customer/bookings/{bookingId}/checkout` | `Idempotency-Key`, `{}` | 서버 저장 금액으로 결제 준비. 실패 확정 전에는 기존 주문 재사용 |
| GET `/api/v1/customer/payments/{id}` | 없음 | 최신 결제·환불 상태 |
| POST `/api/v1/customer/payments/{id}/confirm` | `Idempotency-Key`, `{ paymentKey, amount }` | 금액 일치 확인 후 승인 의도를 영속화하고 `authorized` 반환 |
| POST `/api/v1/payments/webhooks/toss` | 토스 `PAYMENT_STATUS_CHANGED` | orderId만 사용해 재조회 작업을 저장. 200 반환 |

응답: `{ id, bookingId, orderId, amount, currency: "KRW", status, refundedAmount, clientKey, orderName, payable, refundStatus }`.
`refundStatus`는 null/pending/done. paymentKey·시크릿 키·카드 원문은 응답하지 않는다.
`authorized`는 PG 승인 확인 전의 서버 처리 중 상태다. 클라이언트는 GET 결과로만 성공을 표시한다.

일반 결제 웹훅에는 서명 헤더를 가정하지 않는다. 본문의 상태/키/금액을 신뢰하지 않고 상점 시크릿 키로 PG 주문을 재조회한다.
알 수 없는 주문은 무시하고 주문당 30초에 한 번만 작업을 예약한다. 원문은 DB/로그에 남기지 않는다.
검증 기준은 주문번호·키·총액·KRW·잔액 범위다. 환불액을 줄이는 오래된 조회 응답은 무시한다.

## 실패·환불·재조정

PaymentReconciler는 1분마다 ShedLock으로 한 인스턴스만 실행한다. 승인 키가 있는 미확정 결제, 확인 주기가 지난 결제, 취소된 유료 예약을 최대 50건씩 아웃박스에 넣는다.
재조정/아웃박스 재시도가 승인 API를 다시 호출해도 동일한 PG 멱등키를 사용한다.
네트워크 오류·404 조회만으로 실패 확정하지 않는다. 확인 중인 주문은 새 결제를 차단한다.
잘못된 키 등으로 결과를 확인할 수 없는 주문은 운영자가 PG 대시보드와 주문번호를 대조해야 한다. DB를 임의로 paid/failed로 바꾸지 않는다.

예약 취소(고객·전체 시리즈·시스템) 후 이미 지불된 잔액은 전체 환불한다. 취소와 승인이 겹치면 승인 적용 뒤 예약 상태를 다시 읽어 환불한다.
환불이 실패하면 pending 행과 동일 멱등키로 재시도한다. 토스 대시보드에서 발생한 부분 환불은 원장에 반영한다.
고객이 임의 금액을 환불받는 API는 제공하지 않는다. 매니저 정산은 기존 작업 완료/운영자 지급 절차를 유지한다.

## 활성화

1. 토스 상점에서 **API 개별 연동 키**를 준비한다. test_ck/test_sk 또는 live_ck/live_sk 쌍을 함께 사용한다.
2. 서버 환경 파일/Secret Manager에 `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY`를 설정한다. 앱 환경 파일에는 시크릿 키를 넣지 않는다.
3. 서버 배포 시 Flyway V052가 적용된다. 기존 마이그레이션은 수정하지 않는다.
4. 토스 개발자센터에 HTTPS API의 `/api/v1/payments/webhooks/toss`와 PAYMENT_STATUS_CHANGED를 등록한다.
5. 테스트 키로 웹 결제창의 승인·거절·중복 클릭·취소·환불·콜백 유실을 확인한 뒤 운영 키로 전환한다.
6. 웹 배포는 `/payments/checkout` SPA 경로 복귀가 가능해야 한다. Capacitor의 외부 카드/간편결제 앱 전환·복귀는 Android/iOS 실기기에서 별도 확인해야 한다. 현재 이 실기기 검증은 미실행이다.

키가 없거나 test/live가 섞이면 결제 준비를 거절한다. 예전 NoopPaymentGateway는 이번 결제 경로에서 호출하지 않는다.
현재 운영 서버·DB·앱 설치본에는 이 변경을 적용하지 않았다. 자동 배포하거나 테스트 키를 임의 상점에서 가져오지 않는다.

## 검증

- Java: `mvnw -DskipITs verify` (상태기계, 권한/JSON, 금액/키 검증, 게이트웨이 요청, 승인/취소 경합, 아웃박스 등록, 패키지 경계).
- Postgres 통합: `mvnw -Dit.test=CheckoutIT verify` (Docker 필요). 실제 Flyway/JPA/멱등/아웃박스/예약/환불 연결을 검증한다.
- Docker 없이 스키마 검증: `npm install --prefix artifacts/payment-validation --no-audit --no-fund --ignore-scripts --save-exact @electric-sql/pglite@0.5.10`, 이어서 `node scripts/verify-payment-schema.mjs`.
  원본 마이그레이션 50개 전체, 중복 청구/환불 제약, 음수·초과 환불, RLS·권한 회수, 애플리케이션의 실제 재조정 SQL을 로컬 PostgreSQL WASM에서 실행한다. Spring 연결과 다중 연결 경합은 이 검증에 포함되지 않는다.
- 프론트: `npm test`, `npm run lint`, `npm run check:tokens`, `npm run build`.

공식 근거: [토스 결제창](https://docs.tosspayments.com/guides/v2/payment-window/integration), [승인·조회·취소 API](https://docs.tosspayments.com/reference), [웹훅](https://docs.tosspayments.com/guides/v2/webhook).
