// Figma 화면에 보이는 값 그대로 (Figma 에 없어 코드에서 정한 것은 각 설명에 표시).
// 서버 데이터는 이미 customer.js 가 http.js 로 서버에서 받는다. 여기는 고객 앱의 문구·목록 상수 모음이다
// (내보내는 것은 값뿐 — 문구에 숫자를 끼우는 작은 함수는 있다, 예: CHANGE_LOCK_COPY·RECURRING_TERM_NOTE). 쓰는 곳은 세 갈래다.
//  1) 화면(screens/**)이 직접 import 하는 문구 — 오류 안내·빈 상태·시트 문구 등 서버가 주지 않는 것
//  2) api/customer.js 가 서버에 묻지 않고 돌려주거나 서버 응답과 합치는 목록 — 거주형태·주기·완료 카드 라벨·수리 카탈로그 등
//  3) test/fakeHttp.js(테스트와 DEV 의 VITE_API_MOCK=1 가짜 서버)가 서버 응답을 흉내 낼 때 쓰는 값 — 예약 목록·설정·FAQ·공지 등
// 일부 값은 서버 설정·검사와 같아야 한다 (예: INPUT_LIMITS — 각 설명 참고). 다르면 앱 안내와 서버 판단이 어긋난다.
// 서버가 응답에 실어 주는 설정 값(예: CHANGE_DEADLINE_HOURS·SERIES_TERM_MONTHS)은 화면이 서버 값을 쓰고, 여기 값은 응답에 없을 때의 기본값이다.
// 문구 속 '\n' 은 줄바꿈 자리다.

/** 가입 방법 키. api/customer.js 의 signUpWith 는 받은 method 가 이 목록에 없으면 오류를 던진다 */
export const SIGN_UP_METHODS = ['kakao', 'google', 'naver', 'phone'];

/** 소셜 로그인은 아직 없다(결정 §13-1). 버튼을 누르면 조용히 전화번호 가입으로 가던 것을 안내로 바꿨다 (사용성 검토 2026-09-22) */
export const SOCIAL_SIGNUP_COPY = {
  title: '지금은 전화번호로만 가입할 수 있어요',
  description: '카카오·구글·네이버 로그인은 준비하고 있어요. 전화번호로 계속하시겠어요?',
  confirm: '전화번호로 계속하기',
  back: '닫기',
};

/** 가입 완료 실패 문구 (코드에서 정함 — Figma 화면은 이미지 한 장). Auth/SignUpComplete 화면(SignUpComplete.jsx)이 쓴다 */
export const SIGNUP_COMPLETE_ERROR = '가입을 마치지 못했어요. 잠시 후 다시 시도해주세요.';

/**
 * 약관 동의 행 (Auth/TermsConsent (79:244) › TermRows) — 문구·순서 Figma 그대로 (다섯 번째는 Figma 줄바꿈·들여쓰기 공백 포함).
 * required=true 는 필수 약관.
 * 지금은 test/fakeHttp.js 가 GET /terms 가짜 응답과 필수 약관 검사에 쓴다 (실서버는 DB terms 테이블에서 준다)
 */
export const TERMS = [
  { id: 'service', label: '(필수) 서비스 이용약관', required: true },
  { id: 'privacy', label: '(필수) 개인정보 수집 및 이용 동의', required: true },
  { id: 'location', label: '(필수) 위치기반 서비스 이용 동의', required: true },
  { id: 'marketing', label: '(선택) 마케팅 정보 수신 동의', required: false },
  { id: 'ux', label: '(선택) 서비스 개선을 위한 \n          사용자 경험 수집 동의', required: false },
];

/** Auth/TermsConsent › PrivacyNoteCard 문구 */
export const PRIVACY_NOTE = '수집된 개인정보는 안전하게 보호되며, \n서비스 제공 목적 외로 사용되지 않습니다.';

/** Auth/ProfileInfo-Phone › Field/비밀번호 › HelperText */
export const PASSWORD_OK_MESSAGE = '사용가능한 비밀번호입니다!';
/** 비밀번호 규칙을 입력 전부터 보여 준다 (코드에서 정함 — 사용성 검토 2026-09-22: 왜 '다음' 이 꺼져 있는지 알 수 없었다) */
export const PASSWORD_HINT = '8자 이상 입력해주세요';

/** Auth/ResetPassword (Figma 에 없어 코드에서 정함 — 로그인 화면 "비밀번호를 잊으셨나요?" 에서 온다) */
export const RESET_PASSWORD_COPY = {
  title: '비밀번호를\n다시 설정해주세요',
  description: '가입한 휴대폰 번호로 인증하면\n새 비밀번호를 정할 수 있어요.',
  phoneLabel: '휴대폰 번호',
  codeLabel: '인증번호',
  codePlaceholder: '문자로 받은 6자리',
  passwordLabel: '새 비밀번호',
  passwordPlaceholder: '8자 이상 입력해주세요',
  passwordHint: '8자 이상 입력해주세요',
  notFound: '가입되지 않은 번호예요',
  signUpPrompt: '아직 계정이 없으신가요?',
  signUpLink: '회원가입하기',
  submit: '비밀번호 변경하기',
  failed: '비밀번호를 바꾸지 못했어요. 잠시 후 다시 시도해주세요.',
};

/** Booking/Confirm — 고르지 않은 항목 (코드에서 정함, 전체 점검 G6) */
export const CONFIRM_EMPTY_COPY = { options: '선택한 옵션이 없어요', notices: '해당하는 주의사항이 없어요', request: '남긴 요청사항이 없어요' };
/**
 * 달력 아래 안내 — 왜 어떤 날이 회색인지 (서버 규칙 §6.1 과 같은 말, 코드에서 정함 — 사용성 검토 2026-09-22).
 * 청소 예약(BookingDateTime)·수리 예약(RepairDateTime) 날짜 화면이 쓴다
 */
export const BOOKING_DATE_NOTE = '내일부터 예약할 수 있어요.';
/**
 * 고른 방문이 변경·취소 기한(방문 N시간 전) 안일 때 — 내일 오전 방문은 예약하는 순간부터 바꾸거나 취소할 수 없는데 예약 전에 알 길이 없었다.
 * 잠긴 예약 카드의 문구도 "24시간 전까지 할 수 있어요" 라 아직 되는 것처럼 읽혔다 (코드에서 정함, 직접 사용 점검 U2)
 * picked(시간): 날짜·시간 화면에서 기한 안의 시간을 골랐을 때 (BookingDateTime·RepairDateTime).
 * card(시간, 고객센터 번호): 홈·내 예약의 잠긴 예약 카드. 번호가 없으면 1:1 문의로 안내한다.
 */
export const CHANGE_LOCK_COPY = {
  picked: (hours) => `방문까지 ${hours}시간이 남지 않아, 예약한 뒤에는 바꾸거나 취소할 수 없어요.`,
  card: (hours, phone) => `방문 ${hours}시간 전이 지나 앱에서는 바꾸거나 취소할 수 없어요. ${phone ? `고객센터(${phone})로 연락해주세요.` : '1:1 문의로 알려주세요.'}`,
};
/**
 * 변경·취소 기한(방문 몇 시간 전까지)의 기본값 = 서버 app_settings.cancel_deadline_hours 의 시드 값.
 * 화면은 서버가 준 changeDeadlineHours 를 쓴다 — 홈(GET /customer/home)·내 예약(GET /customer/reservations)·날짜 화면(GET /catalog/time-slots) 응답.
 * 이 값은 응답에 그 칸이 없을 때(칸을 더하기 전의 서버)와 가짜 서버(test/fakeHttp.js)만 쓴다.
 * 예전에는 잠긴 카드·정기 회차 취소 시트가 이 고정값만 써서, 운영자가 기한을 바꾸면 카드 잠금과 안내 시간이 달랐다 (문제 목록 25번)
 */
export const CHANGE_DEADLINE_HOURS = 24;

/** 주소 검색 안내 (코드에서 정함 — 사용성 검토 2026-09-22. 검색은 카카오 로컬 API, §13-4 · 2026-10-23) */
export const ADDRESS_SEARCH_COPY = {
  // 시·도가 없으면 서버가 받지 않는다(매칭 지역을 못 읽는다) — 예시도 시·도부터 (직접 사용 점검 U1, 코드에서 정함)
  guide: '시·도부터 입력해주세요. 예: 서울 강남구 강남대로 396',
  noResults: '검색 결과가 없어요. 시·도부터 적은 주소 그대로 진행할 수 있어요.',
};

/** 주소 입력 화면의 개인정보 안내 (Auth/ProfileAddress (84:202) › PrivacyNote) — ProfileAddress.jsx 가 쓴다 */
export const ADDRESS_PRIVACY_NOTE = '정확한 주소는 매칭과 방문에만 사용되며,\n다른 사용자에게는 공개되지 않습니다.';

/** Auth/ProfileHousing (79:311) › HousingTypeGrid — 문구·순서 Figma 그대로. icon 은 ui ASSETS 의 키 */
export const HOUSING_TYPES = [
  { id: 'apartment', label: '아파트·빌라', icon: 'housingApartment' },
  { id: 'studio', label: '원룸·오피스텔', icon: 'housingStudio' },
  { id: 'house', label: '단독주택', icon: 'housingHouse' },
  { id: 'other', label: '기타\n(사무실 등)', icon: 'housingOther' },
];

/** Auth/ProfileHousing › InfoBox */
export const HOUSING_INFO_NOTE = '입주청소를 원하시는 경우 \n이사 가는 부동산의 형태를 선택해주세요';

/** Auth/ProfileHousing › PetGrid */
export const PET_OPTIONS = [
  { id: 'yes', label: '있음', icon: 'petYes' },
  { id: 'no', label: '없음', icon: 'petNo' },
];

/** 홈 '다가오는 방문' 카드 (코드에서 정함 — 사용성 검토 2026-09-22) */
export const UPCOMING_HOME_COPY = { title: '다가오는 방문', note: '변경·취소는 "내 예약" 에서 할 수 있어요' };

/**
 * 홈 상단 슬라이드 (Home/Realtime (2:323) · Home/Standard-A (63:346) › HeroCarousel) — 슬라이드 순서 Figma 카운터(01/02, 02/02) 그대로.
 * image/peek 는 ui ASSETS 의 키.
 * test/fakeHttp.js 의 GET /customer/home 가짜 응답이 slides 로 쓴다
 */
export const HOME_SLIDES = [
  { id: 'realtime', eyebrow: '안심되는 청소,', headline: '지금 시작해요', title: '실시간 청소', description: '내가 없는 순간에도, \n보이니까 더 안심되는 청소', image: 'heroRealtime', peek: 'heroRealtimePeek' },
  { id: 'standard', eyebrow: '깨끗한 하루,', headline: '지금 시작해요', title: '일반 청소', description: '깨끗함이 만드는\n기분 좋은 당신의 하루', image: 'heroRoom', peek: 'heroPeek' },
];

/** Repair (84:254) › HeroSection */
export const REPAIR_HERO = { eyebrow: '머리 아픈 집수리, ', headline: '안심클린에 맡겨요', title: '수리 서비스', description: '전문 기사가 방문해 진단부터\n수리까지 한 번에 해결해드려요.', image: 'repairHero' };

/** Reservations (2047:1683) — 문구 Figma 그대로. status: 'scheduled'(방문 예정) | 'assigning'(매니저 배정 중). tab: 'upcoming' | 'done' | 'cancelled' */
// test/fakeHttp.js 가 가짜 서버의 처음 예약 목록으로 쓴다 (GET /customer/reservations·/customer/home). 실서버는 DB 예약을 준다
export const RESERVATIONS = [
  { id: 'r1', tab: 'upcoming', category: 'home', title: '가정/원룸 청소', status: 'scheduled', date: '2026-11-01', time: '09:00', rows: [{ label: '일정', value: '2026년 11월 1일 (일) 오전 9시' }, { label: '주소', value: '부산광역시 중앙대로 2127-23' }, { label: '매니저', value: '김미영 매니저' }] },
  { id: 'r2', tab: 'upcoming', category: 'appliance', title: '가전청소', status: 'assigning', date: '2026-11-14', time: '14:00', rows: [{ label: '일정', value: '2026년 11월 14일 (토) 오후 2시' }, { label: '주소', value: '부산광역시 중앙대로 2127-23' }, { label: '매니저', value: '배정 후 알려드려요' }] },
  // 아래 3건은 Figma 에 없음 — 진행 중(Service/Start 진입점)·완료·취소 탭을 채우려고 코드에서 정함
  { id: 'r0', tab: 'upcoming', category: 'home', title: '가정/원룸 청소', status: 'inProgress', date: '2026-09-19', time: '14:00', rows: [{ label: '일정', value: '오늘 오후 2시' }, { label: '주소', value: '부산광역시 중앙대로 2127-23' }, { label: '매니저', value: '김미영 매니저' }] },
  { id: 'r3', tab: 'done', category: 'appliance', title: '가전청소', status: 'done', date: '2026-08-03', time: '14:00', rows: [{ label: '일정', value: '2026년 8월 3일 (월) 오후 2시' }, { label: '주소', value: '부산광역시 중앙대로 2127-23' }, { label: '매니저', value: '김미영 매니저' }] },
  { id: 'r4', tab: 'cancelled', category: 'office', title: '사무실 청소', status: 'cancelled', date: '2026-07-20', time: '10:00', rows: [{ label: '일정', value: '2026년 7월 20일 (월) 오전 10시' }, { label: '주소', value: '부산광역시 중앙대로 2127-23' }, { label: '매니저', value: '배정 전 취소' }] },
];

/** Reservations › PageHeader · SegmentTabs (탭 문구 "예정 2" 의 2 는 건수) */
export const RESERVATIONS_HEADER = { title: '내 예약을\n확인해주세요', description: '예정된 일정과 지난 이용 내역을\n한 곳에서 볼 수 있어요.' };
export const RESERVATION_TABS = [
  { key: 'upcoming', label: '예정' },
  { key: 'done', label: '완료' },
  { key: 'cancelled', label: '취소' },
];

/** Settings (2048:1709) — 문구·순서 Figma 그대로 */
// SETTINGS_HEADER·PROFILE·SETTINGS_GROUPS 는 test/fakeHttp.js 의 GET /customer/settings 가짜 응답이 쓴다
export const SETTINGS_HEADER = { title: '내 정보와\n설정을 관리해요', description: '계정, 알림, 결제 수단을\n한 곳에서 관리할 수 있어요.' };
export const PROFILE = { name: '김하늘 님', phone: '010-1234-5678', avatarSrc: '' };
export const SETTINGS_GROUPS = [
  { title: '계정', rows: [{ key: 'editProfile', label: '내 정보 수정' }, { key: 'addresses', label: '주소 관리' }, { key: 'payment', label: '결제 수단' }] },
  { title: '알림', rows: [{ key: 'push', label: '푸시 알림', toggle: true }, { key: 'marketing', label: '마케팅 정보 수신', toggle: true }] },
  { title: '고객지원', rows: [{ key: 'faq', label: '자주 묻는 질문' }, { key: 'inquiry', label: '1:1 문의하기' }, { key: 'notice', label: '공지사항' }] },
  { title: '약관 및 정보', rows: [{ key: 'terms', label: '이용약관' }, { key: 'privacy', label: '개인정보 처리방침' }, { key: 'version', label: '버전 정보', value: 'v1.0.0' }] },
];
/** 알림 토글 초기값 (Settings › 알림). test/fakeHttp.js 의 GET /customer/settings 가짜 응답이 쓴다 */
export const NOTIFICATION_SETTINGS = { push: true, marketing: false };
/** 라이브 연결·캡처 실패 문구 (코드에서 정함) */
export const LIVE_ERROR_COPY = '영상 연결에 문제가 있어요. 잠시 후 다시 시도해주세요.';
/** 서버 토글은 ON 인데 기기(OS) 알림 권한이 꺼져 있을 때 (코드에서 정함) */
export const PUSH_OS_DENIED_NOTE = '기기 설정에서 이 앱의 알림이 꺼져 있어요. 켜야 알림을 받을 수 있어요.';
/** 알림 토글 저장 실패 문구 (코드에서 정함) — Settings.jsx 가 ActionError 로 보여 준다 */
export const NOTIFICATION_TOGGLE_FAILED = '알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해주세요.';

// 여기부터 예약 플로우 값. 실제 앱은 서버의 /catalog/categories/…·/catalog/time-slots 응답을 쓰고,
// 아래 카테고리·옵션·주의사항 칩·확인 문구·시간표는 test/fakeHttp.js 가 같은 경로를 흉내 낼 때 쓴다.
/** 예약 플로우 카테고리 (Figma 섹션 HomeCleaning/OfficeCleaning/ApplianceCleaning/MoveInCleaning). label = 각 Frequency 프레임의 ServiceLabel 문구.
 *  promo: home 만 Banner/RecurringPromo, 나머지는 PromoBanner (자리표시 · 이미지 교체 필요) 의 Figma 문구 그대로 */
export const BOOKING_CATEGORIES = {
  home: { label: '가정/원룸 청소', promo: 'recurring' },
  office: { label: '사무실 청소', promo: 'placeholder', promoText: '프로모션 배너 영역\nOffice 전용 이미지 필요' },
  appliance: { label: '가전청소', promo: 'placeholder', promoText: '프로모션 배너 영역\nAppliance 전용 이미지 필요' },
  'move-in': { label: '입주(이사) 청소', promo: 'placeholder', promoText: '프로모션 배너 영역\nMoveIn 전용 이미지 필요' },
};

/** Booking/Frequency › RadioOption 문구 (기본 선택: 정기청소 — Figma Selected) */
export const FREQUENCY_OPTIONS = [ // api/customer.js getFrequencyOptions 가 그대로 돌려준다
  { key: 'recurring', label: '정기청소' },
  { key: 'oneTime', label: '1회 청소' },
];

/** Booking/DateTime › TimeSlotGrid 문구 */
export const TIME_SLOTS = { am: ['09:00', '10:00', '11:00', '12:00'], pm: ['13:00', '14:00', '15:00', '16:00', '17:00', '18:00'] };
/** 오늘부터 며칠 뒤까지 예약할 수 있나 — 서버 app_settings.booking_horizon_days(V026)와 같다 (코드에서 정함, 전체 재점검 B1) */
export const BOOKABLE_DAYS = 60;

/** Booking/Step2 › RadioOption 문구 (기본 선택: 네 — Figma Selected) */
export const ADDRESS_REUSE_OPTIONS = [ // api/customer.js getAddressReuseOptions 가 그대로 돌려준다
  { key: 'yes', label: '네' },
  { key: 'no', label: '아니요' },  // Figma 는 "아니오" — 맞춤법대로 "아니요" (사용자 지시 2026-10-23)
];

/** Booking/Options — 카테고리별 무료 옵션(택2) 3 + 유료 옵션 2. 문구·가격·순서 Figma 그대로. icon 은 ui ASSETS.bookingOptionIcons[category] 의 index */
export const BOOKING_OPTIONS = {
  home: {
    free: [{ key: 'dishes', label: '설거지', icon: 0 }, { key: 'laundry', label: '세탁', icon: 1 }, { key: 'bedding', label: '침구교체', icon: 2 }],
    paid: [{ key: 'pet', label: '반려동물\n배변 청소', price: '+3,000원', icon: 3 }, { key: 'fridge', label: '냉장고 \n정리 및 청소', price: '+10,000원', icon: 4 }],
  },
  office: {
    free: [{ key: 'pantry', label: '탕비실 정리', icon: 0 }, { key: 'meeting', label: '회의실 정돈', icon: 1 }, { key: 'recycle', label: '분리배출', icon: 2 }],
    paid: [{ key: 'carpet', label: '카펫\n스팀 청소', price: '+50,000원', icon: 3 }, { key: 'window', label: '유리창\n외부 청소', price: '+35,000원', icon: 4 }],
  },
  appliance: {
    free: [{ key: 'filter', label: '필터 세척', icon: 0 }, { key: 'polish', label: '외관 광택', icon: 1 }, { key: 'drain', label: '배수구 청소', icon: 2 }],
    paid: [{ key: 'ac', label: '에어컨\n분해 청소', price: '+40,000원', icon: 3 }, { key: 'washer', label: '세탁조\n살균', price: '+25,000원', icon: 4 }],
  },
  'move-in': {
    free: [{ key: 'sill', label: '창틀 청소', icon: 0 }, { key: 'veranda', label: '베란다', icon: 1 }, { key: 'closet', label: '붙박이장', icon: 2 }],
    paid: [{ key: 'grout', label: '줄눈\n시공', price: '+30,000원', icon: 3 }, { key: 'sick', label: '새집증후군\n케어', price: '+50,000원', icon: 4 }],
  },
};
export const FREE_OPTION_LIMIT = 2; // "무료 옵션(택2)"

/** Booking/NotesRequests › NoticeChip — 카테고리별 9개 (문구·아이콘 Figma 그대로) */
export const NOTICE_CHIPS = {
  home: [
    { key: 'pet', label: '반려동물이 있어요', icon: 'pet' }, { key: 'baby', label: '영유아가 있어요', icon: 'baby' }, { key: 'smoke', label: '흡연 공간이 있어요', icon: 'smoke' },
    { key: 'valuables', label: '귀중품이 있어요', icon: 'lock' }, { key: 'stairs', label: '계단 이용이 필요해요', icon: 'stairs' }, { key: 'noElevator', label: '엘리베이터 없음', icon: 'elevator' },
    { key: 'remodel', label: '리모델링 후 청소예요', icon: 'tools' }, { key: 'caution', label: '특별히 주의할 공간이 있어요', icon: 'warning' }, { key: 'etc', label: '기타', icon: 'dots' },
  ],
  office: [
    { key: 'secure', label: '보안 구역이 있어요', icon: 'lock' }, { key: 'access', label: '출입 등록이 필요해요', icon: 'lock' }, { key: 'night', label: '야간 작업이 필요해요', icon: 'clock' },
    { key: 'weekend', label: '주말만 가능해요', icon: 'clock' }, { key: 'server', label: '서버실이 있어요', icon: 'server' }, { key: 'elevator', label: '엘리베이터 제한 있어요', icon: 'elevator' },
    { key: 'desk', label: '개인 자리는 제외해주세요', icon: 'desk' }, { key: 'caution', label: '특별히 주의할 공간이 있어요', icon: 'warning' }, { key: 'etc', label: '기타', icon: 'dots' },
  ],
  appliance: [
    { key: 'wall', label: '벽걸이형이에요', icon: 'ac' }, { key: 'ceiling', label: '천장형이에요', icon: 'ac' }, { key: 'stand', label: '스탠드형이에요', icon: 'ac' },
    { key: 'old', label: '설치 10년이 넘었어요', icon: 'clock' }, { key: 'ladder', label: '사다리 작업이 필요해요', icon: 'stairs' }, { key: 'noWater', label: '물 사용이 어려워요', icon: 'droplet' },
    { key: 'warranty', label: '보증기간 중이에요', icon: 'shield' }, { key: 'caution', label: '특별히 주의할 기기가 있어요', icon: 'warning' }, { key: 'etc', label: '기타', icon: 'dots' },
  ],
  'move-in': [
    { key: 'boxes', label: '이삿짐이 아직 있어요', icon: 'box' }, { key: 'new', label: '신축 건물이에요', icon: 'building' }, { key: 'remodel', label: '리모델링 직후예요', icon: 'tools' },
    { key: 'noElevator', label: '엘리베이터가 없어요', icon: 'elevator' }, { key: 'noParking', label: '주차 공간이 없어요', icon: 'parking' }, { key: 'utilities', label: '수도·전기 사용 가능해요', icon: 'droplet' },
    { key: 'pet', label: '반려동물이 있어요', icon: 'pet' }, { key: 'caution', label: '특별히 주의할 공간이 있어요', icon: 'warning' }, { key: 'etc', label: '기타', icon: 'dots' },
  ],
  // 수리(Figma rGCjx3zWMBk18fXgNZ9UYX 1:341) — 서버 V012 catalog_notice_chips 와 같다. 가짜 서버가 쓴다
  repair: [
    { key: 'parking', label: '주차 가능해요', icon: 'parking' }, { key: 'elevator', label: '엘리베이터 있어요', icon: 'elevator' },
    { key: 'pet', label: '반려동물이 있어요', icon: 'pet' }, { key: 'intercom', label: '공동현관 호출 필요', icon: 'lock' },
  ],
};

/** Booking/NotesRequests 문구 */
export const NOTES_COPY = {
  title: '주의사항/\n요청사항을 알려주세요',
  description: '더 꼼꼼하고 만족스러운\n청소를 위해 미리 알려주세요',
  noticeTitle: '주의사항',
  noticeDescription: '해당되는 내용이 있다면 선택해주세요.',
  requestTitle: '요청사항',
  requestDescription: '청소 시 요청사항이 있다면 자유롭게 입력해주세요.',
  requestPlaceholder: '여기에 작성해주세요',
  tipTitle: '이렇게 요청해보세요!',
  tipBody: '-특히 신경 써줬으면 하는 공간이나 가구가 있다면 알려주세요.\n-청소 전 확인이 필요한 사항이 있다면 미리 알려주세요.\n-기타 특별한 요청사항이 있다면 자세히 적어주세요.',
};

/** Booking/Confirm › SummaryRow/Frequency 부가 문구 — 카테고리 × 주기 (Figma 4 프레임에서 뽑음. 비어 있는 조합은 home 문구를 씀).
 *  recurring 문구는 Figma 가 "매주 지정한 요일에 진행해요" 지만 정기청소는 **매월 1회**다(사용자 결정 2026-09-22) → 사실대로. 디자이너 확인 필요 */
export const FREQUENCY_DESCRIPTIONS = {
  home: { recurring: '매월 같은 날짜에 한 번 진행해요', oneTime: '원하는 날짜에 한 번만 진행해요.' },
  office: { recurring: '매월 같은 날짜에 한 번 진행해요', oneTime: '원하는 날짜에 한 번만 진행해요' },
  appliance: { recurring: '매월 같은 날짜에 한 번 진행해요', oneTime: '원하는 날짜에 한 번만 진행해요' },
  'move-in': { recurring: '매월 같은 날짜에 한 번 진행해요', oneTime: '입주 전 한 번만 진행해요' },
};

/**
 * 정기청소 개월 수 = 회차 수의 기본값 = 서버 app_settings.series_term_months (V021 에서 6).
 * 주기 화면은 서버 confirm-copy 응답의 seriesTermMonths 를 쓰고, 이 값은 응답에 그 칸이 없을 때와 가짜 서버만 쓴다 (문제 목록 25번)
 */
export const SERIES_TERM_MONTHS = 6;
/**
 * 정기청소가 무엇을 약속하는지 (코드에서 정함 — 배너는 '원하는 주기' 를 말하는데 실제로는 매주 한 달이었다, 사용성 검토 2026-09-22).
 * months = 서버가 준 회차 수(seriesTermMonths). 개월 수와 회차 수가 같다(매월 1회). 6 이면 '6개월 동안 6회'
 */
export const RECURRING_TERM_NOTE = (months) => `정기청소는 매월 같은 날짜·같은 시간에 한 번씩, ${months}개월 동안 ${months}회 방문해요. 주말에도 선택한 날짜를 유지해요. 회차마다 따로 변경·취소할 수 있어요.`;

/** Booking/Confirm 문구 */
export const CONFIRM_COPY = { title: '예약내용을\n확인해주세요', description: '아래 내용으로 예약을 진행할게요.\n다시 한번 확인해주세요.' };

/** Booking/Complete › BookingInfoCard 라벨 (값은 예약 내용에서) */
export const COMPLETE_ROWS = [ // api/customer.js getCompleteRows 가 그대로 돌려준다. key 로 예약 내용의 값을 찾는다
  { key: 'date', icon: 'calendar', label: '예약일' },
  { key: 'time', icon: 'clock', label: '예약시간' },
  { key: 'address', icon: 'pin', label: '서비스 주소' },
  { key: 'cleaner', icon: 'person', label: '담당 클리너' },
  // Figma 에 없음 — 코드에서 정함 (BACKEND_PLAN.md §9.2-7: 완료 카드에 총 금액 행)
  { key: 'amount', icon: 'won', label: '결제 금액' },
];

/** Booking/Step2 "네(기존 주소 사용)" 일 때 쓰는 기존 주소 — Booking/Confirm (64:168) 의 예시 값 그대로. 실서버에서는 회원 주소 */
export const SAVED_ADDRESS = { roadAddress: '부산광역시 중앙대로 2127-23', detailAddress: '금정아파트 802동 203호' };
/** Booking/Complete › 담당 클리너 배정 전 문구 (Reservations 의 "배정 후 알려드려요" 재사용) */
export const CLEANER_PENDING = '배정 후 알려드려요';

/** Service/LivePortrait (2167:2238) · LiveLandscape (2165:2222) 에 보이는 값 그대로. 실제 세션 정보는 서버(GET /customer/bookings/:id/live)가 준다 */
// 쓰는 곳: test/fakeHttp.js 의 라이브 세션 가짜 응답, 그리고 DEV 에서 예약 id 없이 라이브 화면을 열 때 api/customer.js getLiveSession
export const LIVE_SESSION = {
  manager: { name: '김미영 매니저', description: '경력 7년 · 정리정돈부터 꼼꼼한 생활청소까지', avatar: 'managerAvatar' },
  startedAt: '2025. 09. 11. 14:32',
  viewers: 1,
  status: { title: '거실 청소 중', description: '깨끗한 공간을 만들어가고 있어요!' },
  reassurance: { title: '안심하고 지켜보세요!', description: '항상 최선을 다해 청소하고 있어요 💙' },
  /** 녹화본 탐색 표시 (LiveLandscape 예시: 5:20 / 10:30, 진행 410/804) */
  playback: { currentLabel: '5:20', totalLabel: '10:30', ratio: 320 / 630, currentSeconds: 320, totalSeconds: 630 },
};

/** PDF의 ProgressOverlay 카드와 다시보기 구간 예시. 실서비스는 고객 체크리스트 API를 쓴다. */
export const LIVE_CHECKLIST = {
  done: 4, total: 9, percent: 44,
  groups: [
    { group: '거실', done: 3, total: 3, items: [
      { id: 'c1', label: '바닥 청소', done: true }, { id: 'c2', label: '먼지 제거', done: true }, { id: 'c3', label: '테이블 정리', done: true },
    ] },
    { group: '주방', done: 1, total: 3, items: [
      { id: 'c4', label: '싱크대', done: true }, { id: 'c5', label: '가스레인지', done: false }, { id: 'c6', label: '조리대', done: false },
    ] },
    { group: '욕실', done: 0, total: 3, items: [
      { id: 'c7', label: '세면대', done: false }, { id: 'c8', label: '변기', done: false }, { id: 'c9', label: '바닥', done: false },
    ] },
  ],
  timeline: [
    { id: 'c1', group: '거실', label: '바닥 청소', offsetSeconds: 165 },
    { id: 'c2', group: '거실', label: '먼지 제거', offsetSeconds: 190 },
    { id: 'c3', group: '거실', label: '테이블 정리', offsetSeconds: 245 },
    { id: 'c4', group: '주방', label: '싱크대', offsetSeconds: 320 },
  ],
  requests: [],
};
export const LIVE_CHECKLIST_COPY = {
  title: '청소 체크리스트', basis: '매니저 완료 체크 기준', requestsTitle: '고객 요청사항',
  requestsStatus: '완료 여부 미확인', requestsEmpty: '등록된 요청사항 정보가 제공되지 않았습니다.',
  requestsNote: '청소 진행률과 별도로 완료 상태를 표시합니다.',
};

/**
 * 청소 전·후 비교 사진 (2026-10-23 사용자 요청 — Figma 에 없어 코드에서 정함). GET /customer/bookings/{id}/photos 모양.
 * ready = 매니저가 청소를 완료했다. 완료 전에는 ready=false·빈 목록이고, 완료 뒤에는 전·후가 다 있는 위치만 온다
 */
export const CLEANING_PHOTOS = {
  ready: true,
  photos: [
    { id: 'photo-1', location: '거실 소파 앞', beforeUrl: 'https://example.com/photos/photo-1-before.jpg', afterUrl: 'https://example.com/photos/photo-1-after.jpg' },
    { id: 'photo-2', location: '주방 싱크대', beforeUrl: 'https://example.com/photos/photo-2-before.jpg', afterUrl: 'https://example.com/photos/photo-2-after.jpg' },
  ],
};
/** 녹화본 화면의 청소 전·후 비교 묶음 문구 (코드에서 정함) */
export const CLEANING_PHOTOS_COPY = {
  title: '청소 전·후 비교',
  hint: '가운데 막대를 좌우로 밀어 청소 전과 후를 비교해보세요.',
  before: '청소 전',
  after: '청소 후',
};

/** 약관 본문 (Figma 에 화면 없음 — 코드에서 정함). 실제 문안은 법무 검토 후 실서버에서 내려받는다 */
export const TERM_BODIES = {
  service: { title: '서비스 이용약관', body: '제1조 (목적)\n이 약관은 안심클린이 제공하는 청소 서비스의 이용 조건과 절차, 회사와 이용자의 권리·의무를 정합니다.\n\n제2조 (서비스의 내용)\n회사는 이용자가 예약한 일시에 청소 매니저를 배정하고, 청소 과정을 실시간 영상으로 제공합니다.\n\n※ 최종 문안은 법무 검토 후 반영됩니다.' },
  privacy: { title: '개인정보 수집 및 이용 동의', body: '수집 항목: 이름, 닉네임, 휴대폰 번호, 주소, 거주 정보\n이용 목적: 예약 매칭, 방문 서비스 제공, 고객 응대\n보유 기간: 회원 탈퇴 시 또는 관계 법령이 정한 기간\n\n※ 최종 문안은 법무 검토 후 반영됩니다.' },
  location: { title: '위치기반 서비스 이용 동의', body: '주소와 위치 정보는 청소 매니저 매칭과 방문에만 사용되며 다른 이용자에게 공개되지 않습니다.\n\n※ 최종 문안은 법무 검토 후 반영됩니다.' },
  marketing: { title: '마케팅 정보 수신 동의', body: '할인·이벤트 소식을 앱 알림과 문자로 보내드립니다. 설정 > 알림에서 언제든 해제할 수 있습니다.' },
  ux: { title: '서비스 개선을 위한 사용자 경험 수집 동의', body: '앱 사용 패턴을 익명으로 수집해 서비스 개선에 활용합니다. 개인을 식별하는 정보는 포함되지 않습니다.' },
};

/** Booking/AddressSearch › MapPreview 이미지에 그려진 결과 4건 (mock). test/fakeHttp.js 의 GET /catalog/address 가짜 응답만 쓴다 — 실서버는 카카오 로컬 API 결과를 준다 */
export const ADDRESS_RESULTS = [
  { id: 'addr-1', main: '서울특별시 강남구 테헤란로 123', sub: '(역삼동, ABC빌딩)' },
  { id: 'addr-2', main: '서울특별시 강남구 테헤란로 123-1', sub: '(역삼동)' },
  { id: 'addr-3', main: '서울특별시 강남구 테헤란로 123길 5', sub: '(역삼동)' },
  { id: 'addr-4', main: '서울특별시 강남구 테헤란로 123길 12', sub: '(역삼동)' },
];

/** Settings › 내 정보 수정 · 주소 관리 · FAQ · 1:1 문의 · 공지사항 (Figma 에 화면 없음 — 코드에서 정함) */
// PROFILE_FORM·ADDRESSES·FAQ·NOTICES 는 test/fakeHttp.js 가 설정·고객지원 가짜 응답을 만들 때 쓴다 (실서버는 DB 값을 준다)
export const PROFILE_FORM = { name: '김하늘', nickname: '하늘', phone: '12345678' };
/** 설정 › 주소 관리의 주소 목록 예시. isDefault=true 가 기본 주소('예약 기본 주소' 배지) */
export const ADDRESSES = [{ id: 'home', label: '집', roadAddress: SAVED_ADDRESS.roadAddress, detailAddress: SAVED_ADDRESS.detailAddress, isDefault: true }];
/** 설정 › 주소 관리 — 어느 주소가 예약 "기존 주소" 인지와 바꾸기 (코드에서 정함, 직접 사용 점검 U15) */
export const ADDRESS_MANAGE_COPY = {
  defaultBadge: '예약 기본 주소',
  makeDefault: '기본으로',
  madeDefault: '예약에 쓸 기본 주소를 바꿨어요',
  failed: '기본 주소를 바꾸지 못했어요. 다시 시도해주세요.',
};
/** 설정 › 자주 묻는 질문 (질문·답). 답 문구 일부는 서버 마이그레이션(V027·V035·V049·V050)이 고친 문구와 맞춰 두었다 */
export const FAQ = [
  // V049 와 같게 — 기한 시간은 운영 설정이라 FAQ 글자에 숫자를 두지 않는다 (문제 목록 25번. 그 전 V027, 직접 사용 점검 U16)
  { id: 'f1', question: '예약을 변경하거나 취소할 수 있나요?', answer: '내 예약 탭에서 예약 카드의 예약 변경 · 예약 취소 버튼으로 할 수 있어요. 방문이 가까워 기한이 지나면 버튼 대신 기한 안내가 보이고, 그 뒤에는 고객센터로 문의해주세요.' },
  { id: 'f2', question: '실시간 영상은 어떻게 보나요?', answer: '청소가 시작되면 내 예약 카드에 실시간 영상 보기 버튼이 나타나요. 영상은 서버에 녹화되어 나중에도 볼 수 있어요.' },
  // V050 과 같게 — 밤(21시~08시)에 생긴 콜은 아침 08:05 에 나간다(문제 목록 2번). 그 전 V035 (코드에서 정함, 자체 점검 #30 · 합친 38건 검토 R9)
  { id: 'f3', question: '매니저는 언제 배정되나요?', answer: '1회 청소는 예약하면 바로 매니저를 찾기 시작해요. 밤 9시~아침 8시에 한 예약은 아침 8시부터 찾아요. 정기청소는 첫 방문은 바로, 이후 방문은 각 방문 7일 전부터 찾아요. 배정되면 알림으로 알려드려요.' },
  { id: 'f4', question: '결제는 언제 되나요?', answer: '지금은 청소가 끝난 뒤 안내드리는 방법으로 결제해요. 앱에서 결제 수단을 등록하는 기능은 결제 서비스를 연동한 뒤 제공돼요.' },
];
/** 설정 › 1:1 문의하기 화면(Inquiry.jsx) 문구 — 제목·안내·입력 자리 글·접수 완료 */
export const INQUIRY_COPY = { title: '1:1 문의하기', description: '불편한 점이나 궁금한 점을 남겨주세요. 영업일 기준 1~2일 안에 답변드려요.', placeholder: '문의 내용을 입력해주세요', done: '문의가 접수되었어요' };
/** 설정 › 공지사항 예시. fakeHttp 는 목록에 id·title·date 만, 상세에 body 까지 준다 */
export const NOTICES = [ // 추석 연휴 공지는 V029 로 뺐다 (공휴일은 예약을 막지 않는다 — 사용자 결정 2026-09-24)
  { id: 'n2', title: '실시간 청소 영상 기능이 추가되었어요', date: '2026.09.01', body: '이제 청소가 진행되는 동안 내 예약에서 실시간 영상을 볼 수 있어요. 영상은 안전하게 녹화되어 청소 후에도 확인할 수 있습니다.' },
  { id: 'n3', title: '개인정보 처리방침 개정 안내', date: '2026.08.15', body: '개인정보 처리방침이 개정되어 9월 1일부터 적용됩니다. 설정 > 약관 및 정보에서 확인하실 수 있어요.' },
];
/** 내 예약 › 예약 취소 확인 시트 문구 (Reservations.jsx 의 Sheet) */
export const CANCEL_COPY = {
  title: '예약을 취소할까요?',
  description: '취소하면 되돌릴 수 없어요. 매니저에게도 취소 사실이 전달됩니다.',
  confirm: '예약 취소',
  back: '돌아가기',
  /** 정기청소 회차(series: true)일 때의 범위 선택 (BACKEND_PLAN.md §9.2-5 — 코드에서 정함) */
  scopeOptions: [
    { key: 'one', label: '이번 회차만' },
    { key: 'all', label: '정기청소 전체' },
  ],
  /** hours = 서버가 준 변경·취소 기한(내 예약 응답의 changeDeadlineHours) — 전체 취소는 이 기한 안의 회차를 남긴다 */
  seriesNote: (hours) => `${hours}시간 안에 시작하는 방문은 취소할 수 없어요.`,
  /** 정기청소일 때 버튼이 무엇을 취소하는지 그대로 말한다 — 문구가 나타나며 버튼이 밀려 잘못 눌리는 일이 있었다 (사용성 검토 2026-09-22) */
  confirmScope: { one: '이 회차만 취소하기', all: '정기청소 전체 취소하기' },
  failed: '취소하지 못했어요. 잠시 후 다시 시도해주세요.',
};
/** Booking/Confirm › "요금" 행 (Figma 에 없음 — 코드에서 정함, §9.2-7) */
/** 요금 내역 줄 이름 (코드에서 정함 — 총액 한 줄뿐이라 옵션·할인이 얼마인지 알 수 없었다, 사용성 검토 2026-09-22) */
export const QUOTE_COPY = {
  title: '요금',
  recurring: (perVisit, visits, total) => `1회 ${perVisit} · ${visits}회 총 ${total}`,
  baseLabel: '기본 요금',
  optionsLabel: '선택 옵션',
  savingLabel: '정기 할인',
  failed: '요금을 불러오지 못했어요. 다시 시도해주세요.',
};
/** 내 예약 탭별 빈 상태 문구 (키 = RESERVATION_TABS 의 key). Reservations.jsx 의 EmptyState 가 쓴다 */
export const RESERVATIONS_EMPTY = { upcoming: '예정된 예약이 없어요', done: '완료된 예약이 없어요', cancelled: '취소한 예약이 없어요' };

/**
 * 수리 사진 만료 안내 — 서버 RepairPhotoService.PHOTO_EXPIRED 와 글자 하나까지 같다 (코드에서 정함, 재감사 M-04).
 * 두 곳에 쓴다: ① 증상 상세 화면이 올린 지 24시간 지난 사진을 초안에서 뺄 때 보이는 문구 ② 예약 생성이 이 문구의 VALIDATION 으로
 * 막혔는지 알아보는 기준(repairFlow.js isPhotoExpiredError). 서버 문구를 바꾸면 여기도 같이 바꾼다 — 다르면 ②가 만료를 알아채지 못한다
 */
export const REPAIR_PHOTO_EXPIRED = '첨부한 사진이 만료됐어요. 사진을 다시 올려주세요';

/**
 * 수리 예약 완료 화면 머리글 설명(Figma 1:236)의 첫 문장. 아래 REPAIR_BOOKING.copy.complete.description 은 이 문장 + 배정 알림 문장이다(Figma 그대로).
 * 매칭에 실패했거나 취소된 예약이면 완료 화면은 배정 알림을 약속하는 뒷문장을 빼고 이 첫 문장만 보인다 — 새 글자가 아니라 Figma 문구의 앞부분이다 (코드에서 정함, 합친 38건 검토 R8·R16)
 */
const REPAIR_COMPLETE_CHECK = '방문 일정과 주소를 확인해 주세요.';

/** 수리 예약 플로우 (Figma rGCjx3zWMBk18fXgNZ9UYX, 8화면). 문구는 Figma 그대로. 가전수리 외 카테고리의 가전·증상 목록은 Figma 에 없다 — TODO(figma) */
// api/customer.js getRepairBooking 이 이 값에 서버의 시간표·현장 상황 칩을 덮어 수리 예약 화면들에 돌려준다
export const REPAIR_BOOKING = {
  serviceName: '가전수리',
  appliances: [
    { key: 'fridge', label: '냉장고' },
    { key: 'washer', label: '세탁기 · 건조기' },
    { key: 'ac', label: '에어컨' },
    { key: 'tv', label: 'TV · 모니터' },
    { key: 'kitchen', label: '주방가전' },
    { key: 'other', label: '기타 가전' },
  ],
  /** 냉장고만 Figma 에 있다(1:515). 다른 가전은 그중 공통 증상 3개만 (코드에서 정함, PROGRESS.md 판단 항목) */
  symptoms: {
    fridge: [
      { key: 'cooling', label: '냉장이 잘 안 돼요' },
      { key: 'leak', label: '물이 새거나 성에가 생겨요' },
      { key: 'noise', label: '소음이 크거나 진동이 있어요' },
      { key: 'power', label: '전원이 켜지지 않아요' },
      { key: 'door', label: '문이 잘 닫히지 않아요' },
      { key: 'other', label: '기타 증상이에요' },
    ],
    default: [
      { key: 'noise', label: '소음이 크거나 진동이 있어요' },
      { key: 'power', label: '전원이 켜지지 않아요' },
      { key: 'other', label: '기타 증상이에요' },
    ],
  },
  /** 현장 상황 칩(1:341). summary 는 확인 화면 "주차 가능 · 엘리베이터 있음"(1:318) 형식 — 나머지 둘은 코드에서 정함 */
  situations: [
    { key: 'parking', label: '주차 가능해요', summary: '주차 가능' },
    { key: 'elevator', label: '엘리베이터 있어요', summary: '엘리베이터 있음' },
    { key: 'pet', label: '반려동물이 있어요', summary: '반려동물 있음' },
    { key: 'intercom', label: '공동현관 호출 필요', summary: '공동현관 호출 필요' },
  ],
  timeSlots: { am: ['09:00', '10:00', '11:00'], pm: ['13:00', '14:00', '15:00', '16:00', '17:00', '18:00'] }, // 갤러리·fallback 용 — 화면은 서버 time_slots 를 쓴다
  copy: {
    appliance: { title: '어떤 가전을 수리할까요?', description: '수리가 필요한 가전 한 가지를 선택해주세요.' },
    symptom: { title: '어떤 증상이 있나요?', description: '가장 가까운 증상 한 가지를 선택해주세요.' },
    detail: {
      title: '증상을 자세히 알려주세요', description: '기사님이 미리 확인할 수 있도록 설명과 사진을 남겨주세요.',
      summaryTitle: '현재 선택', summaryBadge: '3/4 단계', rows: { service: '서비스', appliance: '가전', photos: '사진' }, photosValue: (n) => (n ? `${n}장 첨부됨` : '없음'),
      fieldLabel: '증상 상세', fieldPlaceholder: '냉장실 온도가 올라가고 모터 소리가 평소보다 크게 들려요.',
      photosTitle: '사진 첨부 · 선택', photosHelper: '제품 전체와 문제가 보이는 부분을 함께 찍으면 더 정확히 안내할 수 있어요.',
      photosExpired: REPAIR_PHOTO_EXPIRED, // 만료된 사진을 초안에서 뺐을 때 사진 칸 아래 안내 (코드에서 정함 — 위 REPAIR_PHOTO_EXPIRED)
    },
    address: {
      title: '이 주소로 방문할까요?', description: '등록된 서비스 주소를 확인해주세요.', cardTitle: '현재 선택된 주소', caption: '서비스 방문지',
      changeHint: '주소가 다르면 변경해주세요', changeButton: '다른 주소로 변경', note: '정확한 방문을 위해 동·호수와 공동현관 정보를 확인해주세요.',
      empty: '등록된 주소가 없어요. 방문할 주소를 입력해주세요.',
    },
    datetime: { title: '방문 일정을 선택해주세요', description: '현재 선택한 날짜와 시간을 확인하고 다음 단계로 넘어갈 수 있어요.', timeTitle: '방문 시간 선택' },
    notes: {
      title: '기사님께 알려주세요', description: '현장 상황과 방문 전 확인할 내용을 남겨주세요.', situationsTitle: '현장 상황 · 중복 선택',
      fieldLabel: '기사님께 전할 요청사항', fieldPlaceholder: '방문 10분 전에 연락 부탁드려요. 공동현관은 경비실 호출해주세요.',
      safety: '냉장고 주변의 물건을 미리 정리하면 점검이 더 빠르게 진행돼요.',
    },
    confirm: {
      title: '예약내용을 확인해주세요', description: '아래 내용으로 예약을 진행할게요. 다시 한번 확인해주세요.',
      rows: { service: '서비스', detail: '증상 상세', address: '서비스 주소', schedule: '방문 일정', situations: '현장 상황' },
      photosSub: (n) => (n ? `사진 ${n}장 첨부` : '사진 없음'), noSituations: '선택 안 함',
      costTitle: '수리비는 현장 진단 후 확정돼요', costBody: '출장 및 진단 후 필요한 부품과 작업 범위를 안내드리며, 고객님 동의 후 수리를 진행해요.',
      cta: '예약하기',
    },
    complete: {
      title: '예약이 완료되었어요!', description: `${REPAIR_COMPLETE_CHECK} 기사 배정 완료 시 알림으로 바로 알려드릴게요.`,
      descriptionNoAssignment: REPAIR_COMPLETE_CHECK, // 매칭에 실패했거나 취소돼 배정 알림이 오지 않는 예약의 머리글 설명 — Figma 설명의 첫 문장만 (위 REPAIR_COMPLETE_CHECK)
      rows: { date: '방문 일정', time: '방문 시간', address: '서비스 주소', manager: '기사 배정' }, badge: '신규', status: '배정 중',
      note: '기사 배정이 완료되면 알림으로 바로 알려드릴게요.', cta: '예약 확인',
    },
  },
};

// 입력 길이 상한 — 서버 @Size 와 같은 값 (backend CreateBookingRequest·Inquiry.MAX_LENGTH). 넘치면 마지막 '예약하기' 에서야 VALIDATION 을 보던 것을 입력란에서 막는다 (검증 검토)
export const INPUT_LIMITS = {
  roadAddress: 200,
  detailAddress: 200,
  request: 1000, // 청소 요청사항 · 수리 요청사항
  repairDetail: 1000, // 증상 상세
  inquiry: 2000,
};
// (코드에서 정함) 결제 기능 문구. 기존 Figma 화면의 문구는 바꾸지 않는다.
export const PAYMENT_COPY = {
  refunding: '예약이 취소되어 환불을 처리하고 있어요.', notPayable: '취소된 예약은 결제할 수 없어요.',
  methodsNote: '내 예약에서 방문 회차별로 결제할 수 있어요. 카드 정보는 결제사에서 처리하며 앱에 저장하지 않아요.',
  title: '결제', amount: '이번 방문 결제 금액', refund: '환불 금액', pay: '결제하기',
  checking: '결제 결과를 확인하고 있어요.', refresh: '결제 결과 다시 확인', retry: '다시 결제하기',
  reservations: '내 예약 보기', perVisit: '정기청소도 방문 회차별로 결제해요. 자동결제는 등록되지 않아요.',
  invalid: '결제 정보를 확인할 수 없어요. 내 예약에서 다시 열어주세요.',
  cancelled: '결제가 완료되지 않았어요. 다시 결제할 수 있어요.', failed: '결제창을 열지 못했어요. 다시 시도해주세요.',
  status: { pending: '결제를 진행해주세요.', authorized: '결제 결과를 확인하고 있어요. 창을 닫아도 서버에서 확인을 계속해요.', paid: '결제가 완료되었어요.', failed: '결제가 실패하거나 만료되었어요.', partially_refunded: '결제 금액의 일부가 환불되었어요.', refunded: '결제 금액이 환불되었어요.' },
};
