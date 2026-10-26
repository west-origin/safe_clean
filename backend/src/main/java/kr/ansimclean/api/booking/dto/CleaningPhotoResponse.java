package kr.ansimclean.api.booking.dto;

import java.util.UUID;

// 청소 전·후 사진 한 쌍(위치 하나). 매니저 목록·업로드 응답과 고객 목록이 같은 모양을 쓴다.
// beforeUrl·afterUrl 은 비공개 버킷(job-photos) 파일을 1시간 동안 볼 수 있는 서명 URL 이다.
// afterUrl 이 null 이면 아직 '후' 사진을 안 찍었다. 서명 URL 을 만들지 못한 사진도 그 칸만 null 이다 (목록 전체를 실패시키지 않는다).
/** docs/api.md "청소 전·후 사진" — { id, location, beforeUrl, afterUrl } */
public record CleaningPhotoResponse(UUID id, String location, String beforeUrl, String afterUrl) {}
