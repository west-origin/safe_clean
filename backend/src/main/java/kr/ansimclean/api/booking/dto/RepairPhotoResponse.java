package kr.ansimclean.api.booking.dto;

// 수리 사진 업로드 응답. RepairPhotoService.upload 가 만들고 RepairPhotoController 가 JSON 으로 돌려준다.
// path = Storage 안 저장 위치(고객 id 폴더/uuid.확장자), url = 비공개 버킷 파일을 잠시 볼 수 있는 서명 URL.
// 고객 앱은 둘 다 예약 초안에 두었다가, 예약을 만들 때 path 만 보낸다.
/** POST /customer/repair-photos — path 는 예약 본문 repair.photos 에, url 은 미리보기(서명 URL, 24시간) */
public record RepairPhotoResponse(String path, String url) {}
