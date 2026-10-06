// Figma: Booking/AddressSearch (64:136) — Sheet(흰 배경 + 1px 테두리) 안에 SheetHeader(StepProgress 4) + "주소를 검색해주세요" + 검색창 + 결과 목록. 4 카테고리 동일.
// Figma 의 MapPreview (flat image) 는 '검색 결과가 이미 있는' 그림이라, 진짜 결과인 줄 알게 했다 → 그리지 않는다 (사용성 검토 2026-09-22).
//   검색어가 없으면 입력 안내, 있는데 결과가 0이면 '주소 검색이 연결되어 있지 않다' 를 사실대로 말하고 입력한 대로 진행하게 한다. _Keyboard 는 OS 가 그림.
// 결과를 고르면 도로명주소를 채워 /address-confirm 으로. "다음" 은 결과 없이 직접 입력한 검색어를 그대로 쓴다 (코드에서 정함)
// 예약 플로우 4단계(/booking/:category/address-search). 검색어·고른 주소는 예약 초안(sessionStorage)에 둔다.
// 검색은 searchAddress → GET /catalog/address (주소 API 연동 전이라 서버는 빈 목록을 준다 — backend/docs/api.md).
// 수리 예약의 '다른 주소로 변경' 도 /booking/repair:<수리 종류>/address-search 로 이 화면을 빌려 쓴다 (RepairAddress.jsx).
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, SearchField, AddressResultList, InfoNote, Button, useAsync, cx } from '@ansimclean/ui';
import { searchAddress, saveBookingStep } from '../../api/customer.js';
import { ADDRESS_SEARCH_COPY } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingAddressSearch.module.css';

/** 주소 검색 화면. 검색 결과를 누르거나, 결과 없이 "다음" 을 누르면 그 주소로 주소 확인 화면에 간다 */
export default function BookingAddressSearch() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(또는 수리 예약)에서 왔으면 그 화면으로 돌아간다
  const [draft, update] = useBookingDraft(category);
  const query = draft.addressQuery ?? ''; // 검색창 글자는 초안에 바로 저장한다 — 새로고침·뒤로 가기에도 남는다
  // 검색어가 바뀔 때마다 다시 검색한다 (두 글자 미만이면 searchAddress 가 서버를 부르지 않고 빈 목록을 준다).
  // 검색 실패(error)는 따로 그리지 않는다 — 결과가 없는 것처럼 안내 문구가 보이고 직접 입력으로 진행할 수 있다
  const { data } = useAsync(() => searchAddress(query), [query]);
  const results = data?.results ?? [];

  // 고른 도로명주소를 초안에 넣고 주소 확인 화면으로.
  // carry: returnTo 가 있으면(확인 화면 '수정하기'·수리 예약 주소 변경) 다음 화면에도 이어 붙여, 주소 확인 화면의 "다음" 이 그리로 돌아간다
  const goConfirm = async (roadAddress) => {
    await saveBookingStep(category, 'address-search', { addressQuery: query, roadAddress });
    update((prev) => ({ roadAddress, detailAddress: prev.roadAddress === roadAddress ? prev.detailAddress : '' })); // 새 도로명에 옛 동·호수가 붙지 않게 (검수 3차)
    navigate(back.carry(`/booking/${category}/address-confirm`));
  };

  return (
    <ScreenLayout noPadding gap="none">
      <div className={common.sheet}>
        <div className={common.sheetStack}>
          <StepNavBar step={4} variant="sheet" onBack={() => navigate(-1)} />
          <div className={styles.searchBlock}>
            <h1 className={cx('text-heading-m', common.sectionTitle)}>주소를 검색해주세요</h1>
            <div className={styles.fieldAndMap}>
              <SearchField filled ariaLabel="주소 검색" value={query} onChange={(e) => update({ addressQuery: e.target.value })} autoComplete="street-address" />
              {/* 결과가 있으면 목록, 없으면 안내: 검색어가 비었으면 입력 안내, 적었는데 결과가 0이면 '검색 미연결' 안내 */}
              {results.length > 0 ? (
                <AddressResultList results={results} onSelect={(r) => goConfirm(r.main)} />
              ) : (
                <InfoNote glyph="ⓘ">{query.trim() ? ADDRESS_SEARCH_COPY.noResults : ADDRESS_SEARCH_COPY.guide}</InfoNote>
              )}
            </div>
          </div>
        </div>
        {/* 결과를 고르지 않고 "다음" 을 누르면 적은 검색어(앞뒤 공백 제거)를 도로명주소로 쓴다 */}
        <div className={common.cta}>
          <Button variant="primary" disabled={!query.trim()} onClick={() => goConfirm(query.trim())}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
