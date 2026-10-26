// Figma 화면에 보이는 값 그대로. 실서버가 생긴 뒤로 manager.js 는 이 파일 대신 서버(http.js)를 호출한다.
// 그래서 이 파일은 두 가지로 쓰인다.
// ① 가짜 서버(test/fakeHttp.js — 테스트와 DEV 의 VITE_API_MOCK=1)가 응답 값으로 쓴다 — 예: HOME·CALLS·JOB_DETAIL.
// ② 서버가 주지 않는 문구(코드에서 정함)는 화면·manager.js 가 직접 import 한다 — 예: *_COPY, INPUT_LIMITS.
// ① 의 값은 가짜 서버가 그대로 응답하므로 모양(필드 이름)을 서버 응답(backend/docs/api.md)과 글자까지 같게 둔다.

// Manager/Home — GET /manager/home 응답 모양 (backend HomeResponse)
export const HOME = {
  manager: { name: '김매니저', approved: true, suspension: null }, // suspension: 콜 배정 정지 안내 문구(서버) 또는 null
  today: { dateLabel: '9월 15일 (화)', count: 3 },
  inProgress: { title: '청소 진행 중', elapsed: '01:23:42', since: '14:02부터 진행 · 강남구 역삼동', jobId: 'job-1', status: 'inProgress', startedAt: null, pausedSeconds: 0 }, // startedAt 이 있으면 화면이 1초마다 계산 // status: inProgress | paused (서버, 일시정지 작업이 LIVE 로 보이지 않게)
  newCalls: { count: 2, expiry: '4:32 후 만료', summary: '가장 가까운 콜 · 9월 18일 오후 2:00 · 85,000원' },
  remaining: [
    { id: 'sch-1', jobId: 'job-1', time: '16:30 - 18:30', status: 'scheduled', customer: '박○○ 고객 · 해운대구 우동', service: '입주 청소' },
  ],
};

// 홈 빈 상태 견본. 지금 import 하는 곳은 없다 — DEV 빈 상태는 manager.js 가 ?scenario=empty 일 때 응답을 비워 만든다
export const HOME_EMPTY = {
  manager: { name: '김매니저', approved: false, suspension: null }, // 빈 홈 시나리오 = 승인 대기 카드도 함께 본다
  today: { dateLabel: '9월 15일 (화)', count: 0 },
  inProgress: null,
  newCalls: null,
  remaining: [],
};

// 콜 목록 (GET /manager/offers 모양). 가짜 서버는 각 콜에 만료 시각(expiresAt)을 더해 돌려준다
export const CALLS = {
  tabBadge: 2,
  calls: [
    { id: 'call-1', expiry: '4:32 후 만료', title: '9월 18일 목요일 오후 2:00', location: '서울 강남구 역삼동 · 아파트 기본청소', pay: '85,000원', details: ['예상 소요 3시간', '아파트·빌라 · 25평 · 방 3 · 화장실 2', '청소 범위: 설거지, 세탁'], note: '특이사항: 반려묘 1마리' },
    { id: 'call-2', expiry: '4:32 후 만료', title: '9월 19일 금요일 오전 10:00', location: '부산 해운대구 우동 · 가전청소', pay: '62,000원', details: ['예상 소요 2시간', '원룸·오피스텔 · 10평'], note: '특이사항: 반려묘 1마리' },
  ],
  availability: { status: '콜 수신 중', detail: '오늘 09:00 - 18:00 근무 설정됨' },
};

export const CALLS_EMPTY = { tabBadge: 0, calls: [], availability: CALLS.availability }; // 지금 import 하는 곳은 없다 (빈 상태는 ?scenario=empty)

// Manager/Call/Detail — CallItem + 고객 요청사항
export const CALL_DETAIL = {
  ...CALLS.calls[0],
  customerRequest: '반려묘 1마리 있어요. 거실 캣타워는 건드리지 말아주세요. 현관 비밀번호는 방문 30분 전 문자로 안내드립니다.',
};

// Manager/Call/RejectSheet — 거절 사유 6개 (Figma 문구 그대로)
export const REJECT_REASONS = ['거리가 너무 멀어요', '시간이 맞지 않아요', '업무 내용이 맞지 않아요', '예상 금액이 맞지 않아요', '개인 사정', '기타']; // 앱은 GET /catalog/reasons?kind=reject 로 받는다

// Manager/Job/Detail
export const JOB_DETAIL = {
  id: 'job-1',
  status: 'scheduled',
  startLabel: '10:00 시작',
  today: true, // 작업 날짜가 오늘인가 — 아니면 startLabel 에 날짜가 붙고 '현장 도착' 대신 안내 (직접 사용 점검 U11)
  address: ['서울 강남구 역삼동', '○○아파트 101동 1203호'],
  info: [
    { label: '고객', value: '김○○' },
    { label: '청소 유형', value: '아파트 기본청소' },
    { label: '예상 소요', value: '3시간' },
  ],
  pay: '85,000원',
  scope: ['거실', '주방', '욕실 2', '침실 3'],
  requests: ['반려묘 있음', '안방 서랍장은 청소 제외', '음식물 쓰레기 처리 요청'],
};

// Manager/Job/Preparing — 촬영 장비 상태
export const EQUIPMENT_OK = [
  { label: '카메라', value: '연결됨', status: 'ok' },
  { label: '배터리', value: '82%', status: 'ok' },
  { label: '네트워크', value: '양호', status: 'ok' },
  { label: '실시간 전송', value: '준비 완료', status: 'ok' },
];

// Manager/Job/Preparing/Error — 카메라 연결 실패 상태
export const EQUIPMENT_ERROR = [
  { label: '카메라', value: '연결 안 됨', status: 'issue' },
  { label: '배터리', value: '82%', status: 'ok' },
  { label: '네트워크', value: '양호', status: 'ok' },
  { label: '실시간 전송', value: '대기 중', status: 'issue' },
];

// Manager/Job/InProgress
export const ACTIVE_JOB = {
  id: 'job-1',
  status: 'inProgress', // 작업 상태 — 경과 시간은 이걸로 멈춘다 (자체 점검 #43)
  title: '청소 진행 중',
  elapsed: '01:23:42',
  since: '14:02부터 진행 · 실시간 촬영 중',
  sinceLabel: '14:02부터 진행',
  streaming: true,
  equipment: [
    { label: '카메라', value: '정상', status: 'ok' },
    { label: '배터리', value: '74%', status: 'ok' },
    { label: '네트워크', value: '양호', status: 'ok' },
  ],
  requests: ['반려묘 있음', '안방 서랍장은 청소 제외', '음식물 쓰레기 처리 요청'],
  checklist: [
    { group: '거실', items: [{ id: 'c1', label: '바닥 청소', done: true }, { id: 'c2', label: '먼지 제거', done: true }, { id: 'c3', label: '테이블 정리', done: true }] },
    { group: '주방', items: [{ id: 'c4', label: '싱크대', done: true }, { id: 'c5', label: '가스레인지', done: false }, { id: 'c6', label: '조리대', done: false }] },
    { group: '욕실', items: [{ id: 'c7', label: '세면대', done: false }, { id: 'c8', label: '변기', done: false }, { id: 'c9', label: '바닥', done: false }] },
  ],
};

// Manager/Job/PauseSheet — 중단 사유 6개 (Figma 문구 그대로)
export const PAUSE_REASONS = ['고객 요청', '화장실 이용', '개인적인 상황', '네트워크 문제', '카메라 문제', '기타']; // 앱은 GET /catalog/reasons?kind=pause 로 받는다

// Manager/Job/Paused
export const PAUSED_JOB = { jobId: 'job-1', since: '14:32부터 중단됨', note: '촬영이 중단된 동안에는 고객 화면과 녹화 영상에 아무것도 나오지 않아요. 정산 금액은 그대로예요.' }; // V033 과 같게 — Figma 문구는 정산 방식과 달라 사용자 결정으로 고침 (재감사 2차 #7)

// Manager/Job/Complete
export const COMPLETED_JOB = {
  title: '청소가 완료되었습니다',
  subtitle: '강남구 역삼동 · 아파트 기본청소',
  summary: [
    { label: '청소 시간', value: '14:02 - 17:25', tone: 'default' },
    { label: '총 소요', value: '3시간 23분', tone: 'default' },
    { label: '체크리스트', value: '9개 중 9개 완료', tone: 'success' },
    { label: '촬영 기록', value: '정상 업로드', tone: 'success' },
  ],
  payout: { label: '예상 정산', amount: '120,000원', note: '정산은 다음 정산일에 지급됩니다.' },
};

// Manager/Settlement
export const SETTLEMENT = {
  monthly: { label: '이번 달 예상 정산', amount: '1,240,000원' },
  stats: [
    { label: '완료 업무', value: '18건', tone: 'default' },
    { label: '정산 예정', value: '320,000원', tone: 'default' },
    { label: '정산 완료', value: '920,000원', tone: 'success' },
  ],
  items: [
    { id: 'st-1', date: '9월 14일', status: 'complete', title: '강남구 기본청소', amount: '120,000원' },
    { id: 'st-2', date: '9월 12일', status: 'complete', title: '해운대구 입주청소', amount: '185,000원' },
    { id: 'st-3', date: '9월 10일', status: 'complete', title: '수영구 가전청소', amount: '96,000원' },
  ],
};

// Manager/Settlement/Detail
export const SETTLEMENT_DETAIL = {
  id: 'st-1',
  title: '9월 14일 · 강남구 기본청소',
  subtitle: '10:00 - 13:00 · 김○○ 고객',
  breakdown: [
    { label: '서비스 금액', value: '150,000원', tone: 'default' },
    { label: '플랫폼 수수료', value: '-15,000원', tone: 'secondary' },
    { label: '기타', value: '-15,000원', tone: 'secondary' },
  ],
  final: { label: '최종 정산', value: '120,000원' },
};

// Manager/Schedule (오늘)
export const SCHEDULE = {
  items: [
    { id: 'sch-1', time: '10:00 - 13:00', status: 'scheduled', customer: '김○○ 고객 · 강남구 역삼동', service: '아파트 기본청소' },
    { id: 'sch-2', time: '14:30 - 16:00', status: 'scheduled', customer: '이○○ 고객 · 강남구 삼성동', service: '가전청소' },
    { id: 'sch-3', time: '16:30 - 18:30', status: 'scheduled', customer: '박○○ 고객 · 해운대구 우동', service: '입주 청소' },
  ],
};
export const SCHEDULE_EMPTY = { items: [] }; // 지금 import 하는 곳은 없다 (빈 상태는 ?scenario=empty)

// Manager/Schedule/Availability
export const AVAILABILITY = {
  defaultHours: [
    { day: '월', hours: '09:00 - 18:00', start: '09:00', end: '18:00' },
    { day: '화', hours: '09:00 - 18:00', start: '09:00', end: '18:00' },
    { day: '수', hours: '09:00 - 18:00', start: '09:00', end: '18:00' },
    { day: '목', hours: '09:00 - 18:00', start: '09:00', end: '18:00' },
    { day: '금', hours: '09:00 - 18:00', start: '09:00', end: '18:00' },
    { day: '토', hours: '휴무', off: true, start: null, end: null },
    { day: '일', hours: '휴무', off: true, start: null, end: null },
  ],
  unavailable: [
    { id: 'un-1', date: '9월 18일', label: '개인 일정', time: '14:00 - 18:00' },
    { id: 'un-2', date: '9월 23일', label: '휴무', time: '하루 종일' },
  ],
};

/** 미승인 매니저 홈 카드 (코드에서 정함 — 가입 완료 화면 문구 재사용). approved 는 GET /manager/home·/offers 가 준다 */
export const APPROVAL_PENDING_COPY = { title: '승인 대기 중이에요', description: '신원 확인과 기본 교육 이수가 끝나면\n청소 요청을 받을 수 있어요.' };
export const SUSPENDED_COPY = { title: '콜 배정 정지 중이에요' }; // (코드에서 정함) 설명은 서버 suspension 문구 — 콜 화면의 '콜 배정 정지 중' 과 같은 말 (자체 점검 #11)
/** 서버 repeat 코드 → 화면 문구 (근무 불가 카드의 '매주 반복' 표시, 검증 검토). Availability.jsx 는 모르는 코드면 그대로 보인다 */
export const REPEAT_LABELS = { weekly: '매주 반복', monthly: '매월 반복' };
// Manager/Schedule/AddUnavailable — 반복 옵션 (Figma 문구 그대로)
// manager.js getRepeatOptions 가 돌려주고, 저장할 때 manager.js REPEAT_CODES 로 서버 값(none·weekly·monthly)으로 바꾼다 — 문구를 바꾸면 그 키도 같이
export const REPEAT_OPTIONS = ['반복 없음', '매주', '매월'];

// Manager/Profile
export const PROFILE = {
  name: '김○○ 매니저',
  stats: [
    { label: '평점', value: '4.9' },
    { label: '완료 청소', value: '142건' },
  ],
  // 메뉴 순서·문구는 Figma 그대로. to 가 있는 것만 프로토타입 연결이 있음
  menu: [
    { key: 'info', label: '매니저 정보' },
    { key: 'area', label: '활동 지역' },
    { key: 'availability', label: '근무 일정 관리', to: '/schedule/availability' },
    { key: 'identity', label: '신원 인증' },
    { key: 'education', label: '교육 이수' },
    { key: 'account', label: '정산 계좌' },
    { key: 'equipment', label: '촬영 장비', to: '/profile/equipment' },
    { key: 'notifications', label: '알림 설정' },
    { key: 'notice', label: '공지사항' },
    { key: 'support', label: '고객센터' },
  ],
};

// Manager/Profile/Equipment
export const EQUIPMENT_DEVICE = {
  name: '안심캠 #AC-00231',
  connection: '정상 연결',
  status: [
    { label: '배터리', value: '82%', status: 'ok' },
    { label: '네트워크', value: 'LTE / 양호', status: 'ok' },
    { label: '카메라', value: '정상', status: 'ok' },
    { label: '실시간 전송', value: '정상', status: 'ok' },
  ],
};

// ── Figma 에 없는 화면·동작용 (코드에서 정함) ──
/** Manager/Job/Detail › 고객 연락·지도 보기. 전화번호는 실서버에서; 010-0000-0000 은 부여되지 않는 번호(mock) */
export const JOB_CONTACT = { phone: '010-0000-0000', mapQuery: '서울 강남구 역삼동 ○○아파트' };

/** Manager/Schedule › "7일"(오늘~+6일) · "달력" 탭 (Figma 는 오늘만). dateKey 는 'YYYY-MM-DD' */
export const SCHEDULE_WEEK = [
  { dateKey: '2026-09-15', label: '9월 15일 (화)', items: SCHEDULE.items },
  { dateKey: '2026-09-17', label: '9월 17일 (목)', items: [{ id: 'sch-4', time: '13:00 - 16:00', status: 'scheduled', customer: '최○○ 고객 · 수영구 광안동', service: '사무실 청소' }] },
  { dateKey: '2026-09-19', label: '9월 19일 (토)', items: [{ id: 'sch-5', time: '10:00 - 12:00', status: 'scheduled', customer: '정○○ 고객 · 동래구 사직동', service: '가전청소' }] },
];

/** Manager/Profile 메뉴 중 연결 화면이 없던 4개 — 읽기 전용 정보 화면 */
export const PROFILE_DETAILS = {
  info: { title: '매니저 정보', rows: [{ label: '이름', value: '김○○' }, { label: '휴대폰', value: '010-0000-0000' }, { label: '활동 시작', value: '2024년 3월' }, { label: '완료 청소', value: '142건' }], note: '정보 수정은 고객센터로 문의해주세요.' },
  area: { title: '활동 지역', chips: ['강남구', '서초구', '송파구', '해운대구'], note: '활동 지역 변경은 고객센터로 문의해주세요.' },
  identity: { title: '신원 인증', rows: [{ label: '본인 인증', value: '완료' }, { label: '범죄경력 조회', value: '완료 · 2026.03' }, { label: '보험 가입', value: '가입 · 2027.02 만료' }] },
  education: { title: '교육 이수', rows: [{ label: '기본 청소 교육', value: '이수 · 2024.03' }, { label: '실시간 촬영 가이드', value: '이수 · 2026.08' }, { label: '고객 응대 교육', value: '미이수' }] },
  account: { title: '정산 계좌', rows: [{ label: '은행', value: '○○은행' }, { label: '계좌번호', value: '****-**-1234' }, { label: '예금주', value: '김○○' }], note: '계좌 변경은 고객센터로 문의해주세요.' },
};
// 알림 설정: 토글 기본값과 행 목록 — 가짜 서버가 GET /manager/notifications 의 values·rows 로 돌려준다
export const MANAGER_NOTIFICATION_SETTINGS = { push: true, call: true, settlement: true };
export const MANAGER_NOTIFICATION_ROWS = [
  { key: 'push', label: '푸시 알림' },
  { key: 'call', label: '새 청소 요청 알림' },
  { key: 'settlement', label: '정산 완료 알림' },
  { key: 'marketing', label: '마케팅 정보 수신' }, // V018 — 매니저 마케팅 약관이 '알림 설정에서 해제' 를 약속한다
];
// 공지사항 목록·본문 — 가짜 서버의 GET /support/notices?audience=manager
export const MANAGER_NOTICES = [
  { id: 'mn1', title: '추석 연휴 정산 일정 안내', date: '2026.09.10', body: '연휴 기간(9월 24일~27일) 정산은 9월 28일에 일괄 처리됩니다.' },
  { id: 'mn2', title: '실시간 촬영 장비 점검 안내', date: '2026.09.01', body: '9월 5일까지 촬영 장비 배터리와 네트워크 상태를 앱에서 확인해주세요.' },
];
// 고객센터 문의 화면 문구 — manager.js getSupportCopy 가 돌려준다
export const SUPPORT_COPY = { title: '고객센터', description: '업무 중 어려움이나 문의 사항을 남겨주세요. 영업일 기준 1일 안에 답변드려요.', placeholder: '문의 내용을 입력해주세요', done: '문의가 접수되었어요' };
/** 현장에서 장비가 죽으면 '1일 안에 답변' 은 소용이 없다 → 바로 전화할 수 있게 (사용성 검토 2026-09-22). 번호는 서버 설정(support_phone) */
export const URGENT_SUPPORT_COPY = { title: '지금 바로 도움이 필요하면', call: '고객센터 전화하기', note: '방문 시간이 임박했다면 전화로 알려주세요. 고객에게도 안내해 드려요.' };
/** 매니저 방문 취소·패널티 (backend/docs/manager-policy.md — 사용자 승인 2026-09-22. Figma 에 없어 코드에서 정함) */
export const JOB_CANCEL_COPY = {
  button: '방문 취소',
  title: '이 방문을 취소할까요?',
  description: '고객에게 바로 알리고 다른 매니저를 찾아요. 취소는 기록에 남고, 방문이 가까울수록 패널티 점수가 커져요.',
  reasonLabel: '취소 사유',
  detailLabel: '상세 내용 (선택)',
  detailPlaceholder: '고객센터가 확인할 내용을 적어주세요',
  confirm: '방문 취소하기',
  back: '돌아가기',
  done: '방문이 취소됐어요',
  failed: '취소하지 못했어요. 잠시 후 다시 시도해주세요.',
  /** 방문일 전: 서버가 시작을 당일에만 받는데 '현장 도착' 이 보여 준비 화면까지 갔다가 막혔다 (코드에서 정함, 직접 사용 점검 U11) */
  notToday: '현장 도착은 방문 당일에 할 수 있어요.',
};

/** 패널티 내역 화면(Penalties.jsx) 문구. guide 는 backend/docs/manager-policy.md 점수 규칙의 요약이다 */
export const PENALTY_COPY = {
  title: '패널티 내역',
  pointsLabel: '누적 점수',
  empty: '패널티 내역이 없어요',
  emptyDescription: '약속한 방문을 지키면 점수가 쌓이지 않아요.',
  guide: '방문 24시간 전까지 취소는 월 2회까지 점수가 없어요. 그 뒤 취소는 1점, 미방문은 3점이에요. 12개월이 지나면 사라지고, 3개월 동안 아무 일이 없으면 1점이 줄어요.',
};

export const HOURS_EDIT_COPY = { title: '기본 근무시간 수정', off: '휴무' }; // 지금 import 하는 곳은 없다 — Availability.jsx 는 같은 제목을 직접 쓴다
/** Job/InProgress 상단 카드의 라이브 상태 문구 (B5 §9.2-8 — 코드에서 정함). 키 = liveClient status */
export const LIVE_STATE_COPY = {
  connecting: '카메라 연결 대기 중',
  live: '실시간 촬영 중',
  paused: '일시정지',
  ended: '촬영 종료',
  error: '라이브 연결에 문제가 있어요. 잠시 후 다시 시도해주세요.',
  // 여기부터는 status 값이 아니다 — JobInProgress 가 상황에 맞춰 이름으로 고른다
  cameraBusy: '바디캠을 껐다가 다시 켜주세요',
  /** 일시정지 ①(영상 차단) 실패 — 영상이 아직 나간다 */
  pauseVideoFailed: '촬영을 중단하지 못했어요. 영상이 계속 전송되고 있으니 다시 시도해주세요.',
  // 이 상태(영상만 막힘, 작업은 진행 중)는 서버가 매니저의 재개 전까지 막아 둔다 — 바디캠이 끊겼다 다시 붙어도(live_sessions.video_held).
  // 그래서 시트를 닫으면 JobInProgress 가 live/resume 을 불러 촬영을 다시 시작한다 (문구의 '이 창을 닫으면 …')
  /** ① 은 됐고 ②(작업 상태) 만 실패 — 영상은 이미 막혀 있다 */
  pauseJobFailed: '영상은 중단됐지만 작업 상태를 바꾸지 못했어요. 다시 시도해주세요. 이 창을 닫으면 촬영을 다시 시작해요.', // 닫으면 영상 재개 (코드에서 정함, 자체 점검 #15)
  checklistFailed: '체크 상태를 저장하지 못했어요. 다시 시도해주세요.',
  /** 휴대폰 송출: 연결이 잠깐 끊겨 LiveKit 이 다시 붙는 중 (코드에서 정함, 2026-10-10 §8.0) */
  reconnecting: '연결이 잠시 끊겼어요. 다시 연결하는 중이에요.',
};

/**
 * 휴대폰 카메라 송출(§8.0, 2026-10-10)에서 카메라를 켜지 못한 이유별 안내 — Job/Preparing 의 '카메라 연결 실패' 배너 본문.
 * Figma 의 배너 본문은 바디캠(안심캠·블루투스) 문구라 휴대폰에 맞지 않아 코드에서 정함 (PROGRESS 판단 항목). 키 = liveClient cameraError
 */
export const PHONE_CAMERA_COPY = {
  denied: '카메라 권한이 꺼져 있어요. 휴대폰 설정에서 안심클린 매니저의 카메라 권한을 켠 뒤 카메라 다시 연결을 눌러주세요.',
  notFound: '이 휴대폰에서 사용할 수 있는 카메라를 찾지 못했어요.',
  busy: '다른 앱이 카메라를 쓰고 있어요. 그 앱을 닫고 카메라 다시 연결을 눌러주세요.',
  failed: '휴대폰 카메라를 켜지 못했어요. 카메라 다시 연결을 눌러주세요.',
};
/**
 * 청소 전·후 사진 (2026-10-23 사용자 요청 — Figma 에 없어 코드에서 정함, PROGRESS "Figma 에 없어 코드에서 정한 화면").
 * 쓰는 곳: JobInProgress('청소 전 사진' 묶음), JobPhotoCapture(촬영 화면), JobAfterPhotos(청소 완료 직전 '청소 후 사진' 화면)
 */
export const PHOTO_COPY = {
  // 청소 진행 화면의 묶음
  sectionTitle: '청소 전 사진',
  sectionHint: '청소를 시작하기 전에 위치마다 한 장씩 찍어주세요. 청소를 마치면 같은 위치를 다시 찍어요.',
  empty: '아직 찍은 사진이 없어요.',
  takeBefore: '청소 전 사진 찍기',
  beforeDescription: '청소 전 사진',
  remove: '삭제',
  removeFailed: '사진을 지우지 못했어요. 다시 시도해주세요.',
  loadFailed: '사진 목록을 불러오지 못했어요.',
  // 촬영 화면
  beforeTitle: '청소 전 사진',
  afterTitle: '청소 후 사진',
  locationLabel: '촬영 위치',
  locationPlaceholder: '예: 거실 소파 앞',
  locationRequired: '촬영 위치를 1~20자로 적어주세요.',
  beforeGuide: '위치를 적고 청소 전 모습을 찍어주세요.',
  afterGuide: '반투명하게 겹친 청소 전 사진에 맞춰 같은 각도로 찍어주세요.',
  shoot: '촬영하기',
  retake: '다시 찍기',
  save: '저장',
  cameraNotReady: '카메라가 아직 준비되지 않았어요. 잠시 후 다시 찍어주세요.',
  saveFailed: '사진을 저장하지 못했어요. 다시 시도해주세요.',
  notFound: '찾을 수 없는 사진이에요. 목록에서 다시 골라주세요.',
  // 청소 후 사진 화면 (청소 완료 직전)
  afterListGuide: '청소 전에 찍은 곳을 같은 각도로 다시 찍어주세요. 모두 찍으면 청소를 완료할 수 있어요.',
  afterNeeded: '촬영 필요',
  afterDone: '촬영 완료 · 다시 찍기',
  afterAction: '청소 후 사진 찍기',
  afterRemaining: '곳이 남았어요', // 앞에 남은 수가 붙는다: '2곳이 남았어요'
  complete: '청소 완료',
};
/** Profile/Equipment 바디캠 설정 정보 (B5 §9.2-4 — 코드에서 정함) */
export const EQUIPMENT_SETUP_COPY = {
  title: '바디캠 설정',
  url: 'RTMP 주소',
  key: '스트림 키',
  lastOnline: '마지막 연결',
  none: '없음',
  guide: '바디캠에 위 주소와 키를 한 번만 입력하세요. 전체 키는 운영자에게 요청하세요.',
  missingTitle: '등록된 바디캠이 없어요',
  missingDescription: '운영자가 바디캠을 등록하면 여기에 설정 정보가 보여요.',
};
/** 콜 수락이 늦었을 때 (BACKEND_PLAN.md §9.2-2 — 코드에서 정함). 확인하면 목록을 새로 받는다 */
export const CALL_CLOSED_COPY = { title: '이미 마감된 요청이에요', description: '요청이 만료됐거나 다른 매니저가 먼저 수락했어요.', confirm: '확인' };
/** 서버 토글은 ON 인데 기기(OS) 알림 권한이 꺼져 있을 때 (코드에서 정함) — 매니저는 콜을 놓친다 */
export const PUSH_OS_DENIED_NOTE = '기기 설정에서 이 앱의 알림이 꺼져 있어요. 켜야 콜 알림을 받을 수 있어요.';
export const NOTIFICATION_TOGGLE_FAILED = '알림 설정을 저장하지 못했어요. 잠시 후 다시 시도해주세요.'; // 알림 토글 저장 실패 (Notifications.jsx)
// 화면이 ActionError 에 넘긴다. 서버 문구가 없을 때의 대체(fallback)다 — reject 도 서버 문구('이미 수락한 콜이에요 …')가 있으면 그것을 보인다 (문제 목록 10번).
// 예: accept·reject = 콜 목록·콜 상세, save = 근무 일정(Availability)
/** 버튼 동작 실패 문구 (코드에서 정함) */
export const ACTION_FAILED_COPY = { accept: '수락하지 못했어요. 다시 시도해주세요.', reject: '거절하지 못했어요. 다시 시도해주세요.', start: '청소를 시작하지 못했어요. 다시 시도해주세요.', save: '저장하지 못했어요. 다시 시도해주세요.' };

// ── 로그인·회원가입 (Figma 매니저 페이지에 화면 없음 → 코드에서 정함. 디자인이 오면 문구·순서 교체) ──
/** 화면 문구. 고객용 Auth/* 의 어투를 따랐다 */
export const AUTH_COPY = {
  login: {
    title: '매니저 로그인',
    description: '등록된 휴대폰 번호와 비밀번호로\n로그인해주세요.',
    phoneLabel: '휴대폰 번호',
    passwordLabel: '비밀번호',
    passwordPlaceholder: '비밀번호를 입력해주세요',
    submit: '로그인하기',
    signUpPrompt: '아직 매니저 계정이 없으신가요?',
    signUpLink: '회원가입',
    failed: '휴대폰 번호 또는 비밀번호를 확인해주세요.',
  },
  terms: { title: '매니저 활동을 위한\n약관에 동의해주세요', agreeAll: '전체 동의하기', next: '다음' },
  info: {
    title: '매니저 정보를\n입력해주세요',
    nameLabel: '이름',
    namePlaceholder: '이름을 입력해주세요',
    phoneLabel: '휴대폰 번호',
    passwordLabel: '비밀번호',
    passwordPlaceholder: '8자 이상 입력해주세요',
    passwordConfirmLabel: '비밀번호 확인',
    passwordConfirmPlaceholder: '비밀번호를 한 번 더 입력해주세요',
    passwordMismatch: '비밀번호가 일치하지 않아요',
    next: '다음',
  },
  reset: {
    title: '비밀번호를\n다시 설정해주세요',
    description: '가입한 휴대폰 번호로 인증하면\n새 비밀번호를 정할 수 있어요.',
    phoneLabel: '휴대폰 번호',
    codeLabel: '인증번호',
    codePlaceholder: '문자로 받은 6자리',
    passwordLabel: '새 비밀번호',
    passwordPlaceholder: '8자 이상 입력해주세요',
    sent: '인증번호를 보냈어요',
    notFound: '가입되지 않은 번호예요',
    signUpPrompt: '아직 매니저 계정이 없으신가요?',
    signUpLink: '회원가입',
    submit: '비밀번호 변경하기',
    failed: '비밀번호를 바꾸지 못했어요. 잠시 후 다시 시도해주세요.',
  },
  complete: {
    title: '가입이 완료되었어요',
    description: '신원 확인과 기본 교육 이수가 끝나면\n청소 요청을 받을 수 있어요.',
    cta: '시작하기',
    error: '가입을 마치지 못했어요. 잠시 후 다시 시도해주세요.',
  },
};

// 약관 목록·본문·안내 문구 (아래 세 값). 실제 앱은 서버(GET /terms?audience=manager)에서 받고,
// 이 값은 테스트용 가짜 서버가 같은 모양으로 돌려준다
/** 매니저 약관. 고객용 약관 3개 + 실시간 촬영 동의(매니저에게만 필요) */
export const MANAGER_TERMS = [
  { id: 'service', label: '(필수) 매니저 서비스 이용약관', required: true },
  { id: 'privacy', label: '(필수) 개인정보 수집 및 이용 동의', required: true },
  { id: 'location', label: '(필수) 위치기반 서비스 이용 동의', required: true },
  { id: 'recording', label: '(필수) 실시간 촬영 및 영상 녹화 동의', required: true },
  { id: 'marketing', label: '(선택) 마케팅 정보 수신 동의', required: false },
];
export const MANAGER_TERM_BODIES = {
  service: '안심클린 매니저 서비스 이용약관\n\n제1조 (목적)\n이 약관은 안심클린이 제공하는 청소 매니저 서비스의 이용 조건과 절차를 정합니다.\n\n제2조 (매니저의 의무)\n매니저는 배정된 청소를 성실히 수행하고, 고객의 재산과 개인정보를 보호해야 합니다.',
  privacy: '개인정보 수집 및 이용 동의\n\n수집 항목: 이름, 휴대폰 번호, 활동 지역, 정산 계좌\n이용 목적: 청소 매칭, 정산, 신원 확인\n보유 기간: 매니저 탈퇴 시까지 (1:1 문의 기록은 분쟁 처리를 위해 탈퇴 후 3년 보관)', // V039 (사용자 결정 2026-09-27, 코드에서 정함)
  location: '위치기반 서비스 이용 동의\n\n청소 요청 매칭과 방문 확인을 위해 매니저의 현재 위치를 이용합니다. 위치 정보는 업무 시간에만 수집됩니다.',
  recording: '실시간 촬영 및 영상 녹화 동의\n\n청소 진행 중 촬영 장비로 현장을 실시간 송출하고 서버에 녹화합니다. 영상은 고객 확인과 분쟁 해결 목적으로만 사용되며, 정해진 기간 후 삭제됩니다.',
  marketing: '마케팅 정보 수신 동의\n\n새 청소 요청 지역, 프로모션, 교육 안내를 앱 알림과 문자로 받습니다. 언제든지 알림 설정에서 해제할 수 있습니다.',
};
export const MANAGER_PRIVACY_NOTE = '수집된 개인정보는 안전하게 보호되며,\n매니저 활동 목적 외로 사용되지 않습니다.';
/** 비밀번호 규칙은 코드에서 정함: 8자 이상 */
export const MANAGER_PASSWORD_OK_MESSAGE = '사용가능한 비밀번호입니다!'; // manager.js checkPassword 가 8자 이상일 때 돌려준다

// 입력 길이 상한 — 서버 @Size 와 같은 값 (backend CreateBookingRequest·Inquiry.MAX_LENGTH·ManagerCancelRequest). 넘치면 마지막 '예약하기' 에서야 VALIDATION 을 보던 것을 입력란에서 막는다 (검증 검토)
// 서버 값을 바꾸면 여기도 같이 바꾼다 — 입력란이 서버보다 많이 받으면 확인을 눌러도 400(VALIDATION)으로 거절되고, 문구로는 어느 칸이 몇 자까지인지 알 수 없다
export const INPUT_LIMITS = { // 매니저 앱 화면이 쓰는 값은 inquiry(고객센터 문의)와 jobCancelDetail(방문 취소 상세)이다
  roadAddress: 200,
  detailAddress: 200,
  request: 1000, // 청소 요청사항 · 수리 요청사항
  repairDetail: 1000, // 증상 상세
  inquiry: 2000, // 고객센터 문의 (서버 Inquiry.MAX_LENGTH)
  jobCancelDetail: 500, // 방문 취소 상세 (서버 ManagerCancelRequest.detail @Size(max = 500)) — 문의(2000)와 다르다 (문제 목록 1번)
};
