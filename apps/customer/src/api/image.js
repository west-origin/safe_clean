/**
 * 업로드 전에 사진을 줄인다 — 긴 변 1600px, JPEG 품질 0.8. 4~5MB 원본은 느린 망에서 요청 타임아웃에 걸린다 (검수 3차).
 * canvas·createImageBitmap 이 없거나(테스트) 실패하면 원본을 그대로 돌려준다. 이미 작으면(≤ 600KB) 손대지 않는다.
 * 부르는 곳 예: 수리 예약 '증상 상세' 화면(screens/repair/booking/RepairDetail.jsx)이 사진마다
 * uploadRepairPhoto(api/customer.js → POST /customer/repair-photos) 로 올리기 직전에 부른다.
 */
const MAX_EDGE = 1600; // 가로·세로 중 긴 쪽의 최대 픽셀
const SKIP_UNDER_BYTES = 600 * 1024; // 이 크기 이하 파일은 줄이지 않는다 (600KB)

/**
 * 사진 파일 하나를 받아 업로드용 파일을 돌려준다 (서버 호출·저장 없음, 브라우저 안에서만 처리).
 * @param {File} file 사용자가 고른 사진
 * @returns {Promise<File>} 줄인 JPEG 파일, 또는 줄일 필요가 없거나 줄이지 못하면 받은 원본 그대로
 */
export async function shrinkImage(file) {
  if (!file || file.size <= SKIP_UNDER_BYTES || typeof createImageBitmap !== 'function' || typeof document === 'undefined') return file;
  try {
    // createImageBitmap: 파일을 그림(픽셀)으로 풀어 가로·세로 크기를 알아낸다
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height)); // 1 이하로만 — 작은 사진을 키우지 않는다
    // 크기를 줄일 필요도 없고 이미 JPEG 면 다시 인코딩하지 않는다. JPEG 가 아니면(PNG 등) 크기가 그대로여도 JPEG 로 바꿔 용량을 줄인다
    if (scale === 1 && file.type === 'image/jpeg') return file;
    // 화면에 붙이지 않는 canvas 에 줄인 크기로 다시 그린 뒤 JPEG 로 뽑는다
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    const ctx = canvas.getContext('2d');
    if (!ctx) return file;
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    // canvas.toBlob 은 콜백 방식이라 Promise 로 감싸 await 한다. 실패하면 blob 이 null 로 온다
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.8));
    if (!blob) return file;
    // 원래 이름의 마지막 확장자를 .jpg 로 바꾼다(확장자가 없으면 붙인다) — 내용이 JPEG 가 됐으므로
    return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.jpg', { type: 'image/jpeg' });
  } catch {
    return file; // 축소 실패는 업로드를 막지 않는다
  }
}
