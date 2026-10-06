package kr.ansimclean.api.auth;

import java.util.UUID;

/**
 * 탈퇴 + 5년 뒤 남은 개인정보 파기의 한 부분 (BACKEND_PLAN §13-22). auth 가 의존하지 않는 기능(예약·결제)이 구현해 등록한다 —
 * AccountDeletionGuard 와 같은 방식(패키지 순환 없이). auth 가 이미 의존하는 기능(프로필·일정·지원)은 DeletedAccountPurger 가 직접 부른다.
 * DB 만 바꾼다(외부 효과 없음 — 사진·녹화는 탈퇴 때 지웠다). 같은 트랜잭션에서 불리며 다시 불려도 결과가 같아야 한다
 * 구현체 예: booking.BookingAccountPurge(예약·정기청소의 개인정보 칸), payment.PaymentAccountPurge(결제 수단) — 둘 다 고객 계정일 때만 일한다.
 * 스프링이 구현체를 모두 모아 DeletedAccountPurger 에 List 로 넣어 준다.
 * 예외를 던지면 그 계정의 파기 전체(다른 구현체·파기 표시 포함)가 되돌려지고 다음 날 처음부터 다시 돈다 (DeletedAccountPurger).
 */
public interface AccountPurge {
    // DeletedAccountPurger 가 파기 대상 계정마다 한 번씩 부른다. role("customer"|"manager")로 할 일을 가른다
    void purge(UUID userId, String role);
}
