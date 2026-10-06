package kr.ansimclean.api.settlement;

import kr.ansimclean.api.common.security.Roles;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SettlementDeletionGuard(탈퇴 전 검사 — 지급 안 된 정산이 남은 매니저의 탈퇴를 막는다) 단위 테스트.
 * 정산 저장소는 Mockito 가짜라, '대기(pending) 정산이 있는가' 의 답을 테스트가 정한다.
 * blockReason 결과: 막는 문구(탈퇴 거절) 또는 null(막지 않음)
 */
class SettlementDeletionGuardTest {
    SettlementRepository repo = mock(SettlementRepository.class);
    SettlementDeletionGuard guard = new SettlementDeletionGuard(repo);
    UUID manager = UUID.randomUUID();

    // 대기 정산이 있을 때: 매니저 역할이면 MANAGER_BLOCKED 문구, 같은 id 를 고객 역할로 물으면 null(고객은 정산이 없다).
    // 대기 정산이 없어지면 매니저도 null
    @Test
    void 미지급_정산이_남은_매니저는_탈퇴할_수_없고_고객은_해당_없다() {
        when(repo.existsByManagerIdAndStatus(manager, Settlement.PENDING)).thenReturn(true);
        assertThat(guard.blockReason(manager, Roles.MANAGER)).isEqualTo(SettlementDeletionGuard.MANAGER_BLOCKED);
        assertThat(guard.blockReason(manager, Roles.CUSTOMER)).isNull();
        when(repo.existsByManagerIdAndStatus(manager, Settlement.PENDING)).thenReturn(false);
        assertThat(guard.blockReason(manager, Roles.MANAGER)).isNull();
    }
}
