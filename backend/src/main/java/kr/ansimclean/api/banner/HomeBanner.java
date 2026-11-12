package kr.ansimclean.api.banner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * home_banners 한 행 (V067) — 고객 앱 홈 혜택 배너 한 장. 값은 아래 메서드로만 바꾼다(setter 없음).
 * 게시 기간은 서울 날짜, 양 끝 포함. null 이면 그쪽 제한 없음.
 */
@Entity
@Table(name = "home_banners")
public class HomeBanner {
    public static final int TITLE_MAX = 40;        // 대체 문구 제목 상한 (코드에서 정함)
    public static final int DESCRIPTION_MAX = 60;  // 대체 문구 설명 상한 (코드에서 정함)

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(columnDefinition = "text", nullable = false)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String description;

    @Column(name = "image_path", columnDefinition = "text", nullable = false)
    private String imagePath;

    @Column(name = "starts_on")
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HomeBanner() {} // JPA 용

    /** 새 배너 (id 는 이미지 경로를 먼저 정하려고 부르는 쪽이 만든다) */
    static HomeBanner create(UUID id, String title, String description, String imagePath, LocalDate startsOn, LocalDate endsOn, int sortOrder, Instant now) {
        HomeBanner b = new HomeBanner();
        b.id = id;
        b.title = title;
        b.description = description;
        b.imagePath = imagePath;
        b.startsOn = startsOn;
        b.endsOn = endsOn;
        b.sortOrder = sortOrder;
        b.createdAt = now;
        b.updatedAt = now;
        return b;
    }

    /** 대체 문구·게시 기간 고치기 */
    void edit(String title, String description, LocalDate startsOn, LocalDate endsOn, Instant now) {
        this.title = title;
        this.description = description;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.updatedAt = now;
    }

    /** 이미지 바꾸기 → 옛 경로를 돌려준다(부르는 쪽이 아웃박스로 지운다) */
    String replaceImage(String path, Instant now) {
        String old = this.imagePath;
        this.imagePath = path;
        this.updatedAt = now;
        return old;
    }

    void moveTo(int sortOrder, Instant now) {
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    /** 오늘(서울)이 게시 기간 안인가 */
    boolean isLiveOn(LocalDate today) {
        return (startsOn == null || !today.isBefore(startsOn)) && (endsOn == null || !today.isAfter(endsOn));
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getImagePath() { return imagePath; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public int getSortOrder() { return sortOrder; }
}
