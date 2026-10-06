package kr.ansimclean.api.payment;

import kr.ansimclean.api.auth.AccountPurge;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 부르는 곳: auth.DeletedAccountPurger (매일 04:20, 계정마다 트랜잭션 하나). AccountPurge 를 구현한 빈을 목록으로 받아 차례로 부른다.
// 인터페이스로 두어 auth 패키지가 payment 패키지를 직접 알 필요가 없다 (AccountPurge 설명 참고).
// PG 연동 전에는 payment_methods 행이 생기지 않아, 지금은 지울 것이 없어도 그대로 끝난다.
// 주의: 결제 수단은 탈퇴 때(AuthService.deleteAccount)가 아니라 이 5년 파기 때만 지워진다. 행이 없는 지금은 문제가 없지만,
// PG 를 붙일 때는 탈퇴 트랜잭션에서 빌링키를 바로 지우기로 했다 (PG 쪽 해지는 바깥 호출이라 아웃박스로 — PROGRESS.md 2026-09-27 메모).
// 여기서 예외가 나면 그 계정의 파기 전체가 되돌려지고 다음 날 다시 돈다 (AccountPurge 설명).
/** 탈퇴 + 5년: 고객의 결제 수단(빌링키·카드 표시)을 지운다. 결제 행(금액·PG 거래 id)은 예약 기록이라 남기고 수단 참조만 끊는다 */
@Component
public class PaymentAccountPurge implements AccountPurge {
    private final PaymentMethodRepository methods;
    private final PaymentRepository payments;

    public PaymentAccountPurge(PaymentMethodRepository methods, PaymentRepository payments) {
        this.methods = methods;
        this.payments = payments;
    }

    /** 고객 계정이면 결제 행의 수단 참조를 끊고 → 결제 수단 행을 지운다. 다시 불려도 이미 비었으면 0건이라 결과가 같다 */
    @Override
    public void purge(UUID userId, String role) {
        if (!Roles.CUSTOMER.equals(role)) return; // 결제 수단은 고객에게만 있다
        // 순서가 중요하다: payments.payment_method_id 가 payment_methods 를 외래 키로 가리키고 on delete 규칙이 없어(V001),
        // 참조를 먼저 비우지 않으면 수단 행 삭제가 DB 오류로 실패한다
        payments.detachMethodsOf(userId);
        methods.deleteByCustomerId(userId);
    }
}
