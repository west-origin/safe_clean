// 수리 예약 플로우 공통: 초안 키, 단계 번호, 서비스 이름, 라우트. 초안은 bookingDraft 의 sessionStorage 저장소를 'repair:<category>' 키로 같이 쓴다
// (주소 검색·확인 화면(/booking/:category/…)이 그 저장소에 roadAddress 를 넣어 주기 때문)
// 쓰는 곳 예: 같은 폴더의 수리 예약 화면(RepairAppliance·RepairConfirm). category 는 주소의 :category
// (REPAIR_CATEGORIES 의 key — 'appliance', 'plumbing' 등)이다.
import { REPAIR_CATEGORIES } from '@ansimclean/ui';
import { REPAIR_PHOTO_EXPIRED } from '../../../api/mock.js';

/**
 * 초안 저장 키: 'appliance' → 'repair:appliance'. 청소 예약에도 'appliance'(가전청소) 카테고리가 있어,
 * 앞에 repair: 를 붙이지 않으면 가전수리와 가전청소의 초안이 한 칸에 섞인다
 */
export const draftKey = (category) => `repair:${category}`;
/** 수리 예약 단계 화면 주소: ('appliance', 'symptom') → '/repair/booking/appliance/symptom' (routes.jsx 와 같은 모양) */
export const path = (category, step) => `/repair/booking/${category}/${step}`;

/** 가전수리만 가전·증상 단계가 있다 (Figma 8단계). 나머지는 6단계 */
export const hasApplianceSteps = (category) => category === 'appliance';
/** 단계 이름을 화면 순서대로 (완료 화면 포함). 가전수리가 아니면 앞 두 개(appliance·symptom)를 뺀 나머지를 쓴다 */
export const STEPS = ['appliance', 'symptom', 'detail', 'address', 'datetime', 'notes', 'confirm', 'complete'];
/**
 * 상단 진행 표시(StepNavBar)에 넣을 "몇 단계 중 몇 번째" — 예: ('appliance', 'detail') → { step: 3, total: 8 },
 * ('plumbing', 'detail') → { step: 1, total: 6 }. 목록에 없는 단계 이름이면 step 이 0 이 된다
 */
export function stepOf(category, step) {
  const list = hasApplianceSteps(category) ? STEPS : STEPS.slice(2);
  return { step: list.indexOf(step) + 1, total: list.length };
}

/** 수리 종류 key → 화면 이름 ('plumbing' → '배관·누수'). 모르는 key 면 '수리' */
export const categoryLabel = (category) => REPAIR_CATEGORIES.find((c) => c.key === category)?.label ?? '수리';

// 서비스 이름: 단계 화면 머리글(StepQuestion 의 service)과 확인 화면에 쓴다. catalog 는 getRepairBooking() 결과, draft 는 수리 예약 초안
/** "가전수리 · 냉장고" (가전을 고르기 전엔 "가전수리") */
export function serviceName(category, catalog, draft) {
  const label = categoryLabel(category);
  const appliance = catalog.appliances.find((a) => a.key === draft.appliance);
  return appliance ? `${label} · ${appliance.label}` : label;
}

/** 고른 가전의 증상 목록. 따로 목록이 없는 가전은 공통 목록(default)을 쓴다 (가전별 목록은 mock.js REPAIR_BOOKING.symptoms) */
export const symptomsFor = (catalog, appliance) => catalog.symptoms[appliance] ?? catalog.symptoms.default;
/** 초안의 증상 key → 표시 문구 (확인 화면용). 고르지 않았거나 목록에 없으면 빈 문자열 */
export const symptomLabel = (catalog, draft) => symptomsFor(catalog, draft.appliance).find((s) => s.key === draft.symptom)?.label ?? '';

// ── 증상 사진의 24시간 만료 (문제 목록 20번)
// 서버는 올린 사진에 24시간짜리 미리보기 주소를 한 번만 주고, 24시간 동안 예약에 붙지 않은 사진은 새벽 정리가 지운다
// (backend booking/RepairPhotoService.PREVIEW_SECONDS·UNATTACHED_KEEP). 그래서 초안의 사진마다 올린 시각(uploadedAt, 밀리초)을 두고,
// 24시간이 지난 사진은 초안에서 빼 다시 올리게 한다 — 두지 않으면 썸네일만 깨진 채 'N장 첨부됨' 으로 남다가 '예약하기' 에서야 막힌다.

/** 사진을 초안에 둘 수 있는 시간 — 서버 PREVIEW_SECONDS(24시간)와 같다. 서버 값을 바꾸면 같이 바꾼다 */
export const PHOTO_TTL_MS = 24 * 60 * 60 * 1000;

/**
 * 초안 사진 중 아직 쓸 수 있는 것(올린 지 24시간 안). 기기 시계로 잰 경과 시간이라 서버 시계와 달라도 괜찮다.
 * uploadedAt 이 없는 사진(이 규칙 전에 만든 초안)은 알 수 없어 남긴다 — 정말 만료됐으면 예약할 때 서버가 막고 확인 화면이 비운다
 * @param {{ uploadedAt?: number }[]|undefined} photos 초안의 photos
 * @param {number} [now] 지금(밀리초) — 테스트가 넘긴다
 */
export const freshPhotos = (photos, now = Date.now()) => (photos ?? []).filter((p) => !p.uploadedAt || now - p.uploadedAt < PHOTO_TTL_MS);

/** 초안에 24시간 지난 사진이 하나라도 있나 */
export const hasExpiredPhotos = (photos, now = Date.now()) => freshPhotos(photos, now).length !== (photos ?? []).length;

/**
 * 예약 생성이 '사진 만료' 로 막혔나 — 서버는 이 경우 따로 코드 없이 VALIDATION + 만료 문구로 답한다 (docs/api.md createRepairBooking).
 * 문구로 알아보므로 서버 문구(RepairPhotoService.PHOTO_EXPIRED)와 mock.js REPAIR_PHOTO_EXPIRED 가 같아야 한다. 다르면 일반 실패 문구로만 보인다
 */
export const isPhotoExpiredError = (e) => e?.code === 'VALIDATION' && e.message === REPAIR_PHOTO_EXPIRED;

// 날짜·시간 표시 형식은 @ansimclean/ui(dateKey.js)에 하나만 둔다 (검증 검토: 청소 플로우와 두 벌이었고 new Date(y, m-1, d) 직접 생성 경로가 있었다)
// 수리 화면에서 부르는 이름: dateShort "9월 23일(수)", dateLong "2026년 9월 23일(수)", timeKo "오전 11시" (입력은 'YYYY-MM-DD'·'HH:mm')
export { formatDateShortWeekday as dateShort, formatDateLongWeekday as dateLong, formatTimeKo as timeKo } from '@ansimclean/ui';
