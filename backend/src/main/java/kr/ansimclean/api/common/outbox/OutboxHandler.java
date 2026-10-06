package kr.ansimclean.api.common.outbox;

import java.util.Set;

/**
 * 이벤트를 실제로 처리한다(푸시 발송·LiveKit 호출 …). 한 핸들러가 여러 타입을 맡을 수 있다(푸시는 이벤트 종류마다 문구만 다르다).
 * **멱등이어야 한다** — 같은 이벤트가 두 번 와도 결과가 같아야 한다 (BACKEND_PLAN.md §2.4).
 * 던지면 실패로 기록되고 백오프 후 다시 불린다.
 * 구현 예: PushHandler(푸시), SupabaseUserDeletionHandler(탈퇴 뒤 Supabase 사용자 삭제).
 * 한 타입은 한 핸들러만 맡는다 — 겹치면 OutboxDispatcher 생성자가 기동을 멈춘다.
 */
public interface OutboxHandler {
    /** 이 핸들러가 맡는 이벤트 타입들 (OutboxEvent.type = 이벤트 record 의 단순 클래스 이름) */
    Set<String> types();

    // 이벤트 한 건을 처리한다. 핸들러 안의 DB 변경은 sent 기록과 같은 트랜잭션에서 일어난다
    // — 던지면 둘 다 되돌려진다 (OutboxDispatcher.dispatchOne)
    // 주의: 안에서 부른 @Transactional 메서드가 예외를 던지면 핸들러가 잡아도 이 트랜잭션이 rollback-only 가 되어
    // 커밋에서 실패하고, 이벤트는 실패로 기록돼 다시 온다 (CODE_GUIDE.md 3-3 바깥 묶음에 합쳐짐)
    /**
     * @param type        이벤트 타입
     * @param payloadJson 발행할 때 넣은 JSON 원문
     */
    void handle(String type, String payloadJson);
}
