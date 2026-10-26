// Figma: Booking/Confirm (64:168) · Office (2038:1149) · Appliance (2038:939) · MoveIn (2038:729) — 구조 동일, 서비스 종류·주기 문구·칩 내용만 다름. 393×1125 (스크롤)
// 헤더 + HeroIllustration(우상단 절대 위치) + SummaryCard(6행, 각 "수정하기") + "완료"(304). "수정하기" 는 해당 단계로. "완료" = createBooking → /complete (PROGRESS.md 판단 항목)
// 코드에서 정함: 일정·주소가 없으면 "완료" 비활성 + 행에 안내 문구, 요청 실패는 버튼 아래 문구, 성공 후에는 뒤로 가도 이 화면으로 돌아오지 않는다(replace)
// (코드에서 정함, B3) "요금" 행: 서버 견적(getBookingQuote) — 1회 금액, 정기청소면 회차 날짜와 총액, 결제 안내. 견적이 없으면 완료 불가 (BACKEND_PLAN.md §9.2-7).
//   "완료" 는 처음 누를 때 submitKey 를 만들어 초안에 두고 재시도 때 같은 키를 보낸다 (§9.4)
// 예약 플로우 8단계(/booking/:category/confirm). 앞 단계들이 sessionStorage 초안에 모은 값을 한 장에 보여 주고,
// "완료" 로 서버에 예약을 만든다(POST /customer/bookings). 성공하면 초안을 지우고 완료 화면(?id=)으로 간다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, Card, SummaryItem, Chip, Button, ASSETS, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getBookingCategory, getFrequencyOptions, getBookingOptions, getNoticeChips, getConfirmCopy, getSavedAddress, getBookingQuote, createBooking, newSubmitKey } from '../../api/customer.js';
import { QUOTE_COPY, CONFIRM_EMPTY_COPY } from '../../api/mock.js';
import { useBookingDraft, formatDate, formatTimeKo } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingConfirm.module.css';

// 옵션 라벨의 줄바꿈(앞뒤 공백 포함)을 공백 하나로 바꿔 칩 안에서 한 줄로 보이게 한다
const oneLine = (label) => label.replace(/\s*\n\s*/g, ' ').trim();

/** 예약 최종 확인 화면. 항목마다 "수정하기" 로 그 단계에 갔다가 돌아오고, 일정·주소·견적이 모두 있어야 "완료" 가 열린다 */
export default function BookingConfirm() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 여기서는 '수정하기' 주소(그 단계 + ?returnTo=이 화면)를 만드는 withReturn 만 쓴다
  const [draft, update, clear] = useBookingDraft(category);
  const useNewAddress = draft.reuseAddress === 'no'; // Step2 에서 '기존 주소 사용' 에 아니요를 골랐다
  // 화면에 필요한 목록·문구를 한꺼번에 받는다. 하나라도 실패하면 오류 화면 (기존 주소가 없는 404 만은 null 로 받아 화면을 연다)
  const { data: info, error, reload } = useAsync(
    () =>
      Promise.all([
        getBookingCategory(category),
        getFrequencyOptions(),
        getBookingOptions(category),
        getNoticeChips(category),
        getConfirmCopy(category),
        // 새 주소를 쓰면 기존 주소는 필요 없다. 기존 주소가 없으면(404 → null) 화면은 열리고 "주소를 입력해주세요" 로 안내한다. 조회 실패(네트워크 등)는 오류 화면 → 다시 시도
        useNewAddress ? Promise.resolve(null) : getSavedAddress(),
      ]).then(([cat, frequencies, options, notices, confirm, savedAddress]) => ({ cat, frequencies, options, chips: notices.chips, ...confirm, savedAddress })),
    [category, useNewAddress],
  );
  // 요금은 일정·옵션·주기가 정해진 뒤 서버 견적으로. 초안이 바뀌면 다시 받는다
  // (다시 받는 조건은 아래 deps 의 카테고리·주기·날짜·시간·옵션뿐이다. 옵션 배열은 문자열 하나로 합쳐 비교한다)
  const optionKeys = [...(draft.freeOptions ?? []), ...(draft.paidOptions ?? [])].join(',');
  const quoteQ = useAsync(() => getBookingQuote(category, draft), [category, draft.frequency, draft.date, draft.time, optionKeys]);
  // 주소: "아니요" 면 새 주소만, "네" 면 기존 주소만. 둘 다 없으면 null → 완료 불가 (info 가 오기 전에는 null)
  const address = useNewAddress ? (draft.roadAddress ? { roadAddress: draft.roadAddress, detailAddress: draft.detailAddress } : null) : (info?.savedAddress ?? null);
  const hasSchedule = Boolean(draft.date && draft.time);
  const quote = hasSchedule ? quoteQ.data : null;
  const canComplete = Boolean(address?.roadAddress?.trim() && (!useNewAddress || address.detailAddress?.trim()) && hasSchedule && quote);
  // "완료": 예약 생성. 멱등 키(submitKey)를 초안에 먼저 저장해 두어, 실패 뒤 다시 눌러도 같은 키가 가고 예약이 두 번 생기지 않는다.
  // 초안 내용을 바꾸면 useBookingDraft 가 submitKey 를 지워 다음 "완료" 때 새 키를 만든다 (bookingDraft.js).
  // 주소는 위에서 고른 address(새 주소 또는 기존 주소)로 초안 값을 덮어 보낸다
  const submit = useAction(async () => {
    const submitKey = draft.submitKey ?? newSubmitKey();
    if (!draft.submitKey) update({ submitKey });
    const booking = await createBooking(category, { ...draft, ...address, submitKey });
    clear(); // 성공한 뒤에만 초안을 지운다 — 실패하면 입력이 그대로 남는다
    // replace: 방문 기록의 이 화면 자리를 완료 화면으로 바꾼다 (파일 머리 설명)
    navigate(`/booking/${category}/complete?id=${booking.id}`, { replace: true });
  });


  if (error) return <ScreenError error={error} onRetry={reload} navBar={<StepNavBar step={8} onBack={() => navigate(-1)} />} />;
  if (!info) return <ScreenLoading navBar={<StepNavBar step={8} onBack={() => navigate(-1)} />} />;

  const frequency = draft.frequency ?? 'recurring'; // 주기가 없으면 정기청소 (customer.js bookingBody 도 같은 기본값)
  // 초안에는 key 만 있으므로, 서버 목록에서 고른 key 의 항목(라벨)을 찾아 칩으로 그린다
  const chosenOptions = [...info.options.free.filter((o) => (draft.freeOptions ?? []).includes(o.key)), ...info.options.paid.filter((o) => (draft.paidOptions ?? []).includes(o.key))];
  const chosenNotices = info.chips.filter((c) => (draft.notices ?? []).includes(c.key));
  const go = (step) => navigate(back.withReturn(`/booking/${category}/${step}`, `/booking/${category}/confirm`)); // 한 단계만 고치고 돌아온다

  return (
    <ScreenLayout noPadding gap="none" navBar={<StepNavBar step={8} onBack={() => navigate(-1)} />} className={styles.screen}>
      {/* 오른쪽 위 장식 일러스트 (alt="" — 스크린리더가 건너뛴다) */}
      <img className={styles.hero} src={ASSETS.confirmHero.src} srcSet={ASSETS.confirmHero.srcSet} width={ASSETS.confirmHero.width} height={ASSETS.confirmHero.height} alt="" />
      <div className={cx(common.body, styles.body)}>
        <div className={cx(common.stack, styles.stack)}>
          <header className={styles.header}>
            <h1 className={cx('text-heading-m', common.sectionTitle)}>{info.copy.title}</h1>
            <p className={cx('text-body-s', styles.description)}>{info.copy.description}</p>
          </header>

          {/* 요약 카드. "수정하기" 는 go(단계) 로 그 단계에 ?returnTo=확인 화면 을 붙여 연다 — 서비스 종류만 홈으로 */}
          <Card padding="none" gap="none" className={styles.summary}>
            <SummaryItem icon={ASSETS.confirmIcons.service} title="서비스 종류" value={info.cat?.label} onEdit={() => navigate('/home')} />
            {/* 주소 "수정하기": 새 주소면 주소 검색으로, 기존 주소면 '기존 주소 사용' 선택(step2)으로 */}
            <SummaryItem
              icon={ASSETS.confirmIcons.address}
              title="주소"
              value={address ? address.roadAddress : '주소를 입력해주세요'}
              subValue={address ? address.detailAddress : undefined}
              onEdit={() => go('step2')}
            />
            <SummaryItem icon={ASSETS.confirmIcons.schedule} title="방문 일정" value={hasSchedule ? formatDate(draft.date) : '날짜와 시간을 선택해주세요'} subValue={hasSchedule ? formatTimeKo(draft.time) : undefined} onEdit={() => go('datetime')} />
            <SummaryItem icon={ASSETS.confirmIcons.frequency} title="서비스 주기" value={info.frequencies.find((f) => f.key === frequency)?.label} subValue={info.frequencyDescriptions[frequency]} onEdit={() => go('frequency')} />
            {/* 고르지 않은 항목은 제목과 빈 상자만 남아 빠뜨린 건지 알 수 없었다 → "없어요" (전체 점검 G6) */}
            {chosenOptions.length > 0 ? (
              <SummaryItem icon={ASSETS.confirmIcons.options} title="선택 옵션" onEdit={() => go('options')}>
                <div className={styles.chips}>
                  {chosenOptions.map((o) => (
                    <Chip key={o.key}>{oneLine(o.label)}</Chip>
                  ))}
                </div>
              </SummaryItem>
            ) : (
              <SummaryItem icon={ASSETS.confirmIcons.options} title="선택 옵션" value={CONFIRM_EMPTY_COPY.options} onEdit={() => go('options')} />
            )}
            {chosenNotices.length > 0 ? (
              <SummaryItem icon={ASSETS.confirmIcons.notes} title="주의사항" onEdit={() => go('notes')}>
                <div className={styles.chips}>
                  {chosenNotices.map((c) => (
                    <Chip key={c.key}>{c.label}</Chip>
                  ))}
                </div>
              </SummaryItem>
            ) : (
              <SummaryItem icon={ASSETS.confirmIcons.notes} title="주의사항" value={CONFIRM_EMPTY_COPY.notices} onEdit={() => go('notes')} />
            )}
            {draft.request?.trim() ? (
              <SummaryItem icon={ASSETS.confirmIcons.requests} title="요청사항" note={draft.request} onEdit={() => go('notes')} />
            ) : (
              <SummaryItem icon={ASSETS.confirmIcons.requests} title="요청사항" value={CONFIRM_EMPTY_COPY.request} onEdit={() => go('notes')} />
            )}
            {/* 요금 행: 일정이 있을 때만. 값은 견적 결과 → 없으면 오류 문구(400·409 는 서버 문구, 그 밖은 QUOTE_COPY.failed) → 받는 중엔 비움.
                견적이 순간 장애로 실패했을 때만 "수정하기" 자리가 견적 다시 받기(quoteQ.reload)가 된다 */}
            {hasSchedule && (
              // TODO(asset): 요금 아이콘은 Figma 에 없어 옵션 아이콘을 재사용
              <SummaryItem
                icon={ASSETS.confirmIcons.options}
                title={QUOTE_COPY.title}
                value={quote ? (frequency === 'recurring' ? QUOTE_COPY.recurring(quote.perVisit.label, quote.visitsPlanned, quote.totalLabel) : quote.perVisit.label) : quoteQ.error ? (quoteQ.error.status === 409 || quoteQ.error.status === 400 ? quoteQ.error.message : QUOTE_COPY.failed) : undefined}
                subValue={quote ? quote.paymentNote : undefined}
                onEdit={!quote && quoteQ.error && quoteQ.error.status !== 409 && quoteQ.error.status !== 400 ? quoteQ.reload : undefined} // 순간 장애면 같은 화면에서 다시 요청할 길을 둔다 (검수 3차)
              >
                {/* 총액 한 줄뿐이라 옵션이 얼마인지·정기 할인이 얼마인지 알 수 없었다 (사용성 검토 2026-09-22) */}
                {quote && (
                  <dl className={styles.priceRows}>
                    <div className={styles.priceRow}>
                      <dt className={cx('text-caption-l', styles.priceLabel)}>{QUOTE_COPY.baseLabel}</dt>
                      <dd className={cx('text-caption-l', styles.priceValue)}>{quote.perVisit.baseLabel}</dd>
                    </div>
                    {quote.perVisit.optionsLabel && (
                      <div className={styles.priceRow}>
                        <dt className={cx('text-caption-l', styles.priceLabel)}>{QUOTE_COPY.optionsLabel}</dt>
                        <dd className={cx('text-caption-l', styles.priceValue)}>{quote.perVisit.optionsLabel}</dd>
                      </div>
                    )}
                    {quote.savingNote && (
                      <div className={styles.priceRow}>
                        <dt className={cx('text-caption-l', styles.priceLabel)}>{QUOTE_COPY.savingLabel}</dt>
                        <dd className={cx('text-caption-l', styles.saving)}>{quote.savingNote}</dd>
                      </div>
                    )}
                  </dl>
                )}
                {/* 정기청소면 서버가 정한 회차 방문 날짜를 칩으로 */}
                {quote && frequency === 'recurring' && (
                  <div className={styles.chips}>
                    {quote.visits.map((v) => (
                      <Chip key={v.date}>{v.label}</Chip>
                    ))}
                  </div>
                )}
              </SummaryItem>
            )}
          </Card>
        </div>

        <div className={common.ctaWide}>
          <Button variant="primary" chevron={false} disabled={!canComplete} loading={submit.loading} onClick={submit.run}>
            완료
          </Button>
          {/* 예약 생성 실패: 서버 문구가 있으면 그것을, 없으면 fallback 문구를 버튼 아래에 */}
          <ActionError error={submit.error} fallback={'예약을 완료하지 못했어요. 다시 시도해주세요.'} className={styles.error} />
        </div>
      </div>
    </ScreenLayout>
  );
}
