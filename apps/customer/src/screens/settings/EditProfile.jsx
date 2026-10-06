// Settings › 내 정보 수정 (Figma 에 화면 없음 — 가입 화면의 필드 구성을 그대로 씀: 이름·닉네임·휴대폰)
// 들어오는 길: 설정 화면의 프로필 카드와 '내 정보 수정' 메뉴(/settings/profile).
// 흐름: GET /customer/profile 로 현재 값 → 이름·닉네임 수정 → 저장(PATCH /customer/profile) → 토스트 후 설정 화면으로.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, TextField, PhoneField, Button, ScreenError, ScreenLoading, useAsync, useAction, ActionError, showToast } from '@ansimclean/ui';
import { getProfileForm, updateProfile } from '../../api/customer.js';
import styles from './settingsSub.module.css';

/** 내 정보 수정 화면. 휴대폰 번호는 보여 주기만 하고 바꿀 수 없다 */
export default function EditProfile() {
  const navigate = useNavigate();
  const { data, error, reload } = useAsync(() => getProfileForm(), []);
  const [edits, setEdits] = useState({}); // 사용자가 바꾼 값만 담고, 화면에는 불러온 값 위에 덮어서 보여준다
  const form = { ...data, ...edits };
  // 휴대폰 번호 변경은 재인증이 필요해(§13-7) 서버에 보내지 않는다 — 이름·닉네임만
  const save = useAction(async () => {
    await updateProfile({ name: form.name, nickname: form.nickname });
    showToast('저장했어요');
    navigate('/settings');
  });

  const navBar = <NavBar title="내 정보 수정" onBack={() => navigate('/settings')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;

  // 입력칸마다 onChange 를 만든다: set('name') 은 이름 칸의 값을 edits.name 에 넣는다
  const set = (key) => (e) => setEdits((f) => ({ ...f, [key]: e.target.value }));

  // 저장 버튼은 이름·닉네임·휴대폰 중 하나라도 비면 잠근다
  return (
    <ScreenLayout
      navBar={navBar}
      stickyCta={
        <Button loading={save.loading} disabled={!form.name || !form.nickname || !form.phone} onClick={save.run}>
          저장
        </Button>
      }
    >
      <ActionError error={save.error} fallback={'저장하지 못했어요. 다시 시도해주세요.'} />
      <div className={styles.form}>
        <TextField label="이름" placeholder="이름을 입력해주세요" autoComplete="name" value={form.name} onChange={set('name')} />
        <TextField label="닉네임" placeholder="닉네임을 입력해주세요" autoComplete="nickname" value={form.nickname} onChange={set('nickname')} />
        {/* 휴대폰 번호 변경은 결정 §13-7 전까지 없다 — 편집·인증 버튼이 동작하는 것처럼 보이지 않게 disabled (코드에서 정함) */}
        <PhoneField label="휴대폰 번호" value={form.phone} onChange={set('phone')} disabled />
      </div>
    </ScreenLayout>
  );
}
