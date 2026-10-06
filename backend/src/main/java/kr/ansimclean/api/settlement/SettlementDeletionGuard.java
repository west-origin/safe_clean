package kr.ansimclean.api.settlement;

import kr.ansimclean.api.auth.AccountDeletionGuard;
import kr.ansimclean.api.common.security.Roles;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 탈퇴 차단: 지급 안 된 정산이 남은 매니저는 탈퇴할 수 없다 (검증 검토 — withdraw 가 계좌 정보를 바로 지워 정산 행은 있는데 보낼 계좌가 없었다).
 * 정산이 모두 완료된 뒤 다시 시도하면 된다. 고객에겐 해당 없음
 */
// AccountDeletionGuard 구현이라 AuthService 가 받는 가드 목록(List<AccountDeletionGuard>)에 자동으로 들어간다
@Component
public class SettlementDeletionGuard implements AccountDeletionGuard {
    // 탈퇴가 막힐 때 오류 응답 message 로 그대로 나가는 문구
    static final String MANAGER_BLOCKED = "아직 지급되지 않은 정산이 있어 탈퇴할 수 없어요. 정산이 완료된 뒤 다시 시도해주세요";

    private final SettlementRepository settlements;

    public SettlementDeletionGuard(SettlementRepository settlements) {
        this.settlements = settlements;
    }

    /** 매니저이고 pending 정산이 하나라도 있으면 막는 문구, 아니면 null(탈퇴를 막지 않는다). AuthService.deleteAccount 가 탈퇴 트랜잭션 안에서 부른다 */
    @Override
    public String blockReason(UUID userId, String role) {
        if (!Roles.MANAGER.equals(role)) return null;
        return settlements.existsByManagerIdAndStatus(userId, Settlement.PENDING) ? MANAGER_BLOCKED : null;
    }
}
