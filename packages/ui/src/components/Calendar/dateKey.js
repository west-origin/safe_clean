// 날짜 도우미 모음: 'YYYY-MM-DD'(dateKey) ↔ Date 변환, 서울 기준 '오늘', 화면용 한국어 날짜 형식,
// 예약 가능 날짜·변경 기한 판정. 대부분 Calendar/index.js → packages/ui/src/index.js 를 거쳐 두 앱에 공개된다.
// 예: 고객 앱 청소 예약 일시 선택(BookingDateTime), 매니저 앱 일정(Schedule).
// 'YYYY-MM-DD' 를 Date 로 바꿀 때는 꼭 fromDateKey 를 쓴다 — `new Date('YYYY-MM-DD')` 는 UTC 로 읽혀
//   시간대에 따라 하루가 어긋난다.
const pad2 = (n) => String(n).padStart(2, '0'); // 9 → '09'

/** Date → 'YYYY-MM-DD' (로컬 기준). 화면과 api 가 날짜를 주고받는 형식 */
export const toDateKey = (d) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;

/** 'YYYY-MM-DD' → Date (로컬 자정).
 *  `new Date('YYYY-MM-DD')` 는 UTC 자정으로 읽어서 시간대에 따라 하루가 어긋나므로 쓰지 않는다. 형식이 틀리면 null */
export function fromDateKey(key) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(key ?? '');
  if (!m) return null;
  return new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
}

/** 서울 기준 오늘 'YYYY-MM-DD' — 서버 규칙(Asia/Seoul)과 같게. 기기 시간대가 달라도 '오늘' 이 하루 어긋나지 않게 (자체 점검 #40) */
export const seoulTodayKey = (now = new Date()) =>
  // 'en-CA'(캐나다 영어) 형식은 날짜를 YYYY-MM-DD 모양으로 찍어 준다. timeZone 으로 서울 날짜를 뽑는다
  new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);

/** 오늘(서울 날짜)의 로컬 자정 — 달력 칸(로컬 자정 Date)과 비교하는 기준 */
export const startOfToday = () => fromDateKey(seoulTodayKey());

// ── 화면 표시용 한국어 형식 (서버 KoreanFormat 과 짝). 청소·수리 예약 플로우가 각자 갖고 있던 두 벌을 하나로 (검증 검토)
const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];

/** 'YYYY-MM-DD' → "2026년 11월 1일" (Booking/Confirm 방문 일정) */
export function formatDate(key) {
  const d = fromDateKey(key);
  return d ? `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일` : '';
}

/** 'YYYY-MM-DD' + 'HH:mm' → "2026년 9월 16일 (수) 11:00" */
export function formatDateTime(key, time) {
  const d = fromDateKey(key);
  if (!d) return '';
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${WEEKDAY[d.getDay()]})${time ? ` ${time}` : ''}`;
}

/** 'YYYY-MM-DD' → "9월 23일(수)" (수리 Figma 1:252) */
export function formatDateShortWeekday(key) {
  const d = fromDateKey(key);
  return d ? `${d.getMonth() + 1}월 ${d.getDate()}일(${WEEKDAY[d.getDay()]})` : '';
}

/** 'YYYY-MM-DD' → "2026년 9월 23일(수)" (수리 Figma 1:311) */
export function formatDateLongWeekday(key) {
  const d = fromDateKey(key);
  return d ? `${d.getFullYear()}년 ${formatDateShortWeekday(key)}` : '';
}

/** 'HH:mm' → "오전 9시" / "오후 2시 30분" */
export function formatTimeKo(time) {
  if (!time) return '';
  const [h, m] = time.split(':').map(Number);
  const period = h < 12 ? '오전' : '오후';
  const hour = h % 12 === 0 ? 12 : h % 12; // 0시는 오전 12시, 12시는 오후 12시
  return `${period} ${hour}시${m ? ` ${m}분` : ''}`;
}

/** 예약 가능 날짜 조건: 오늘 이후의 평일 (BookingDateTime·RepairDateTime 공용) */
export const notBookable = (d) => d <= startOfToday() || d.getDay() === 0 || d.getDay() === 6;

/**
 * notBookable + 예약 가능 기간(서버 /catalog/time-slots 의 bookableDays). 서버도 같은 값으로 막는다 (전체 재점검 B1).
 * @param {number} [days] 기준일부터 며칠 뒤까지. 없으면 기간 제한 없이 notBookable 과 같다
 * @param {string} [fromKey] 일정 변경일 때 지금 방문일 'YYYY-MM-DD' — 오늘보다 뒤면 그날부터 센다(서버와 같다).
 *   오늘부터만 세면 60일 뒤의 정기청소 회차는 고를 날짜가 하나도 없었다 (전체 점검 G3)
 * @returns {(d: Date) => boolean} 날짜를 받아 막을지(true) 알려 주는 함수 — Calendar·WeekCalendar 의 isDisabled 에 그대로 넘긴다
 */
export const notBookableWithin = (days, fromKey) => (d) => {
  if (notBookable(d)) return true;
  if (!days) return false;
  const today = startOfToday();
  const from = fromDateKey(fromKey);
  const last = from && from > today ? from : today; // 기준일: 지금 방문일이 오늘보다 뒤면 그날, 아니면 오늘
  // 마지막 예약 가능일 = 기준일 + days (그날까지는 연다). last 는 방금 새로 만든 Date 라 바꿔도 다른 곳에 영향이 없다
  last.setDate(last.getDate() + days);
  return d > last;
};

/**
 * 'YYYY-MM-DD' + 'HH:mm' 방문이 지금부터 hours 시간 안인가 — 서버의 변경·취소 기한(starts_at − now < N시간)과 같은 셈.
 * 예약 전에 "이 시간은 예약 후 바꾸거나 취소할 수 없어요" 를 알려 주려고 쓴다 (직접 사용 점검 U2)
 */
export function isWithinHours(key, time, hours, now = new Date()) {
  // 날짜·시각 형식이 틀리거나 hours 가 없으면 경고할 것이 없다고 보고 false
  if (!fromDateKey(key) || !/^\d{2}:\d{2}$/.test(time ?? '') || !hours) return false;
  const visit = Date.parse(`${key}T${time}:00+09:00`); // 방문 시각은 서울 시각 — 기기 시간대로 읽으면 해외 기기에서 몇 시간씩 어긋났다 (자체 점검 #40)
  return visit - now.getTime() < hours * 3600 * 1000; // 남은 밀리초 < hours 시간(밀리초로 바꿈)
}
