// 예약 플로우 9화면이 함께 쓰는 입력값. 카테고리별로 sessionStorage 에 두어 새로고침해도 남고, 앱을 닫으면 사라진다.
// 로그아웃·탈퇴로 세션이 지워지면 초안도 전부 지운다 — 같은 기기에서 계정을 바꿔도 이전 사람의 주소·요청사항이 보이지 않게.
// (이 모듈이 아직 로드되지 않은 실행을 위해 session.clear() 도 같은 키를 직접 지운다 — api/session.js)
// 수리 예약 플로우(screens/repair/booking)도 'repair:<카테고리>' 를, 예약 변경(BookingDateTime 의 editId)은
// 'edit-<예약 id>' 를 category 자리에 넣어 같은 훅을 쓴다 — 서로 다른 키라 초안이 섞이지 않는다.
import { useCallback, useState } from 'react';
import { session } from '../../api/session.js';

// sessionStorage 키: 접두어 + 카테고리 (예: 'ansimclean.booking.home'). 접두어는 api/session.js 의 DRAFT_PREFIX 와 같아야 한다
const KEY_PREFIX = 'ansimclean.booking.';
const KEY = (category) => `${KEY_PREFIX}${category}`;

/** 모든 카테고리의 초안 삭제 */
export function clearAllBookingDrafts() {
  try {
    Object.keys(sessionStorage)
      .filter((k) => k.startsWith(KEY_PREFIX))
      .forEach((k) => sessionStorage.removeItem(k));
  } catch {
    // 저장소를 쓸 수 없는 환경
  }
}

// 이 모듈이 처음 import 될 때 한 번 등록된다. 세션이 바뀔 때마다 불리고, 토큰이 없어졌으면(로그아웃 등) 초안을 전부 지운다
session.subscribe(() => {
  if (!session.accessToken()) clearAllBookingDrafts();
});

/** 한 카테고리의 초안을 읽는다. 저장된 것이 없거나 JSON 이 깨졌거나 저장소를 못 쓰면 빈 객체 {} */
function read(category) {
  try {
    return JSON.parse(sessionStorage.getItem(KEY(category))) ?? {};
  } catch {
    return {};
  }
}

/** 훅 밖에서 초안에 값을 바로 넣을 때 (저장소에 곧바로 쓴다). 예: 수리 확인 화면이 사진 만료로 증상 상세로 보내기 직전(RepairConfirm), 테스트가 앞 단계 초안을 꾸밀 때 */
export function setBookingDraft(category, patch) {
  write(category, { ...read(category), ...patch });
}

/** 저장소가 막힌 환경(사생활 보호·용량 초과)에서 렌더 예외로 번지지 않게 — 이번 실행 동안 메모리 state 로만 진행된다 (session.js 와 같은 규칙) */
function write(category, value) {
  try {
    sessionStorage.setItem(KEY(category), JSON.stringify(value));
  } catch {
    // 무시
  }
}

/**
 * @param {string} category 'home' | 'office' | 'appliance' | 'move-in'
 * @returns {[object, (patch: object | ((prev: object) => object)) => void, () => void]} [draft, update, clear] — update 는 객체(병합) 또는 함수(이전 값 → 병합할 객체).
 *   내용을 바꾸면 `submitKey`(예약 생성 멱등 키, BACKEND_PLAN.md §9.4)는 지운다 — 서버가 같은 키에 다른 본문을 거부하기 때문. submitKey 자체를 넣는 patch 는 예외
 */
export function useBookingDraft(category) {
  // 처음 그릴 때만 저장소에서 읽는다 (useState 에 함수를 넘기면 첫 렌더에서 한 번만 부른다)
  const [draft, setDraft] = useState(() => read(category));
  // useCallback: 같은 category 동안 update 함수를 새로 만들지 않고 같은 것을 돌려준다
  const update = useCallback(
    (patch) => {
      // 직전 state(prev)에 합친 결과를 화면 state 와 sessionStorage 에 함께 반영한다
      setDraft((prev) => {
        const values = typeof patch === 'function' ? patch(prev) : patch;
        const next = { ...prev, ...('submitKey' in values ? {} : { submitKey: undefined }), ...values };
        write(category, next);
        return next;
      });
    },
    [category],
  );
  // 이 카테고리의 초안만 지우고 화면 state 도 비운다 (예: 확인 화면이 예약을 마친 뒤)
  const clear = useCallback(() => {
    try {
      sessionStorage.removeItem(KEY(category));
    } catch {
      // 무시
    }
    setDraft({});
  }, [category]);
  return [draft, update, clear];
}

// 날짜·시간 표시 형식은 @ansimclean/ui(dateKey.js)에 하나만 둔다 — 수리 플로우와 같은 함수 (검증 검토)
export { formatDate, formatDateTime, formatTimeKo } from '@ansimclean/ui';
