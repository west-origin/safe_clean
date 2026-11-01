// (코드에서 정함) 저장 주소 수정 — 주소 선택 화면(BookingAddress)의 '수정' 에서 온다 (/booking/:category/address-edit/:id).
// 2026-10-31 사용자 요청: 상세주소와 주소 이름(별칭)만 고칠 수 있고, 잘못 등록한 주소는 지울 수 있다.
// 도로명주소는 읽기 전용 — 틀렸으면 지우고 '+ 주소추가' 로 새로 넣는다 (서버 PATCH 도 별칭·상세만 받는다).
// 저장·삭제가 성공한 뒤에만 주소 선택 화면으로 돌아간다. 이번 예약이 이 주소를 골라 둔 상태면 초안도 같이 고친다.
// 문구는 mock.js ADDRESS_EDIT_COPY.
import { useState } from 'react';
import { Navigate, useNavigate, useParams } from 'react-router';
import { ScreenLayout, NavBar, TextField, Button, Sheet, ActionError, cx, useAsync, useAction, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getAddresses, updateAddress, removeAddress } from '../../api/customer.js';
import { ADDRESS_EDIT_COPY as COPY, INPUT_LIMITS } from '../../api/mock.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import styles from './BookingAddressEdit.module.css';

/** 주소 수정 화면. 별칭·상세주소 입력 + 저장, 아래 '주소 삭제'(확인 시트) */
export default function BookingAddressEdit() {
  const navigate = useNavigate();
  const { category, id } = useParams();
  const [draft, update] = useBookingDraft(category);
  const { data, error, reload } = useAsync(async () => (await getAddresses()).find((a) => a.id === id) ?? null, [id]);
  const [form, setForm] = useState(null); // 고치는 중인 { label, detailAddress } — 처음엔 서버 값
  const [confirmOpen, setConfirmOpen] = useState(false);
  const values = form ?? { label: data?.label ?? '', detailAddress: data?.detailAddress ?? '' };

  const save = useAction(async () => {
    const saved = await updateAddress(id, values);
    if (draft.addressId === id) update({ detailAddress: saved.detailAddress ?? '' }); // 이번 예약에 고른 주소면 초안도
    navigate(-1);
  });
  const remove = useAction(async () => {
    await removeAddress(id);
    if (draft.addressId === id) update({ addressId: undefined, roadAddress: undefined, detailAddress: undefined, reuseAddress: undefined });
    navigate(-1);
  });

  const navBar = <NavBar onBack={() => navigate(-1)} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (data === undefined) return <ScreenLoading navBar={navBar} />;
  if (data === null) return <Navigate to={`/booking/${category}/address`} replace />; // 이미 지워진 주소 — 목록으로

  return (
    <ScreenLayout
      noPadding
      gap="none"
      navBar={navBar}
      stickyCta={
        <>
          <Button variant="primary" chevron={false} className={common.ctaRounded} loading={save.loading} onClick={save.run}>
            {COPY.save}
          </Button>
          <ActionError error={save.error} fallback={COPY.saveFailed} />
        </>
      }
    >
      <div className={cx(common.page, styles.page)}>
        <h1 className={cx('text-heading-m', common.pageTitle)}>{COPY.title}</h1>
        <div className={styles.fields}>
          <div className={styles.road}>
            <TextField label={COPY.road} value={data.roadAddress} disabled onChange={() => {}} />
            <p className={cx('text-body-s', styles.note)}>{COPY.roadNote}</p>
          </div>
          <TextField label={COPY.label} placeholder={COPY.labelPlaceholder} value={values.label} maxLength={30} onChange={(e) => setForm({ ...values, label: e.target.value })} />
          <TextField label={COPY.detail} placeholder={COPY.detailPlaceholder} value={values.detailAddress} maxLength={INPUT_LIMITS.detailAddress} onChange={(e) => setForm({ ...values, detailAddress: e.target.value })} />
        </div>
        <Button variant="outlineGray" chevron={false} className={cx(common.pill, styles.remove)} onClick={() => setConfirmOpen(true)}>
          {COPY.remove}
        </Button>
      </div>

      <Sheet open={confirmOpen} onClose={() => setConfirmOpen(false)} title={COPY.removeTitle}>
        <p className={cx('text-body-default', styles.note)}>{[data.roadAddress, data.detailAddress].filter(Boolean).join(' ')}</p>
        <Button variant="destructive" chevron={false} loading={remove.loading} onClick={remove.run}>
          {COPY.removeConfirm}
        </Button>
        <Button variant="secondary" chevron={false} onClick={() => setConfirmOpen(false)}>
          {COPY.cancel}
        </Button>
        <ActionError error={remove.error} fallback={COPY.removeFailed} />
      </Sheet>
    </ScreenLayout>
  );
}
