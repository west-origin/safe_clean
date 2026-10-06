package kr.ansimclean.api.auth;

import java.util.UUID;

/**
 * 탈퇴를 막아야 하는 조건 (예: 진행 중 예약, 활성 정기청소 — B3 의 booking 패키지가 구현해 등록한다).
 * 막아야 하면 사용자에게 보여줄 한국어 문구를 돌려주고, 괜찮으면 null.
 * 구현체 예: booking.BookingDeletionGuard(끝나지 않은 예약·활성 정기청소·맡은 작업), settlement.SettlementDeletionGuard(지급 안 된 정산).
 * 스프링이 구현체를 모두 모아 AuthService 에 List 로 넣어 주고, AuthService.deleteAccount 가 트랜잭션·프로필 잠금 안에서 하나씩 묻는다.
 * 하나라도 문구를 돌려주면 탈퇴는 INVALID_STATE(409) 로 멈추고 그 문구가 오류 응답의 message 로 나간다.
 * auth 가 booking·settlement 를 직접 부르지 않고 이 인터페이스를 거치므로 패키지끼리 서로 물지 않는다 (AccountPurge 와 같은 방식).
 */
public interface AccountDeletionGuard {
    // role 은 "customer" 또는 "manager" (Roles) — 역할마다 막는 조건이 다르다
    String blockReason(UUID userId, String role);
}
