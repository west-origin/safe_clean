package kr.ansimclean.api.profile;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 매니저 계정 상태 (rbac.md §3, V065): 가입 = pending, 승인 = active, 탈퇴 = withdrawn. 콜 후보는 active 뿐 */
class ManagerProfileStatusTest {
    static final Instant T0 = Instant.parse("2026-11-12T00:00:00Z");
    static final Instant T1 = Instant.parse("2026-11-13T00:00:00Z");

    @Test
    void 가입하면_승인_대기다() {
        ManagerProfile m = ManagerProfile.create(UUID.randomUUID(), T0);
        assertThat(m.getStatus()).isEqualTo(ManagerProfile.PENDING);
        assertThat(m.getStatusChangedAt()).isEqualTo(T0);
        assertThat(m.isApproved()).isFalse();
    }

    @Test
    void 승인하면_활동_중이고_콜_후보다() {
        ManagerProfile m = ManagerProfile.create(UUID.randomUUID(), T0);
        m.approve(List.of("서울특별시 강남구"), "서울특별시", LocalDate.of(2026, 11, 13), T1);
        assertThat(m.getStatus()).isEqualTo(ManagerProfile.ACTIVE);
        assertThat(m.getStatusChangedAt()).isEqualTo(T1);
        assertThat(m.isApproved()).isTrue();
    }

    @Test
    void 탈퇴하면_퇴사_상태라_콜_후보가_아니다() {
        ManagerProfile m = ManagerProfile.create(UUID.randomUUID(), T0);
        m.approve(List.of("서울특별시 강남구"), "서울특별시", LocalDate.of(2026, 11, 12), T0);
        m.withdraw(T1);
        assertThat(m.getStatus()).isEqualTo(ManagerProfile.WITHDRAWN);
        assertThat(m.getStatusChangedAt()).isEqualTo(T1);
        assertThat(m.isApproved()).isFalse();
    }
}
