// Figma: Booking/Options (92:729) · Office (2038:1092) · Appliance (2038:882) · MoveIn (2038:672) — 구조 동일, 옵션 문구·가격·아이콘만 다름.
// Sheet 안에 SheetHeader(StepProgress 6) + "무료 옵션(택2)" 3개 + "유료 옵션" 2개. 선택 상태는 Figma 에 없어 코드에서 정함.
// 프로토타입 연결 없음 → "다음" = /notes (PROGRESS.md 판단 항목)
// 2026-11-01 사용자 첨부 시안으로 바꿨다: 흰 시트·단계 점 대신 뒤로가기 + '옵션을 선택해 주세요.', 무료는 한 줄 카드(OptionCheckRow),
//   유료는 두 장씩 카드(PaidOptionCard), 하단에 금액(서비스 시간 + 고른 유료 옵션, 표시용) + "다음". '청소 범위' 는 사용자 요청으로 뺐다.
//   매장(store)·가정/원룸(home) 옵션 그림은 홈 서비스 아이콘과 같은 플랫 SVG(FLAT_OPTION_ICONS), 다른 카테고리는 기존 래스터 그림.
// 예약 플로우 6단계 (경로 /booking/:category/options). 옵션 목록은 서버(GET /catalog/categories/{c}/options)에서 받는다.
// 누를 때마다 고른 key 를 예약 초안(bookingDraft, sessionStorage)에 바로 저장하고, '다음' 에서 Step2 화면의
// '옵션 선택' 칸에 보일 요약 문구(optionsSummary)를 만든다. 확인 화면 '수정하기' 로 왔으면 확인 화면으로 돌아간다.
import { useNavigate, useParams } from 'react-router';
import { useState } from 'react';
import { ScreenLayout, NavBar, OptionCheckRow, PaidOptionCard, PriceCtaBar, Sheet, Button, ASSETS, cx, useAsync, ScreenError, ScreenLoading, OptionShelfIcon, OptionEntranceIcon, OptionRecycleIcon, OptionKitchenIcon, OptionSignboardIcon, OptionDishesIcon, OptionLaundryIcon, OptionBeddingIcon, OptionPetCareIcon, OptionFridgeIcon } from '@ansimclean/ui';
import { getBookingOptions, saveBookingStep } from '../../api/customer.js';
import { OPTIONS_COPY as COPY, SCHEDULE_COPY, SCHEDULE_PRICE } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import { won, schedulePrice, optionPrice } from './bookingPrice.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingOptions.module.css';

// 플랫 옵션 그림 — 서버 옵션의 icon 순번. 매장(V058: 진열대·출입문·분리배출·주방·간판), 가정/원룸(설거지·세탁·침구·반려동물 배변·냉장고)
const FLAT_OPTION_ICONS = {
  store: [OptionShelfIcon, OptionEntranceIcon, OptionRecycleIcon, OptionKitchenIcon, OptionSignboardIcon],
  home: [OptionDishesIcon, OptionLaundryIcon, OptionBeddingIcon, OptionPetCareIcon, OptionFridgeIcon],
};

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
  const [detailOpen, setDetailOpen] = useState(false); // '자세히' 시트
  // 순번 i 의 그림: 매장·가정이면 플랫 SVG, 아니면 기존 래스터
  const iconOf = (i) => {
    const Flat = FLAT_OPTION_ICONS[category]?.[i];
    if (Flat) return <Flat />;
    const img = icons[i];
    return img ? <img src={img.src} srcSet={img.srcSet} alt="" /> : null;
  };


  const navBar = <NavBar onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!options) return <ScreenLoading navBar={navBar} />;

  // 하단 금액(표시용): 날짜/시간 화면의 서비스 시간 금액 + 고른 유료 옵션
  const hours = draft.durationHours ?? SCHEDULE_PRICE.standardHours;
  const basePrice = schedulePrice(hours, draft.frequency ?? 'recurring');
  const paidChosen = options.paid.filter((o) => paid.includes(o.key));
  const total = basePrice + paidChosen.reduce((sum, o) => sum + optionPrice(o.price), 0);

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
    update({ optionsSummary, paidOptionsTotal: chosen.reduce((sum, o) => sum + optionPrice(o.price ?? ''), 0) }); // 합계는 주의사항 화면 하단 금액용
    navigate(back.next(`/booking/${category}/notes`));
  };

  // 무료 옵션: 한도(freeLimit)만큼 이미 골랐으면 아직 안 고른 것만 비활성 — 고른 것은 눌러서 풀 수 있다.
  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <PriceCtaBar priceLabel={won(total)} detailLabel={SCHEDULE_COPY.detail} onDetail={() => setDetailOpen(true)}>
          <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={next}>
            다음
          </Button>
        </PriceCtaBar>
      }
    >
      <div className={cx(common.page, styles.page)}>
        <h1 className={cx('text-heading-m', common.pageTitle)}>{COPY.title}</h1>
        <section className={styles.section} aria-labelledby="options-free-title">
          <h2 id="options-free-title" className={cx('text-heading-s', common.pageTitle)}>{COPY.free}</h2>
          <div className={styles.freeList}>
            {options.free.map((o) => (
              <OptionCheckRow key={o.key} icon={iconOf(o.icon)} label={o.label} selected={free.includes(o.key)} disabled={!free.includes(o.key) && free.length >= options.freeLimit} onToggle={() => toggle(o.key, 'freeOptions')} />
            ))}
          </div>
        </section>
        <section className={styles.section} aria-labelledby="options-paid-title">
          <h2 id="options-paid-title" className={cx('text-heading-s', common.pageTitle)}>{COPY.paid}</h2>
          <div className={styles.paidGrid}>
            {options.paid.map((o) => (
              <PaidOptionCard key={o.key} icon={iconOf(o.icon)} label={o.label} price={o.price} selected={paid.includes(o.key)} onToggle={() => toggle(o.key, 'paidOptions')} />
            ))}
          </div>
        </section>
      </div>

      {/* '자세히': 서비스 시간 금액 + 고른 유료 옵션 */}
      <Sheet open={detailOpen} onClose={() => setDetailOpen(false)} title={SCHEDULE_COPY.detailTitle}>
        <dl className={styles.detail}>
          <div className={styles.detailRow}>
            <dt className={cx('text-body-default', styles.detailKey)}>{`${SCHEDULE_COPY.duration} ${hours}시간`}</dt>
            <dd className={cx('text-body-bold', styles.detailValue)}>{won(basePrice)}</dd>
          </div>
          {paidChosen.map((o) => (
            <div key={o.key} className={styles.detailRow}>
              <dt className={cx('text-body-default', styles.detailKey)}>{oneLine(o.label)}</dt>
              <dd className={cx('text-body-bold', styles.detailValue)}>{o.price}</dd>
            </div>
          ))}
        </dl>
        <p className={cx('text-body-s', styles.detailKey)}>{SCHEDULE_COPY.detailNote}</p>
        <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={() => setDetailOpen(false)}>
          {SCHEDULE_COPY.confirm}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
