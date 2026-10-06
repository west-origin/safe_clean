// Figma: Booking/AddressConfirm (92:402) — Sheet 안에 SheetHeader(StepProgress 5) + "이 주소를 사용할까요?" + Verified 아이콘 + 도로명주소/상세주소 필드. 4 카테고리 동일.
// 프로토타입 연결 없음 → "다음" = /options (PROGRESS.md 판단 항목). 도로명주소는 검색어를 미리 채운다
// 예약 플로우 5단계(/booking/:category/address-confirm). 주소 검색 화면에서 고른(또는 입력한) 도로명주소에
// 상세주소(동·호수)를 더해 예약 초안(sessionStorage)에 둔다. 서버에는 확인 화면에서 예약을 만들 때 한꺼번에 간다.
// 수리 예약의 '다른 주소로 변경' 도 주소 검색 → 이 화면을 빌려 쓴다 (:category = 'repair:<수리 종류>', RepairAddress.jsx).
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, TextField, VerifiedIcon, Button, cx } from '@ansimclean/ui';
import { saveBookingStep } from '../../api/customer.js';
import { useBookingDraft } from './bookingDraft.js';
import { INPUT_LIMITS } from '../../api/mock.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingAddressConfirm.module.css';

/** 주소 확인 화면. 도로명주소·상세주소 두 칸을 모두 채워야 "다음" 이 열린다 */
export default function BookingAddressConfirm() {
  const navigate = useNavigate();
  const { category } = useParams(); // 주소의 :category — 청소 카테고리(예: home) 또는 수리 예약이 빌려 쓸 때 'repair:<수리 종류>'
  const back = useReturnTo(); // 확인 화면 '수정하기'(또는 수리 예약)에서 왔으면 그 화면으로 돌아간다
  const [draft, update] = useBookingDraft(category);
  // 도로명주소가 아직 없으면 주소 검색 화면에 적은 검색어를 대신 보여 준다. 칸을 고치면 update 로 초안에 바로 저장된다
  const roadAddress = draft.roadAddress ?? draft.addressQuery ?? '';
  const detailAddress = draft.detailAddress ?? '';

  // "다음": 지금 칸의 값을 초안에 확정하고 옵션 단계로 (returnTo 가 있으면 그 화면으로).
  // saveBookingStep 은 지금 서버를 부르지 않는다 (customer.js — 서버에는 예약 완료 때 한 번 보낸다)
  const next = async () => {
    await saveBookingStep(category, 'address-confirm', { roadAddress, detailAddress });
    update({ roadAddress, detailAddress });
    navigate(back.next(`/booking/${category}/options`));
  };

  return (
    <ScreenLayout noPadding gap="none">
      <div className={common.sheet}>
        <div className={common.sheetStack}>
          <StepNavBar step={5} variant="sheet" onBack={() => navigate(-1)} />
          <div className={styles.form}>
            <div className={styles.titleRow}>
              <h1 className={cx('text-heading-m', common.sectionTitle)}>이 주소를 사용할까요?</h1>
              <VerifiedIcon className={styles.verified} />
            </div>
            {/* maxLength 는 mock.js INPUT_LIMITS — 서버 설정과 같아야 하는 값이다 (mock.js 머리 설명) */}
            <TextField label="도로명주소" placeholder="서울특별시 강남구 테헤란로 123" autoComplete="street-address" value={roadAddress} onChange={(e) => update({ roadAddress: e.target.value })} maxLength={INPUT_LIMITS.roadAddress} />
            <TextField label="상세주소" placeholder="604동 1002호" value={detailAddress} onChange={(e) => update({ detailAddress: e.target.value })} maxLength={INPUT_LIMITS.detailAddress} />
          </div>
        </div>
        {/* 공백만 적은 칸은 빈 칸으로 본다 */}
        <div className={common.cta}>
          <Button variant="primary" disabled={!roadAddress?.trim() || !detailAddress?.trim()} onClick={next}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
