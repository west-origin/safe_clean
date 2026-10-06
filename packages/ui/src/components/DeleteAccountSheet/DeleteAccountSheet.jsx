// 회원 탈퇴 확인 시트 (Figma 에 없음 — 코드에서 정함, 앱스토어 심사 요건). 두 앱이 같이 쓴다.
// 비밀번호를 받아 onConfirm(password) 를 부르고, 실패 문구(error)는 화면이 넘긴다.
// 쓰는 곳 예: 고객 앱 Settings, 매니저 앱 Profile 의 "회원 탈퇴". 서버 호출(deleteAccount)은 화면이 onConfirm 안에서 한다.
// 공용 Sheet(바텀시트) 안에 안내문 → 비밀번호 칸 → 탈퇴하기 / 돌아가기 버튼을 쌓는다.
import { useState } from 'react';
import Sheet from '../Sheet/Sheet.jsx';
import TextField from '../TextField/TextField.jsx';
import Button from '../Button/Button.jsx';
import { cx } from '../../utils/cx.js';
import styles from './DeleteAccountSheet.module.css';

/**
 * @param {object} props
 * @param {boolean} props.open
 * @param {() => void} props.onClose
 * @param {(password: string) => void} props.onConfirm 탈퇴 실행 (화면의 useAction.run)
 * @param {boolean} [props.loading]
 * @param {string} [props.error] 실패 문구 (비밀번호 불일치·진행 중 예약 등)
 * @param {string} [props.description] 안내 문구. 기본: 되돌릴 수 없다는 안내
 */
// 탈퇴 확인 시트. 입력한 비밀번호는 이 컴포넌트가 들고 있다가 "탈퇴하기" 를 누를 때만 onConfirm 으로 넘긴다.
export default function DeleteAccountSheet({ open, onClose, onConfirm, loading = false, error, description }) {
  const [password, setPassword] = useState('');
  // 돌아가기 버튼뿐 아니라 Sheet 의 ESC·배경 탭 닫기도 이 함수를 거쳐서, 요청 중 막기와 비밀번호 비우기가 똑같이 적용된다.
  const close = () => {
    if (loading) return; // 탈퇴 요청이 나간 뒤에는 결과를 볼 때까지 닫지 않는다
    setPassword(''); // 닫으면 비밀번호를 남기지 않는다 — 다시 열었을 때 바로 '탈퇴하기' 가 눌리지 않게
    onClose();
  };
  return (
    <Sheet open={open} onClose={close} title="정말 탈퇴할까요?" gap="lg">
      {/* description 을 안 넘기면 기본 안내문을 쓴다. error 를 넘기면 TextField 가 비밀번호 칸 아래 오류 문구로 보여 준다 */}
      <p className={cx('text-body-default', styles.description)}>{description ?? '탈퇴하면 계정과 저장한 정보가 삭제되고 되돌릴 수 없어요. 예약·이용 내역은 법령에 따라 일정 기간 보관돼요.'}</p>
      <TextField label="비밀번호 확인" placeholder="비밀번호를 입력해주세요" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} error={error} />
      <Button variant="destructive" loading={loading} disabled={!password} onClick={() => onConfirm(password)}>
        탈퇴하기
      </Button>
      <Button variant="secondary" onClick={close} disabled={loading}>
        돌아가기
      </Button>
    </Sheet>
  );
}
