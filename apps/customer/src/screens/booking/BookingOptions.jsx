// Figma: Booking/Options (92:729) · Office (2038:1092) · Appliance (2038:882) · MoveIn (2038:672) — 구조 동일, 옵션 문구·가격·아이콘만 다름.
// Sheet 안에 SheetHeader(StepProgress 6) + "무료 옵션(택2)" 3개 + "유료 옵션" 2개. 선택 상태는 Figma 에 없어 코드에서 정함.
// 프로토타입 연결 없음 → "다음" = /notes (PROGRESS.md 판단 항목)
// 예약 플로우 6단계 (경로 /booking/:category/options). 옵션 목록은 서버(GET /catalog/categories/{c}/options)에서 받는다.
// 누를 때마다 고른 key 를 예약 초안(bookingDraft, sessionStorage)에 바로 저장하고, '다음' 에서 Step2 화면의
// '옵션 선택' 칸에 보일 요약 문구(optionsSummary)를 만든다. 확인 화면 '수정하기' 로 왔으면 확인 화면으로 돌아간다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, OptionItem, Button, ASSETS, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getBookingOptions, saveBookingStep } from '../../api/customer.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingOptions.module.css';

// 두 줄로 나뉜 옵션 이름(예: '반려동물' + 줄바꿈 + '배변 청소')을 공백 하나로 이어 한 줄로 만든다 — 요약 문구용
const oneLine = (label) => label.replace(/\s*\n\s*/g, ' ').trim();

/** Booking/Options 화면. 무료 옵션은 서버가 준 freeLimit 개(택2)까지, 유료 옵션은 개수 제한 없이 고른다 */
export default function BookingOptions() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(?returnTo=)에서 왔으면 그 화면으로 돌아간다 (수리 예약은 이 화면을 쓰지 않는다)
  const [draft, update] = useBookingDraft(category);
  const { data: options, error, reload } = useAsync(() => getBookingOptions(category), [category]);
  // 지금까지 고른 옵션 key 배열 (초안에 없으면 아무것도 안 고른 상태)
  const free = draft.freeOptions ?? [];
  const paid = draft.paidOptions ?? [];
  // 카테고리별 옵션 그림 목록. 서버 옵션의 icon 은 이 배열의 순번이다. 모르는 카테고리면 가정 청소 그림을 쓴다
  const icons = ASSETS.bookingOptionIcons[category] ?? ASSETS.bookingOptionIcons.home;


  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!options) return <ScreenLoading />;

  // key 를 field('freeOptions' | 'paidOptions') 목록에 넣거나 뺀다. 함수형 update 라 빠르게 연달아 눌러도 직전 값에서 계산한다
  const toggle = (key, field) =>
    update((prev) => {
      const list = prev[field] ?? [];
      return { [field]: list.includes(key) ? list.filter((k) => k !== key) : [...list, key] };
    });

  // '다음': 고른 옵션을 서버 목록 순서(무료 → 유료)대로 모아 요약 문구를 만들어 초안에 넣고 요청사항 화면(또는 returnTo)으로 간다.
  // saveBookingStep 은 지금 서버를 부르지 않는다 — 초안은 기기에만 두고 서버에는 예약 완료 때 한 번 보낸다 (api/customer.js)
  const next = async () => {
    const chosen = [...options.free.filter((o) => free.includes(o.key)), ...options.paid.filter((o) => paid.includes(o.key))];
    const optionsSummary = chosen.map((o) => oneLine(o.label)).join(', ');
    await saveBookingStep(category, 'options', { freeOptions: free, paidOptions: paid });
    update({ optionsSummary });
    navigate(back.next(`/booking/${category}/notes`));
  };

  // 이 화면은 NavBar 대신 흰 시트(common.sheet) 안에 단계 표시(StepNavBar variant="sheet")를 둔다.
  // 무료 옵션: 한도만큼 이미 골랐으면 아직 안 고른 것만 비활성(disabled) — 고른 것은 눌러서 풀 수 있다.
  return (
    <ScreenLayout noPadding gap="none">
      <div className={common.sheet}>
        <div className={cx(common.sheetStack, styles.stack)}>
          <StepNavBar step={6} variant="sheet" onBack={() => navigate(-1)} />
          <div className={styles.sections}>
            <section className={styles.freeSection} aria-label="무료 옵션">
              <h1 className={cx('text-heading-m', common.sectionTitle)}>무료 옵션(택2)</h1>
              <div className={styles.freeRow}>
                {options.free.map((o) => (
                  <OptionItem key={o.key} image={icons[o.icon]} label={o.label} selected={free.includes(o.key)} disabled={!free.includes(o.key) && free.length >= options.freeLimit} onSelect={() => toggle(o.key, 'freeOptions')} />
                ))}
              </div>
            </section>
            <section className={styles.paidSection} aria-label="유료 옵션">
              <h2 className={cx('text-heading-m', common.sectionTitle)}>유료 옵션</h2>
              <div className={styles.paidRow}>
                {options.paid.map((o) => (
                  <OptionItem key={o.key} image={icons[o.icon]} label={o.label} price={o.price} selected={paid.includes(o.key)} onSelect={() => toggle(o.key, 'paidOptions')} />
                ))}
              </div>
            </section>
          </div>
        </div>
        <div className={common.cta}>
          <Button variant="primary" onClick={next}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
