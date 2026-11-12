package kr.ansimclean.api.support.dto;

/** DELETE /admin/notices/{id} 응답 { ok: true }. auth 의 OkResponse 를 쓰지 않는다 — auth 가 support 를 부르므로 거꾸로 참조하면 패키지 순환(ArchitectureTest) */
public record AdminNoticeDeleted(boolean ok) {
    public static final AdminNoticeDeleted OK = new AdminNoticeDeleted(true);
}
