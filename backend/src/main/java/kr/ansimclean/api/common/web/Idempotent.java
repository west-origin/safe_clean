package kr.ansimclean.api.common.web;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 같은 요청이 두 번 저장되지 않게 한다 (BACKEND_PLAN.md §6.9).
 * 업무 저장과 키 기록이 **한 트랜잭션**이라 "저장은 됐는데 기록 전에 죽는" 구간이 없다.
 *  - 처음 온 요청: 실행하고 응답을 저장한다
 *  - 같은 키·같은 본문 재시도: 저장해 둔 응답을 그대로 돌려준다
 *  - 같은 키·다른 본문: IDEMPOTENCY_MISMATCH
 *  - 첫 요청이 아직 처리 중: IN_PROGRESS (앱이 1초 뒤 한 번 더 시도한다)
 * 부르는 곳 예: BookingService(예약 생성), OfferService(콜 수락).
 * 업무(action)가 예외로 실패하면 트랜잭션이 통째로 되돌려져 키 기록도 남지 않는다 — 같은 키로 다시 보내면 처음부터 다시 실행된다.
 * 주의: Postgres 는 같은 키의 앞 요청이 커밋되기 전이면 insertIfAbsent 를 그 끝까지 기다리게 한다. 그래서 동시에 온 같은 요청은
 * 대개 기다린 뒤 저장된 응답을 받고(앞 요청이 롤백됐으면 이 요청이 처음으로 실행된다), IN_PROGRESS 는 기록이 없거나 '처리 중' 으로
 * 보일 때의 대비다. backend/docs/domain.md 의 '동시 요청은 IN_PROGRESS' 설명과 다르다 (IdempotencyIT 는 200·409 를 둘 다 허용한다).
 */
@Service
public class Idempotent {
    private final IdempotencyKeyRepository repository;
    private final AppClock clock;
    private final JsonMapper json = JsonMapper.builder().build(); // 요청 객체 → 해시용 문자열, 응답 객체 ↔ 저장용 JSON

    static final int MAX_KEY_LENGTH = 128;

    public Idempotent(IdempotencyKeyRepository repository, AppClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * @param key    앱이 보낸 Idempotency-Key (없으면 멱등 처리 없이 그냥 실행)
     * @param userId 요청한 사용자 (키는 사용자별로 구분한다)
     * @param request 본문 해시에 쓸 요청 객체 (메서드·경로를 포함한 문자열이면 더 좋다)
     * @param action 실제 업무
     * @param responseType 저장해 둔 응답 JSON 을 되살릴 때 쓸 응답 클래스
     * @return 첫 요청이면 action 의 결과, 재시도면 저장해 둔 결과
     * 해시는 부르는 쪽이 넘긴 request 를 JSON 으로 바꾼 값이다 (예: 예약 생성은 본문 객체만, 콜 수락은 "accept:" + 콜 id).
     * V001 마이그레이션·BACKEND_PLAN.md 의 request_hash 설명 'method + path + body' 와 다르다
     */
    // 부르는 서비스(예: BookingService)가 클래스 전체에 @Transactional 이라 보통 그 묶음에 합쳐진다 (CODE_GUIDE.md 3-3) —
    // 키 기록과 업무 저장이 함께 커밋되고, action 이 던지면 함께 되돌려진다
    // <T>: 응답 타입을 부르는 쪽이 정한다 (예: BookingResponse). Supplier<T> 는 인자 없이 T 를 돌려주는 함수(람다)
    @Transactional
    public <T> T run(String key, UUID userId, Object request, Class<T> responseType, Supplier<T> action) {
        if (key == null || key.isBlank()) return action.get();
        if (key.length() > MAX_KEY_LENGTH) throw ApiException.validation("Idempotency-Key 가 너무 길어요"); // 앱은 UUID(36자). 무제한이면 키 열이 임의 문자열로 자란다

        String hash = hash(json.writeValueAsString(request));
        // 1행이 들어갔으면 이 요청이 처음이다. 0행이면 같은 키의 기록이 이미 있다(재시도 또는 동시에 온 같은 요청)
        boolean first = repository.insertIfAbsent(key, userId, hash, clock.now()) == 1;
        if (!first) {
            // 기록을 잠그고 읽어 비교한다: 내용이 다르면 MISMATCH, 아직 끝나지 않았으면 IN_PROGRESS, 끝났으면 저장한 응답
            IdempotencyKey saved = repository.findForUpdate(key, userId).orElseThrow(() -> new ApiException(ErrorCode.IN_PROGRESS));
            if (!saved.getRequestHash().equals(hash)) throw new ApiException(ErrorCode.IDEMPOTENCY_MISMATCH);
            if (!saved.isDone()) throw new ApiException(ErrorCode.IN_PROGRESS);
            return json.readValue(saved.getResponseBody(), responseType);
        }

        // 처음 온 요청: 업무를 실행하고, 방금 넣은 기록에 결과를 JSON 으로 적는다 (같은 트랜잭션이라 업무 저장과 함께 커밋된다)
        T result = action.get();
        repository.findForUpdate(key, userId).orElseThrow(() -> new ApiException(ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage()))
                .done(200, json.writeValueAsString(result));
        return result;
    }

    /** 문자열의 SHA-256 해시(64자 16진). 요청 내용 대신 이 값을 저장해 비교한다 */
    static String hash(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL, ErrorCode.INTERNAL.defaultMessage());
        }
    }

    /** 오래된 키 삭제 (AuthCleaner 가 매일 04:10 에 부른다, §2.5). Repository 는 이 패키지 안에서만 쓴다(ArchitectureTest) */
    @Transactional
    public long purgeOlderThan(Instant before) {
        return repository.deleteByCreatedAtBefore(before);
    }
}
