// 앱 mock.js(Figma 문구의 원본) → 백엔드 seed SQL + 테스트 JSON.
//   node scripts/backend-mock-export.mjs shapes   테스트 리소스 backend/src/test/resources/mock-shapes/*.json 을 다시 만든다 (언제든 실행)
//   node scripts/backend-mock-export.mjs seed     V002~V005 seed SQL 을 만든다 — **B1 에서 한 번만**. 커밋된 마이그레이션은 바꾸지 않는다.
//                                                  이후 문구를 바꾸려면 새 버전(V00N__update_*.sql)을 손으로 쓴다
// 서버 통합 테스트 CatalogIT 가 mock-shapes/*.json 과 카탈로그·약관 API 응답이 같은지 비교한다.
// CI(.github/workflows/ci.yml)는 shapes 를 다시 만든 뒤 git diff 로 바뀐 게 없는지 본다 —
// mock.js 를 고치고 shapes 를 다시 만들어 커밋하지 않으면 CI 가 실패한다.
import { mkdirSync, writeFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import * as customer from '../apps/customer/src/api/mock.js';
import * as manager from '../apps/manager/src/api/mock.js';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'); // 저장소 루트 (이 파일 폴더의 한 단계 위)
const mode = process.argv[2]; // 명령줄 첫 인자: shapes 또는 seed
if (!['shapes', 'seed'].includes(mode)) {
  console.error('사용법: node scripts/backend-mock-export.mjs shapes|seed');
  process.exit(1);
}

// ── SQL 도우미 ──
// str: 값을 Postgres 문자열 리터럴 E'…' 로. E 문자열이라 역슬래시·줄바꿈을 이스케이프로 살리고, 작은따옴표는 두 번 써서 넣는다
// jsonb: 값을 JSON 문자열로 바꿔 '…'::jsonb 리터럴로. rows: insert 문 하나로 여러 행을 넣는 SQL 을 만든다
const str = (s) => "E'" + String(s).replace(/\\/g, '\\\\').replace(/'/g, "''").replace(/\n/g, '\\n') + "'";
const jsonb = (v) => "'" + JSON.stringify(v).replace(/'/g, "''") + "'::jsonb";
const rows = (table, cols, values) => `insert into ${table} (${cols.join(', ')}) values\n${values.map((v) => '  (' + v.join(', ') + ')').join(',\n')};\n`;

const CATEGORY_ORDER = ['home', 'office', 'appliance', 'move-in']; // 카테고리 순서 — seed 의 sort 값과 shapes 파일을 만드는 순서
// 결정 §13-3: 임시 가격 (정기 / 1회). 운영 전 새 마이그레이션으로 교체
const PRICES = { home: [80000, 100000], office: [120000, 150000], appliance: [60000, 70000], 'move-in': [150000, 180000] };
// 매니저 약관 라벨 앞의 '(필수) '·'(선택) ' 을 떼어 약관 제목으로 쓴다
const stripPrefix = (label) => label.replace(/^\((필수|선택)\)\s*/, '');

// ── seed: V002~V005 마이그레이션 파일을 새로 쓴다 ──
if (mode === 'seed') {
  const dir = path.join(root, 'backend/src/main/resources/db/migration');
  // 같은 이름 파일이 이미 있으면 쓰지 않고 멈춘다 — 적용된 마이그레이션을 덮어쓰면 Flyway 체크섬 검증이 깨진다 (CODE_GUIDE.md 3-10)
  const guard = (file) => {
    const p = path.join(dir, file);
    if (existsSync(p)) {
      console.error(`${file} 이 이미 있어요. 커밋된 마이그레이션은 바꾸지 않습니다 — 새 버전을 손으로 쓰세요.`);
      process.exit(1);
    }
    return p;
  };

  // V002 카탈로그
  let sql = '-- V002__seed_catalog.sql — apps/*/src/api/mock.js 에서 scripts/backend-mock-export.mjs seed 로 생성 (Figma 문구 그대로)\n';
  sql += rows('service_categories', ['key', 'label', 'promo', 'promo_text', 'sort'], CATEGORY_ORDER.map((k, i) => {
    const c = customer.BOOKING_CATEGORIES[k];
    return [str(k), str(c.label), str(c.promo), c.promoText ? str(c.promoText) : 'null', i + 1]; // promoText 가 없으면 SQL null
  }));
  sql += '\n-- 결정 §13-3: 임시 가격표 (원). 운영 전 새 마이그레이션으로 교체\n';
  sql += rows('catalog_prices', ['category_key', 'frequency', 'base_amount'], CATEGORY_ORDER.flatMap((k) => [[str(k), "'recurring'", PRICES[k][0]], [str(k), "'oneTime'", PRICES[k][1]]]));
  sql += '\n' + rows('catalog_options', ['category_key', 'key', 'kind', 'label', 'price', 'icon', 'sort'], CATEGORY_ORDER.flatMap((k) => {
    const o = customer.BOOKING_OPTIONS[k];
    let sort = 0; // 카테고리 안에서 무료 → 유료 순으로 1부터 매긴다
    const price = (p) => Number(String(p ?? '0').replace(/[^0-9]/g, '')); // '+10,000원' 같은 표시 문자열에서 숫자만 남긴다 → 10000
    return [
      ...o.free.map((x) => [str(k), str(x.key), "'free'", str(x.label), 0, x.icon, ++sort]),
      ...o.paid.map((x) => [str(k), str(x.key), "'paid'", str(x.label), price(x.price), x.icon, ++sort]),
    ];
  }));
  sql += '\n' + rows('catalog_notice_chips', ['category_key', 'key', 'label', 'icon', 'sort'], CATEGORY_ORDER.flatMap((k) =>
    customer.NOTICE_CHIPS[k].map((c, i) => [str(k), str(c.key), str(c.label), str(c.icon), i + 1])));
  sql += '\n' + rows('catalog_frequency_copy', ['category_key', 'frequency', 'description'], CATEGORY_ORDER.flatMap((k) =>
    Object.entries(customer.FREQUENCY_DESCRIPTIONS[k]).map(([f, d]) => [str(k), str(f), str(d)])));
  sql += "\n-- 체크리스트 템플릿: Figma Manager/Job/InProgress 의 항목(가정 청소). 다른 카테고리 템플릿은 없음 → 배정 시 home 을 대신 쓴다 (판단 항목)\n";
  // sort = 그룹 순서 × 10 + 그룹 안 순서 + 1 — 그룹을 10 단위로 띄운다 (한 그룹 항목이 10개를 넘으면 다음 그룹과 겹친다)
  sql += rows('checklist_templates', ['category_key', 'group_name', 'label', 'sort'], manager.ACTIVE_JOB.checklist.flatMap((g, gi) =>
    g.items.map((it, ii) => ["'home'", str(g.group), str(it.label), gi * 10 + ii + 1])));
  sql += '\n' + rows('catalog_reasons', ['kind', 'label', 'sort'], [
    ...manager.REJECT_REASONS.map((r, i) => ["'reject'", str(r), i + 1]),
    ...manager.PAUSE_REASONS.map((r, i) => ["'pause'", str(r), i + 1]),
  ]);
  writeFileSync(guard('V002__seed_catalog.sql'), sql);

  // V003 약관
  // 고객 약관은 TERM_BODIES 의 제목·본문을, 매니저 약관은 라벨에서 '(필수)'·'(선택)' 을 뗀 것을 제목으로 쓴다. 모두 version 1
  let terms = '-- V003__seed_terms.sql — 약관 (임시 문안. 법무 검토 후 새 마이그레이션으로 교체)\n';
  terms += rows('terms', ['id', 'audience', 'label', 'title', 'body', 'required', 'version', 'sort'], [
    ...customer.TERMS.map((t, i) => [str(t.id), "'customer'", str(t.label), str(customer.TERM_BODIES[t.id].title), str(customer.TERM_BODIES[t.id].body), t.required, 1, i + 1]),
    ...manager.MANAGER_TERMS.map((t, i) => [str(t.id), "'manager'", str(t.label), str(stripPrefix(t.label)), str(manager.MANAGER_TERM_BODIES[t.id]), t.required, 1, i + 1]),
  ]);
  writeFileSync(guard('V003__seed_terms.sql'), terms);

  // V004 지원 (FAQ · 공지). 공지 날짜 '2026.09.10' → 서울 00:00
  const noticeTs = (d) => `'${d.replace(/\./g, '-')} 00:00:00+09'`;
  let support = '-- V004__seed_support.sql — FAQ·공지 (코드에서 정한 문구. 운영 문구는 새 마이그레이션 또는 관리 도구로)\n';
  support += rows('faqs', ['audience', 'question', 'answer', 'sort'], customer.FAQ.map((f, i) => ["'customer'", str(f.question), str(f.answer), i + 1]));
  support += '\n' + rows('notices', ['audience', 'title', 'body', 'published_at'], [
    ...customer.NOTICES.map((n) => ["'customer'", str(n.title), str(n.body), noticeTs(n.date)]),
    ...manager.MANAGER_NOTICES.map((n) => ["'manager'", str(n.title), str(n.body), noticeTs(n.date)]),
  ]);
  writeFileSync(guard('V004__seed_support.sql'), support);

  // V005 운영 값
  // app_settings 테이블에 key 하나당 jsonb 값 하나로 들어간다 (금액 비율·시간 한도·화면 문구 묶음)
  const settings = {
    platform_fee_rate: 0.1,
    offer_ttl_minutes: 30,
    preferred_offer_ttl_minutes: 10,
    free_option_limit: customer.FREE_OPTION_LIMIT,
    time_slots: customer.TIME_SLOTS,
    cancel_deadline_hours: 24,
    duration_hours: { home: 3, office: 3, appliance: 2, 'move-in': 4 }, // home 은 Figma "예상 소요 3시간". 나머지는 판단 항목
    dispatch_lead_days: 7,
    series_term_months: 1,
    recording_retention_days: 90,
    home_slides: customer.HOME_SLIDES,
    repair_hero: customer.REPAIR_HERO,
    notes_copy: customer.NOTES_COPY,
    confirm_copy: customer.CONFIRM_COPY,
    reservations_header: customer.RESERVATIONS_HEADER,
    reservation_tabs: customer.RESERVATION_TABS,
    settings_header: customer.SETTINGS_HEADER,
    settings_groups: customer.SETTINGS_GROUPS,
    customer_privacy_note: customer.PRIVACY_NOTE,
    manager_privacy_note: manager.MANAGER_PRIVACY_NOTE,
    cleaner_pending_label: customer.CLEANER_PENDING,
  };
  let v5 = '-- V005__seed_settings.sql — 운영 값 (코드에 상수로 두지 않는다). 바꿀 때는 새 마이그레이션에서 update\n';
  v5 += rows('app_settings', ['key', 'value'], Object.entries(settings).map(([k, v]) => [str(k), jsonb(v)]));
  writeFileSync(guard('V005__seed_settings.sql'), v5);
  console.log('seed SQL 4개 생성');
}

// ── shapes: 서버 테스트가 기대하는 응답 JSON 을 다시 쓴다 (파일 이름 = CatalogIT 의 shape 이름) ──
if (mode === 'shapes') {
  const dir = path.join(root, 'backend/src/test/resources/mock-shapes');
  mkdirSync(dir, { recursive: true });
  // 늘 같은 모양(2칸 들여쓰기 + 끝 줄바꿈)으로 써서 내용이 같으면 파일도 글자 그대로 같다
  const write = (name, v) => writeFileSync(path.join(dir, name + '.json'), JSON.stringify(v, null, 2) + '\n');
  // 카테고리마다: 카테고리 정보·옵션·요청사항 칩·확인 화면 문구
  for (const k of CATEGORY_ORDER) {
    write(`category-${k}`, customer.BOOKING_CATEGORIES[k]);
    write(`options-${k}`, { ...customer.BOOKING_OPTIONS[k], freeLimit: customer.FREE_OPTION_LIMIT });
    write(`notice-chips-${k}`, { chips: customer.NOTICE_CHIPS[k], copy: customer.NOTES_COPY });
    write(`confirm-copy-${k}`, { copy: customer.CONFIRM_COPY, frequencyDescriptions: customer.FREQUENCY_DESCRIPTIONS[k], seriesTermMonths: customer.SERIES_TERM_MONTHS });
  }
  write('time-slots', { ...customer.TIME_SLOTS, unavailable: [], bookableDays: customer.BOOKABLE_DAYS, changeDeadlineHours: customer.CHANGE_DEADLINE_HOURS });
  write('reasons-reject', manager.REJECT_REASONS);
  write('reasons-pause', manager.PAUSE_REASONS);
  write('terms-customer', customer.TERMS);
  write('terms-manager', { terms: manager.MANAGER_TERMS, note: manager.MANAGER_PRIVACY_NOTE });
  write('term-customer-service', { id: 'service', ...customer.TERM_BODIES.service });
  write('term-manager-recording', { id: 'recording', title: stripPrefix(manager.MANAGER_TERMS.find((t) => t.id === 'recording').label), body: manager.MANAGER_TERM_BODIES.recording });
  // 보유 기간 문구(V039)가 앱 mock 과 서버에서 어긋나지 않게 (자체 점검 #31 후속)
  write('term-manager-privacy', { id: 'privacy', title: stripPrefix(manager.MANAGER_TERMS.find((t) => t.id === 'privacy').label), body: manager.MANAGER_TERM_BODIES.privacy });
  console.log('mock-shapes 갱신');
}
