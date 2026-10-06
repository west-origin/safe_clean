// Figma: 서비스 주소 확인 (1:424) — StepProgress 4/8 → 질문 → 주소 카드(현재 선택된 주소) → "주소가 다르면 변경해주세요" + Button/Secondary "다른 주소로 변경" → 방문 안내 → "다음". 다음 → /datetime
// 처음엔 등록된 기본 주소를 채운다. 변경은 청소 예약의 주소 검색·확인 화면을 빌려 쓰고(?returnTo=) 돌아온다 (코드에서 정함)
// 빌려 쓰는 화면 주소는 /booking/repair:<수리 종류>/address-search 다. 그 화면의 :category 가 수리 초안 키가 되어
// 같은 초안(sessionStorage)에 roadAddress·detailAddress 를 적어 준다 (repairFlow.js 머리 참고).
import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, StepQuestion, SelectedAddressCard, InfoNote, Button, ScreenError, ScreenLoading, useAsync, cx } from '@ansimclean/ui';
import { getRepairBooking, getSavedAddress } from '../../../api/customer.js';
import { useBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, path, stepOf, serviceName } from './repairFlow.js';
import { useReturnTo } from '../../booking/useReturnTo.js';
import styles from './repair.module.css';

/** 방문 주소를 확인하는 화면. 주소가 있어야 '다음' 을 누를 수 있다 */
export default function RepairAddress() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo();
  const [draft, update] = useBookingDraft(draftKey(category));
  // 초안에 주소가 없을 때만 회원 기본 주소를 묻는다 (없으면 getSavedAddress 가 null → 아래 '등록된 주소가 없어요' 안내).
  // 주소가 채워지면 needSaved 가 false 로 바뀌어 useAsync 가 다시 불리는데, 그때는 기본 주소를 묻지 않는다
  // (deps 가 바뀐 것이라 카탈로그를 다시 받는 동안 잠깐 로딩 화면이 보인다)
  const needSaved = !draft.roadAddress;
  const { data, error, reload } = useAsync(async () => ({ catalog: await getRepairBooking(), saved: needSaved ? await getSavedAddress() : null }), [needSaved]);
  // 받은 기본 주소를 초안에 한 번 채운다. 이미 주소가 있으면(주소 검색에서 돌아온 경우 등) 덮어쓰지 않는다
  useEffect(() => {
    if (data?.saved && !draft.roadAddress) update({ roadAddress: data.saved.roadAddress, detailAddress: data.saved.detailAddress ?? '' });
  }, [data, draft.roadAddress, update]);
  const { step, total } = stepOf(category, 'address');
  const navBar = <StepNavBar step={step} total={total} onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  const copy = data.catalog.copy.address;
  // '다른 주소로 변경': 주소 검색 화면의 returnTo 에 이 화면 주소를 넣는다. 이 화면에 returnTo(확인 화면)가 이미 있으면
  // carry 로 그것까지 붙여 넘겨, 돌아온 뒤 여기서 '다음' 을 누르면 확인 화면으로 간다
  const changeAddress = () => navigate(back.withReturn(`/booking/${draftKey(category)}/address-search`, back.carry(path(category, 'address')))); // 검색·확인 뒤 여기로, 여기서 '다음' 은 원래 돌아갈 곳으로

  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={styles.body}>
        <div className={cx(styles.stack, styles.stackXl)}>
          <StepQuestion service={serviceName(category, data.catalog, draft)} title={copy.title} description={copy.description} />
          {draft.roadAddress ? (
            <SelectedAddressCard title={copy.cardTitle} caption={copy.caption} roadAddress={draft.roadAddress} detailAddress={draft.detailAddress} />
          ) : (
            <InfoNote glyph="ⓘ">{copy.empty}</InfoNote>
          )}
          <div className={styles.changeBlock}>
            <p className={cx('text-caption-l', styles.changeHint)}>{copy.changeHint}</p>
            <Button variant="secondary" onClick={changeAddress}>
              {copy.changeButton}
            </Button>
          </div>
          <InfoNote glyph="ⓘ">{copy.note}</InfoNote>
        </div>
        <div className={styles.cta}>
          {/* 확인 화면에서 고치러 왔으면(?returnTo=) 확인 화면으로, 아니면 날짜·시간 단계로 */}
          <Button variant="primary" className={styles.ctaButton} disabled={!draft.roadAddress} onClick={() => navigate(back.next(path(category, 'datetime')))}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
