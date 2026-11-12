// 고객 앱 홈 혜택 배너 처음 3장 올리기 (배너·공지 관리 4회차). 서버로 옮기기 전 앱에 고정돼 있던 이미지·문구 그대로.
// 사용: API_URL=https://<api>/api/v1 ADMIN_JWT=<어드민 aal2 토큰> node scripts/seed-home-banners.mjs
// 토큰 받는 법은 backend/docs/runbook.md "어드민 계정과 토큰". 이미 올렸는지 확인하지 않는다 — 한 번만 실행한다(지울 때는 매니저 앱 배너 관리).
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const { API_URL, ADMIN_JWT } = process.env;
if (!API_URL || !ADMIN_JWT) {
  console.error('API_URL 과 ADMIN_JWT 를 환경변수로 주세요');
  process.exit(1);
}
const assets = path.join(path.dirname(fileURLToPath(import.meta.url)), '../packages/ui/src/assets');
// 예전 apps/customer/src/screens/home/HomePromotions.jsx 의 BANNERS 와 같은 순서·문구
const BANNERS = [
  { file: 'home-banner-coupon.png', title: '첫 청소 15,000원 쿠폰팩', description: '즉시 지급 · 신규 고객 웰컴' },
  { file: 'home-banner-daily.png', title: '청소는 맡기고, 일상은 여유롭게', description: '우리 집에 맞는 청소를 만나보세요' },
  { file: 'home-banner-gift.png', title: '소중한 사람에게 깨끗한 하루', description: '깨끗함을 넘어, 안심을 전해요' },
];

for (const b of BANNERS) {
  const form = new FormData();
  form.append('file', new Blob([await readFile(path.join(assets, b.file))], { type: 'image/png' }), b.file);
  form.append('title', b.title);
  form.append('description', b.description);
  const res = await fetch(`${API_URL}/admin/banners`, { method: 'POST', headers: { Authorization: `Bearer ${ADMIN_JWT}` }, body: form });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    console.error(`${b.title}: ${res.status}`, body.error ?? body);
    process.exit(1);
  }
  console.log(`올림: ${b.title} (${body.id}, ${body.stateLabel})`);
}
