// 검색에서 선택한 주소를 확인하고 상세주소·장소 별칭을 저장한다.
// 상세주소는 비워도 된다(단독주택). 비운 채 '다음' 이면 깜빡했을 수 있어 확인 시트를 띄운다 (2026-11-11 사용자 요청, 문구 ADDRESS_DETAIL_SKIP_COPY)
import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, Button, Sheet, ActionError, useAction, cx } from '@ansimclean/ui';
import { addAddress } from '../../api/customer.js';
import { useBookingDraft } from './bookingDraft.js';
import { ADDRESS_DETAIL_SKIP_COPY as SKIP, INPUT_LIMITS } from '../../api/mock.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingAddressConfirm.module.css';
import KeyboardAwareFooter from '../../components/KeyboardAwareFooter.jsx';

export default function BookingAddressConfirm() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo();
  const [draft, update] = useBookingDraft(category);
  const roadAddress = draft.roadAddress ?? draft.addressQuery ?? '';
  const detailAddress = draft.detailAddress ?? '';
  const addressLabel = draft.addressLabel ?? '';
  const next = useAction(async () => {
    const saved = await addAddress({ roadAddress, detailAddress: detailAddress.trim(), label: addressLabel.trim() });
    update({ reuseAddress: 'no', addressId: saved.id, roadAddress, detailAddress: detailAddress.trim(), addressLabel: saved.label });
    navigate(back.next(`/booking/${category}/options`));
  });
  const [askSkip, setAskSkip] = useState(false);
  const detailRef = useRef(null);
  const focusDetail = useRef(false);
  // '입력하기' 로 시트가 닫히면 상세주소 칸으로. 시트가 닫히며 '다음' 버튼으로 돌려준 포커스를 이어서 옮긴다
  useEffect(() => {
    if (askSkip || !focusDetail.current) return;
    focusDetail.current = false;
    detailRef.current?.focus();
  }, [askSkip]);
  const onNext = () => (detailAddress.trim() ? next.run() : setAskSkip(true));

  return (
    <ScreenLayout noPadding gap="none" navBar={<NavBar onBack={() => navigate(-1)} />}
      tabBar={<KeyboardAwareFooter>
        <Button variant="primary" chevron={false} className={common.ctaRounded}
          disabled={!roadAddress.trim()} loading={next.loading} onClick={onNext}>다음</Button>
        <ActionError error={next.error} />
      </KeyboardAwareFooter>}
    >
      <div className={cx(common.page, styles.page)}>
        <h1 className={cx('text-heading-m', common.pageTitle)}>장소 정보</h1>
        <section className={styles.address} aria-label="선택한 주소">
          <p className={cx('text-heading-s', styles.road)}>{roadAddress}</p>
          {draft.addressSub && <p className={cx('text-body-default', styles.sub)}>{draft.addressSub}</p>}
          <input ref={detailRef} className={styles.input} aria-label="상세주소" placeholder="상세 주소를 입력해주세요" autoComplete="address-line2"
            value={detailAddress} maxLength={INPUT_LIMITS.detailAddress} onChange={(e) => update({ detailAddress: e.target.value })} />
        </section>
        <section className={styles.alias}>
          <label className="text-title-small" htmlFor="address-alias">장소 별칭</label>
          <input id="address-alias" className={styles.input} placeholder="장소 별칭을 입력해주세요" value={addressLabel} maxLength={30}
            onChange={(e) => update({ addressLabel: e.target.value })} />
        </section>
      </div>
      <Sheet open={askSkip} onClose={() => setAskSkip(false)} title={SKIP.title}>
        <p className="text-body-default">{SKIP.description}</p>
        <Button variant="primary" chevron={false} className={common.ctaRounded}
          onClick={() => { focusDetail.current = true; setAskSkip(false); }}>{SKIP.enter}</Button>
        <Button variant="secondary" chevron={false} className={common.ctaRounded}
          onClick={() => { setAskSkip(false); next.run(); }}>{SKIP.skip}</Button>
      </Sheet>
    </ScreenLayout>
  );
}
