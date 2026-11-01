// (코드에서 정함) 예약 주소 선택 — 2026-10-31 사용자 첨부 시안 '주소를 선택해 주세요.' (Figma 에 없음)
// 서비스 주기 "다음" 에서 온다 (/booking/:category/address). 저장한 주소(GET /customer/addresses)가
//   없으면 → 바로 주소 검색(address-search)으로 바꿔 간다 (replace — 뒤로가기는 서비스 주기로)
//   있으면 → 주소 목록(기본 주소가 먼저 골라져 있다) + '+ 주소추가'(주소 검색) + 하단 "다음"
// "다음" 은 고른 주소를 초안에 넣고 날짜/시간 화면(schedule)으로. 주소 검색·주소 확인을 거쳐도 끝에는 그 화면으로 온다(returnTo).
// '수정' 은 주소 수정 화면(BookingAddressEdit) — 저장 주소의 별칭·상세주소를 고치거나 지운다.
// 문구는 mock.js ADDRESS_SELECT_COPY.
import { Navigate, useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, AddressOption, Button, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getAddresses, saveBookingStep } from '../../api/customer.js';
import { ADDRESS_SELECT_COPY as COPY } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingAddress.module.css';

/** 예약 주소 선택 화면. 저장 주소가 없으면 주소 검색으로 넘긴다 */
export default function BookingAddress() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(?returnTo=)에서 왔으면 끝에 그 화면으로 돌아간다
  const [draft, update] = useBookingDraft(category);
  const { data: addresses, error, reload } = useAsync(() => getAddresses(), []);
  const navBar = <NavBar onBack={() => navigate(-1)} />;

  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!addresses) return <ScreenLoading navBar={navBar} />;

  const after = back.next(`/booking/${category}/schedule`); // 주소를 정한 뒤 갈 곳
  const searchPath = back.withReturn(`/booking/${category}/address-search`, after); // 검색 → 확인 → after
  if (addresses.length === 0) return <Navigate to={searchPath} replace />;

  // 지금 고른 주소: 이번 예약에서 고른 것 → 기본 주소 → 첫 주소
  const selected = addresses.find((a) => a.id === draft.addressId) ?? addresses.find((a) => a.isDefault) ?? addresses[0];

  // 'no' = 확인 화면이 회원 기본 주소 대신 초안의 주소를 쓴다 (BookingConfirm)
  const pick = (a) => ({ reuseAddress: 'no', addressId: a.id, roadAddress: a.roadAddress, detailAddress: a.detailAddress ?? '' });

  const next = async () => {
    await saveBookingStep(category, 'address', pick(selected));
    update(pick(selected));
    navigate(after);
  };

  const edit = (a) => navigate(`/booking/${category}/address-edit/${encodeURIComponent(a.id)}`);

  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <Button variant="primary" chevron={false} className={common.ctaRounded} onClick={next}>
          다음
        </Button>
      }
    >
      <div className={cx(common.page, styles.page)}>
        <h1 className={cx('text-heading-m', common.pageTitle)}>{COPY.title}</h1>
        <div className={styles.list} role="radiogroup" aria-label="예약 주소">
          {addresses.map((a) => (
            <AddressOption
              key={a.id}
              name="booking-address"
              label={a.label}
              address={[a.roadAddress, a.detailAddress].filter(Boolean).join(' ')}
              selected={a.id === selected.id}
              onSelect={() => update({ addressId: a.id })}
              onEdit={() => edit(a)}
            />
          ))}
        </div>
        <Button variant="outlineGray" chevron={false} className={cx(common.pill, styles.add)} onClick={() => navigate(searchPath)}>
          {COPY.add}
        </Button>
      </div>
    </ScreenLayout>
  );
}
