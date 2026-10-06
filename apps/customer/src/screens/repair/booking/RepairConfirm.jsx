// Figma: 예약 내용 최종 확인 (1:273) — StepProgress 7/8 → 질문 → 예약 요약(서비스 🧰 / 증상 상세 📷 / 서비스 주소 Pin / 방문 일정 Calendar / 현장 상황 🏠, 각 "수정 ›") → 비용 안내 → "예약하기". 성공 → /complete?id=
// 초안 전체를 요약해 보여 주고 '예약하기' 로 createRepairBooking(POST /customer/bookings, category 'repair')을 부른다.
// 각 행의 '수정' 은 그 단계 화면으로 ?returnTo=이 화면 을 붙여 보내, 고친 뒤 여기로 바로 돌아오게 한다 (useReturnTo.js).
// 예외: 가전수리의 서비스 행은 가전을 고치면 증상 단계를 한 번 더 거친 뒤 돌아온다 (RepairAppliance 의 carry).
// 주소 행에서 '다른 주소로 변경' 을 누르면 주소 검색·확인 화면을 지나 주소 단계로 돌아오고, 거기서 '다음' 을 눌러야 여기로 온다.
// 사진 만료(24시간): 지난 사진이 있으면(열 때·화면에 머무는 사이·앱을 내려 둔 사이·누른 순간) 또는 서버가 만료로 막으면,
// 사진을 빼고 증상 상세로 보내 다시 올리게 한다(repairFlow.js·useFreshPhotos.js). 요약의 'N장 첨부' 도 지난 사진은 세지 않는다.
import { useCallback, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, StepQuestion, ConfirmSummaryRow, InfoNote, Button, ScreenError, ScreenLoading, useAsync, useAction, InfoPinIcon, InfoCalendarIcon, cx, ActionError } from '@ansimclean/ui';
import { getRepairBooking, createRepairBooking, newRepairSubmitKey } from '../../../api/customer.js';
import { useBookingDraft, setBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, path, stepOf, serviceName, symptomLabel, dateLong, timeKo, hasApplianceSteps, freshPhotos, hasExpiredPhotos, isPhotoExpiredError } from './repairFlow.js';
import { useReturnTo } from '../../booking/useReturnTo.js';
import { useFreshPhotos } from './useFreshPhotos.js';
import styles from './repair.module.css';

/** 수리 예약 최종 확인 화면. 예약이 만들어지면 초안을 지우고 완료 화면으로 바꿔 넣는다(뒤로 가기로 이 화면에 돌아오지 않게 replace) */
export default function RepairConfirm() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo();
  const [draft, update, clear] = useBookingDraft(draftKey(category));
  const { data: catalog, error, reload } = useAsync(() => getRepairBooking(), []);
  // photos = 지금 쓸 수 있는 사진(요약의 'N장 첨부' 는 이것만 센다), expired = 초안에 24시간 지난 사진이 있다 (화면을 연 채 만료되거나 앱이 돌아오면 다시 잰다)
  const { photos, expired } = useFreshPhotos(draft.photos);
  // 사진이 만료됐을 때: 초안 사진을 photos 로 바꾸고 만료 안내를 켠 뒤 증상 상세로 간다. returnTo 를 붙여 거기서 '다음' 을 누르면 여기로 돌아온다.
  // 곧 이 화면을 떠나므로 훅의 update 대신 저장소에 바로 쓴다(setBookingDraft) — 증상 상세가 열리면서 이 값을 읽는다.
  // 본문(사진)이 바뀌므로 멱등 키도 지운다 — 같은 키로 다른 본문을 보내면 서버가 거부한다 (§9.4).
  // useCallback: 아래 effect 가 deps 로 쓰므로 렌더마다 새 함수가 되지 않게 (detailPath 는 문자열이라 렌더마다 같다)
  const detailPath = back.withReturn(path(category, 'detail'), path(category, 'confirm'));
  const backToPhotos = useCallback(
    (nextPhotos) => {
      setBookingDraft(draftKey(category), { photos: nextPhotos, photosExpired: true, submitKey: undefined });
      navigate(detailPath);
    },
    [category, navigate, detailPath],
  );
  // '예약하기': 멱등 키를 초안에 먼저 저장하고 서버에 보낸다. 실패하면 submit.error 로 버튼 아래에 문구가 뜬다 (사진 만료만은 위 backToPhotos)
  const submit = useAction(async () => {
    // 누른 순간 24시간이 지난 사진이 있으면 보내지 않는다 — 서버가 지웠거나 곧 지워 만료로 막힌다. 지난 사진만 뺀다.
    // 아래 effect 가 보통 먼저 보내지만, 백그라운드에서 돌아온 직후처럼 화면이 아직 다시 재지 못했을 때를 위해 여기서도 잰다
    if (hasExpiredPhotos(draft.photos)) return backToPhotos(freshPhotos(draft.photos));
    // 이전 시도에서 저장해 둔 키가 있으면 그대로 쓴다 — 응답을 못 받아 다시 눌러도 서버는 같은 요청으로 본다.
    // 초안 내용을 바꾸면 useBookingDraft 가 이 키를 지워 새 키를 받는다
    const submitKey = draft.submitKey ?? newRepairSubmitKey();
    if (!draft.submitKey) update({ submitKey }); // 재시도해도 예약이 두 번 생기지 않게 (§9.4)
    let booking;
    try {
      booking = await createRepairBooking(category, { ...draft, submitKey });
    } catch (e) {
      // 서버 정리가 사진을 이미 지웠다 — 어느 사진인지는 오지 않으므로 초안 사진을 모두 비우고 다시 올리게 한다
      if (isPhotoExpiredError(e)) return backToPhotos([]);
      throw e;
    }
    clear();
    navigate(`${path(category, 'complete')}?id=${booking.id}`, { replace: true });
  });
  // 열 때 이미, 또는 화면에 머무는 사이(앱을 내려 둔 사이 포함) 24시간 지난 사진이 생기면 누르기를 기다리지 않고 증상 상세로 보내 안내한다 —
  // 예전에는 요약이 만료된 사진까지 'N장 첨부' 로 세다가 '예약하기' 를 눌러야 되돌아왔다 (합친 38건 검토 R10).
  // 예약을 보내는 중에는 서버 응답(성공 → 완료 화면, 만료 → 위 backToPhotos)에 맡긴다
  useEffect(() => {
    if (expired && !submit.loading) backToPhotos(freshPhotos(draft.photos));
  }, [expired, submit.loading, backToPhotos, draft.photos]);
  const { step, total } = stepOf(category, 'confirm');
  const navBar = <StepNavBar step={step} total={total} onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!catalog) return <ScreenLoading navBar={navBar} />;

  const copy = catalog.copy.confirm;
  // 고른 현장 상황 key → 확인 화면용 짧은 문구(summary). 칩 목록에 없는 key 는 뺀다
  const situations = (draft.situations ?? []).map((k) => catalog.situations.find((s) => s.key === k)?.summary).filter(Boolean);
  const editStep = (s) => navigate(back.withReturn(path(category, s), path(category, 'confirm'))); // 한 단계만 고치고 돌아온다

  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={styles.body}>
        <div className={cx(styles.stack, styles.stackXl)}>
          <StepQuestion service={serviceName(category, catalog, draft)} title={copy.title} description={copy.description} />
          <div className={styles.summary}>
            {/* 서비스 행의 '수정': 가전수리는 가전 선택부터, 다른 수리 종류는 가전 단계가 없어 증상 상세로 */}
            <ConfirmSummaryRow icon="🧰" label={copy.rows.service} value={serviceName(category, catalog, draft)} sub={symptomLabel(catalog, draft) || undefined}
              onEdit={() => editStep(hasApplianceSteps(category) ? 'appliance' : 'detail')} />
            <ConfirmSummaryRow icon="📷" label={copy.rows.detail} value={draft.detail?.trim() || '—'} sub={copy.photosSub(photos.length)} onEdit={() => editStep('detail')} />
            <ConfirmSummaryRow icon={<InfoPinIcon />} label={copy.rows.address} value={draft.roadAddress ?? ''} sub={draft.detailAddress || undefined} onEdit={() => editStep('address')} />
            <ConfirmSummaryRow icon={<InfoCalendarIcon />} label={copy.rows.schedule} value={dateLong(draft.date)} sub={timeKo(draft.time)} onEdit={() => editStep('datetime')} />
            <ConfirmSummaryRow icon="🏠" label={copy.rows.situations} value={situations.length ? situations.join(' · ') : copy.noSituations} sub={draft.request?.trim() || undefined} onEdit={() => editStep('notes')} />
          </div>
          {/* 수리비는 현장 진단 뒤에 정해져 이 화면에는 금액이 없다 (api/customer.js createRepairBooking 설명: 금액 0) */}
          <InfoNote tone="warning" title={copy.costTitle}>{copy.costBody}</InfoNote>
        </div>
        <div className={styles.cta}>
          <Button variant="primary" className={styles.ctaButton} loading={submit.loading} onClick={submit.run}>
            {copy.cta}
          </Button>
          <ActionError error={submit.error} fallback={'예약하지 못했어요. 다시 시도해주세요.'} />
        </div>
      </div>
    </ScreenLayout>
  );
}
