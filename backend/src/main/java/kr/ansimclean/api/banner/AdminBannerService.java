package kr.ansimclean.api.banner;

import kr.ansimclean.api.banner.dto.AdminBannerResponse;
import kr.ansimclean.api.banner.dto.AdminBannerTextRequest;
import kr.ansimclean.api.common.activity.ActivityLogService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.events.BannerImageDeleted;
import kr.ansimclean.api.common.format.KoreanFormat;
import kr.ansimclean.api.common.outbox.OutboxService;
import kr.ansimclean.api.common.security.AccessPolicy;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 어드민 배너 관리 (rbac.md §15-22, 배너·공지 관리 4회차). 매니저 앱 관리자 화면 → /admin/banners/**.
 * 모든 메서드는 처음에 AccessPolicy(BANNER_MANAGE = 어드민 + MFA)를 본다. 바꾸는 작업은 같은 트랜잭션에서 활동 이력을 남긴다.
 * 순서: 파일 올리기(외부, 트랜잭션 밖) → DB(트랜잭션). DB 가 실패하면 방금 올린 파일을 치운다.
 * 옛 파일 삭제는 아웃박스(BannerImageDeleted) — 트랜잭션 안에서 Storage 를 부르지 않는다(CLAUDE.md §11-1).
 * 클래스에 @Transactional 을 붙이지 않고 tx(TransactionTemplate)로 DB 부분만 묶는다 — 업로드가 트랜잭션 밖에 있어야 해서다.
 */
@Service
public class AdminBannerService {
    static final int ADMIN_URL_SECONDS = 60 * 60; // 관리 화면 미리보기 1시간

    private final HomeBannerRepository banners;
    private final BannerImages images;
    private final AccessPolicy policy;
    private final ActivityLogService activity;
    private final OutboxService outbox;
    private final TransactionTemplate tx;
    private final AppClock clock;

    public AdminBannerService(HomeBannerRepository banners, BannerImages images, AccessPolicy policy, ActivityLogService activity,
                              OutboxService outbox, TransactionTemplate tx, AppClock clock) {
        this.banners = banners;
        this.images = images;
        this.policy = policy;
        this.activity = activity;
        this.outbox = outbox;
        this.tx = tx;
        this.clock = clock;
    }

    /** 모든 배너 (기간이 지났거나 아직인 것까지), 순서대로 */
    public List<AdminBannerResponse> list(CurrentUser actor) {
        require(actor, null);
        LocalDate today = clock.today();
        return tx.execute(s -> banners.findAllByOrderBySortOrderAscCreatedAtAsc().stream().map(b -> response(b, today)).toList());
    }

    /** 배너 한 장. 없으면 404 */
    public AdminBannerResponse get(CurrentUser actor, UUID id) {
        require(actor, id);
        return tx.execute(s -> response(find(id), clock.today()));
    }

    /** 새 배너: 이미지 + 대체 문구 + 게시 기간. 맨 뒤에 붙는다 */
    public AdminBannerResponse create(CurrentUser actor, String contentType, byte[] bytes, AdminBannerTextRequest req) {
        require(actor, null);
        Text t = clean(req);
        String type = BannerImages.check(contentType, bytes);
        UUID id = UUID.randomUUID();
        String path = images.upload(id, type, bytes);
        try {
            return tx.execute(s -> {
                List<HomeBanner> all = banners.lockAllInOrder();
                int next = all.stream().mapToInt(HomeBanner::getSortOrder).max().orElse(0) + 1;
                HomeBanner b = banners.save(HomeBanner.create(id, t.title(), t.description(), path, t.startsOn(), t.endsOn(), next, clock.now()));
                activity.record(actor, ActivityLogService.BANNER_CREATE, "banner", id.toString(), null, snapshot(b), null);
                return response(b, clock.today());
            });
        } catch (RuntimeException e) {
            images.deleteQuietly(path); // DB 가 실패했으니 올린 파일은 고아가 된다
            throw e;
        }
    }

    /** 대체 문구·게시 기간 고치기 */
    public AdminBannerResponse update(CurrentUser actor, UUID id, AdminBannerTextRequest req) {
        require(actor, id);
        Text t = clean(req);
        return tx.execute(s -> {
            HomeBanner b = find(id);
            Map<String, Object> before = snapshot(b);
            b.edit(t.title(), t.description(), t.startsOn(), t.endsOn(), clock.now());
            activity.record(actor, ActivityLogService.BANNER_UPDATE, "banner", id.toString(), before, snapshot(b), null);
            return response(b, clock.today());
        });
    }

    /** 이미지 바꾸기 — 새 경로로 올리고, 옛 파일은 아웃박스가 지운다 */
    public AdminBannerResponse replaceImage(CurrentUser actor, UUID id, String contentType, byte[] bytes) {
        require(actor, id);
        String type = BannerImages.check(contentType, bytes);
        tx.execute(s -> find(id)); // 없는 배너면 올리기 전에 404
        String path = images.upload(id, type, bytes);
        try {
            return tx.execute(s -> {
                HomeBanner b = find(id);
                String old = b.replaceImage(path, clock.now());
                outbox.publish(new BannerImageDeleted(old));
                activity.record(actor, ActivityLogService.BANNER_IMAGE, "banner", id.toString(), Map.of("imagePath", old), Map.of("imagePath", path), null);
                return response(b, clock.today());
            });
        } catch (RuntimeException e) {
            images.deleteQuietly(path);
            throw e;
        }
    }

    /** 순서 바꾸기: 모든 배너 id 를 새 순서대로 받는다 → 바뀐 전체 목록 */
    public List<AdminBannerResponse> reorder(CurrentUser actor, List<UUID> ids) {
        require(actor, null);
        return tx.execute(s -> {
            List<HomeBanner> all = banners.lockAllInOrder();
            if (ids == null || ids.size() != all.size() || !new HashSet<>(ids).equals(new HashSet<>(all.stream().map(HomeBanner::getId).toList()))) {
                throw ApiException.validation("배너 목록이 바뀌었어요. 화면을 새로고침해주세요");
            }
            List<String> before = all.stream().map(b -> b.getId().toString()).toList();
            Map<UUID, HomeBanner> byId = new LinkedHashMap<>();
            all.forEach(b -> byId.put(b.getId(), b));
            for (int i = 0; i < ids.size(); i++) byId.get(ids.get(i)).moveTo(i + 1, clock.now());
            activity.record(actor, ActivityLogService.BANNER_REORDER, "banner", null, before, ids.stream().map(UUID::toString).toList(), null);
            LocalDate today = clock.today();
            return ids.stream().map(byId::get).map(b -> response(b, today)).toList();
        });
    }

    /** 배너 지우기. 이미지 파일은 아웃박스가 지운다 */
    public void delete(CurrentUser actor, UUID id) {
        require(actor, id);
        tx.executeWithoutResult(s -> {
            HomeBanner b = find(id);
            activity.record(actor, ActivityLogService.BANNER_DELETE, "banner", id.toString(), snapshot(b), null, null);
            outbox.publish(new BannerImageDeleted(b.getImagePath()));
            banners.delete(b);
        });
    }

    private void require(CurrentUser actor, UUID id) {
        policy.require(actor, AccessPolicy.Action.BANNER_MANAGE, AccessPolicy.Resource.of("banner", id));
    }

    private HomeBanner find(UUID id) {
        return banners.findById(id).orElseThrow(ApiException::notFound);
    }

    // 앞뒤 공백을 뗀 입력
    private record Text(String title, String description, LocalDate startsOn, LocalDate endsOn) {}

    /** 제목 1~40자, 설명 0~60자, 날짜는 "2026-11-12" 또는 비움, 시작 ≤ 종료. 어기면 VALIDATION(화면에 그대로 보이는 문구) */
    static Text clean(AdminBannerTextRequest req) {
        String title = req == null || req.title() == null ? "" : req.title().strip();
        String description = req == null || req.description() == null ? "" : req.description().strip();
        if (title.isEmpty()) throw ApiException.validation("배너 제목을 입력해주세요");
        if (title.length() > HomeBanner.TITLE_MAX) throw ApiException.validation("배너 제목은 " + HomeBanner.TITLE_MAX + "자까지 쓸 수 있어요");
        if (description.length() > HomeBanner.DESCRIPTION_MAX) throw ApiException.validation("배너 설명은 " + HomeBanner.DESCRIPTION_MAX + "자까지 쓸 수 있어요");
        LocalDate startsOn = date(req == null ? null : req.startsOn());
        LocalDate endsOn = date(req == null ? null : req.endsOn());
        if (startsOn != null && endsOn != null && startsOn.isAfter(endsOn)) throw ApiException.validation("게시 종료일은 시작일과 같거나 뒤여야 해요");
        return new Text(title, description, startsOn, endsOn);
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.strip()); // "2026-11-12"
        } catch (DateTimeParseException e) {
            throw ApiException.validation("날짜 형식을 확인해주세요");
        }
    }

    private AdminBannerResponse response(HomeBanner b, LocalDate today) {
        String state = b.isLiveOn(today) ? "live" : (b.getStartsOn() != null && today.isBefore(b.getStartsOn()) ? "scheduled" : "ended");
        String stateLabel = switch (state) {
            case "live" -> "게시 중";
            case "scheduled" -> "게시 예정";
            default -> "게시 종료";
        };
        return new AdminBannerResponse(b.getId(), b.getTitle(), b.getDescription(), images.url(b.getImagePath(), ADMIN_URL_SECONDS),
                b.getStartsOn() == null ? null : b.getStartsOn().toString(), b.getEndsOn() == null ? null : b.getEndsOn().toString(),
                periodLabel(b.getStartsOn(), b.getEndsOn()), state, stateLabel);
    }

    /** "11월 12일 ~ 12월 31일" · "11월 12일부터" · "12월 31일까지" · "기간 제한 없음" (코드에서 정함) */
    static String periodLabel(LocalDate startsOn, LocalDate endsOn) {
        if (startsOn == null && endsOn == null) return "기간 제한 없음";
        if (endsOn == null) return KoreanFormat.dateShort(startsOn) + "부터";
        if (startsOn == null) return KoreanFormat.dateShort(endsOn) + "까지";
        return KoreanFormat.dateShort(startsOn) + " ~ " + KoreanFormat.dateShort(endsOn);
    }

    /** 활동 이력에 남길 값 */
    private static Map<String, Object> snapshot(HomeBanner b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", b.getTitle());
        m.put("description", b.getDescription());
        m.put("imagePath", b.getImagePath());
        m.put("startsOn", b.getStartsOn() == null ? null : b.getStartsOn().toString());
        m.put("endsOn", b.getEndsOn() == null ? null : b.getEndsOn().toString());
        m.put("sortOrder", b.getSortOrder());
        return m;
    }
}
