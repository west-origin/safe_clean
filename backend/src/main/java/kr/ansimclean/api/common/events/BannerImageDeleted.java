package kr.ansimclean.api.common.events;

/**
 * 배너 이미지 파일을 지워야 한다 (배너 삭제·이미지 교체 뒤). Storage 삭제는 외부 효과라 트랜잭션 안에서 부르지 않고
 * 아웃박스로 남긴다(CLAUDE.md §11-1) — banner/BannerImageDeletionHandler 가 지우고, 실패하면 아웃박스가 다시 시도한다.
 * path = banners 버킷 안 경로
 */
public record BannerImageDeleted(String path) {}
