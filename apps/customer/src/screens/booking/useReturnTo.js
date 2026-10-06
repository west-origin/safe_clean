// 확인 화면의 "수정하기" 로 한 단계만 고치고 바로 돌아오기 (코드에서 정함 — 검증 검토: 일정 하나 고치려고 뒤 단계를 전부 다시 지나야 했다).
// 진입: `?returnTo=<확인 화면 경로>`. 단계의 "다음" 은 `next(기본 경로)` 로 — returnTo 가 있으면 그리로, 없으면 원래 순서대로.
// 다음 단계로 이어가야 할 때(가전 → 증상처럼 뒤 단계가 앞 값에 의존)는 `carry(경로)` 로 returnTo 를 붙여 넘긴다.
// 예약 플로우 단계 화면들(screens/booking)과 수리 예약 플로우(screens/repair/booking)가 쓴다.
// 진입 주소는 withReturn 으로 만든다 (예: BookingConfirm 의 '수정하기'). returnTo 가 확인 화면이 아닐 때도 있다 —
// 예: RepairAddress 의 주소 변경은 청소 예약의 주소 검색·확인 화면을 빌려 쓰고 수리 주소 화면으로 돌아온다.
import { useSearchParams } from 'react-router';

// 지금 주소의 ?returnTo= 를 읽어 '다음' 이 갈 곳을 계산하는 함수들을 돌려준다. 이동(navigate)은 부르는 화면이 한다
/** @returns {{ returnTo: string|null, next: (defaultPath: string) => string, carry: (path: string) => string, withReturn: (path: string, to: string) => string }} */
export function useReturnTo() {
  const [params] = useSearchParams();
  const raw = params.get('returnTo');
  // 주의: 첫 글자가 '/' 인지만 본다 — '//' 로 시작하는 값(다른 호스트를 가리키는 주소)도 이 검사를 통과한다
  const returnTo = raw && raw.startsWith('/') ? raw : null; // 'https://…' 같은 바깥 주소는 버리고 '/' 로 시작하는 값만
  // path 에 returnTo=to 를 붙인다. path 에 이미 쿼리(?)가 있으면 & 로 잇고, to 는 URL 에 안전하게 인코딩한다
  const withReturn = (path, to) => `${path}${path.includes('?') ? '&' : '?'}returnTo=${encodeURIComponent(to)}`;
  return {
    returnTo,
    next: (defaultPath) => returnTo ?? defaultPath,
    carry: (path) => (returnTo ? withReturn(path, returnTo) : path),
    withReturn,
  };
}
