// Settings › 주소 관리 (Figma 에 화면 없음 — 카드 목록 + 추가 시트, 가입 주소 화면의 필드 재사용)
// 들어오는 길: 설정 화면의 '주소 관리' 메뉴(/settings/addresses). 뒤로가기는 설정 화면으로.
// 서버: 목록 GET · 추가 POST · 기본으로 PUT …/default · 삭제 DELETE /customer/addresses (api/customer.js 의 함수로만 부른다).
// '예약 기본 주소' 표시가 붙은 주소가 예약할 때 "기존 주소" 로 쓰인다.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, Card, Button, Sheet, SearchField, TextField, EmptyState, ScreenError, ScreenLoading, useAsync, useAction, cx, ActionError, showToast } from '@ansimclean/ui';
import { getAddresses, addAddress, removeAddress, setDefaultAddress } from '../../api/customer.js';
import { ADDRESS_SEARCH_COPY, ADDRESS_MANAGE_COPY } from '../../api/mock.js';
import styles from './settingsSub.module.css';

// 주소 추가 시트 입력칸의 처음 값 (저장에 성공하면 이 값으로 비운다)
const EMPTY_FORM = { label: '', roadAddress: '', detailAddress: '' };

/** 주소 관리 화면: 저장된 주소 카드 목록 + '기본으로'·'삭제' 버튼 + 주소 추가 시트 */
export default function Addresses() {
  const navigate = useNavigate();
  const { data: list, error, reload, refresh, setData } = useAsync(() => getAddresses(), []);
  const [open, setOpen] = useState(false); // 주소 추가 시트가 열려 있는가
  const [form, setForm] = useState(EMPTY_FORM);
  // 저장: 서버가 돌려준 새 주소를 목록 끝에 붙이고 입력칸을 비운 뒤 시트를 닫는다 (목록 재조회 없이)
  const add = useAction(async () => {
    const item = await addAddress(form);
    setData((d) => [...d, item]);
    setForm(EMPTY_FORM);
    setOpen(false);
    showToast('주소를 추가했어요');
  });
  // 기본 주소를 지우면 서버가 다음 주소를 기본으로 바꾸므로 목록을 다시 받는다
  const [removingId, setRemovingId] = useState(null); // 누른 카드의 버튼만 스피너 (검수 4차: remove.loading 하나라 모든 삭제 버튼이 같이 돌았다)
  const remove = useAction(async (id) => {
    setRemovingId(id);
    try {
      await removeAddress(id);
      await refresh().catch(() => setData((prev) => prev.filter((a) => a.id !== id))); // 지운 건 성공 — 목록만 못 받으면 지운 카드만 뺀다 (자체 점검 #48)
      showToast('주소를 지웠어요');
    } finally {
      setRemovingId(null);
    }
  });

  // 어느 주소가 예약 "기존 주소" 인지 보이지 않고 바꿀 수도 없었다 (직접 사용 점검 U15)
  const [defaultingId, setDefaultingId] = useState(null); // '기본으로' 를 누른 카드 id — 그 카드 버튼만 스피너
  const makeDefault = useAction(async (id) => {
    setDefaultingId(id);
    try {
      setData(await setDefaultAddress(id)); // 서버가 바뀐 목록 전체를 돌려주므로 그대로 바꿔 끼운다
      showToast(ADDRESS_MANAGE_COPY.madeDefault);
    } finally {
      setDefaultingId(null);
    }
  });

  const navBar = <NavBar title="주소 관리" onBack={() => navigate('/settings')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!list) return <ScreenLoading navBar={navBar} />;

  // 입력칸마다 onChange 를 만든다: set('label') 은 이름 칸의 값을 form.label 에 넣는다
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  return (
    <ScreenLayout navBar={navBar} stickyCta={<Button onClick={() => setOpen(true)}>+ 주소 추가</Button>}>
      <ActionError error={remove.error} fallback={'주소를 지우지 못했어요. 다시 시도해주세요.'} />
      <ActionError error={makeDefault.error} fallback={ADDRESS_MANAGE_COPY.failed} />
      {list.length === 0 ? (
        <EmptyState icon="smile" title="저장된 주소가 없어요" description="자주 쓰는 주소를 추가해두면 예약이 빨라져요." />
      ) : (
        <div className={styles.list}>
          {list.map((a) => (
            <Card key={a.id} padding="sm" gap="xs">
              <div className={styles.row}>
                <div className={styles.stack}>
                  <span className="text-title-small">{a.label}</span>
                  {a.isDefault && <span className={cx('text-caption', styles.secondary)}>{ADDRESS_MANAGE_COPY.defaultBadge}</span>}
                  <p className={cx('text-body-s', styles.primary)}>{a.roadAddress}</p>
                  <p className={cx('text-body-s', styles.secondary)}>{a.detailAddress}</p>
                </div>
                <div className={styles.sheetActions}>
                  {/* 기본 주소에는 '기본으로' 버튼이 없다. 한 카드가 처리 중이면 다른 카드의 같은 버튼은 잠근다 */}
                  {!a.isDefault && (
                    <Button variant="outlineGray" className={styles.inline} loading={defaultingId === a.id} disabled={makeDefault.loading && defaultingId !== a.id} onClick={() => makeDefault.run(a.id)} aria-label={`${a.label} 주소를 기본으로`}>
                      {ADDRESS_MANAGE_COPY.makeDefault}
                    </Button>
                  )}
                  <Button variant="outlineGray" className={styles.inline} loading={removingId === a.id} disabled={remove.loading && removingId !== a.id} onClick={() => remove.run(a.id)} aria-label={`${a.label} 주소 삭제`}>
                    삭제
                  </Button>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* 주소 추가 시트. 이름은 비워도 되고(api 가 '기타' 로 보낸다), 도로명·상세주소가 있어야 저장할 수 있다 */}
      <Sheet open={open} onClose={() => setOpen(false)} title="주소 추가" gap="lg">
        <TextField label="이름" placeholder="예: 집, 회사" value={form.label} onChange={set('label')} />
        <SearchField label="도로명주소" placeholder="도로명주소를 입력해주세요." autoComplete="street-address" value={form.roadAddress} onChange={set('roadAddress')} />
        <p className={cx('text-body-s', styles.secondary)}>{ADDRESS_SEARCH_COPY.guide}</p>
        <TextField label="상세주소" placeholder="상세주소를 입력해주세요." value={form.detailAddress} onChange={set('detailAddress')} />
        <ActionError error={add.error} fallback={'주소를 저장하지 못했어요. 다시 시도해주세요.'} />
        <Button disabled={!form.roadAddress || !form.detailAddress} loading={add.loading} onClick={add.run}>
          저장
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
