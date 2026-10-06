package kr.ansimclean.api.live;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// manager_devices 테이블 저장소 (키 = 매니저 id). 쓰는 곳 예: LiveWebhookService(Ingress id 로 기기 찾기), DeviceService(등록 줄 세우기)
public interface ManagerDeviceRepository extends JpaRepository<ManagerDevice, UUID> {
    /** 웹훅이 알려 준 Ingress id 로 어느 매니저의 바디캠인지 찾는다 (LiveWebhookService) */
    Optional<ManagerDevice> findByIngressId(String ingressId);

    /** 일시정지 때 잠근 채 남은 바디캠 (재조정기의 기기 단위 정리, 자체 점검 #52) */
    List<ManagerDevice> findByIngressEnabledFalse();

    /** 같은 매니저의 등록 요청을 트랜잭션 안에서 줄 세운다 — 겹치면 LiveKit Ingress 가 둘 만들어진다 */
    // pg_advisory_xact_lock = 행이 아니라 '번호' 에 거는 잠금으로,
    // 같은 번호(매니저 id 를 hashtext 로 바꾼 값)를 잡으려는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 기다린다.
    // 기기 행이 아직 없을 때(첫 등록)도 잠글 수 있다. 부르는 곳: DeviceService 등록. 반환값은 쓰지 않는다
    @Query(value = "select pg_advisory_xact_lock(hashtext(cast(:managerId as text)))", nativeQuery = true)
    Object lockManager(UUID managerId);
}
