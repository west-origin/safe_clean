// Figma: Auth/SignUpComplete (81:186) — 문구·"지금 시작하기" 버튼이 모두 든 401×852 이미지 한 장 (flat image)
// 프로토타입 연결 없음 → 이미지(버튼) 탭 시 /home (PROGRESS.md 판단 항목)
// 약관 동의 기록이 없으면(다른 기기에서 이어가기 등) /auth/terms?next=complete 로 보내 동의 뒤 여기로 돌아온다 (코드에서 정함). 그 밖의 실패는 문구로
// 고객 앱 가입 흐름의 마지막 화면(/auth/complete). 이미지 한 장이 통째로 버튼이다.
// 누르면 completeSignUp() 이 가입 초안에 모아 둔 약관 동의를 서버(POST /auth/signup/complete)에 보내 가입을 끝낸다.
import { useNavigate } from 'react-router';
import { ScreenLayout, ImageButton, ASSETS, useAction, ActionError } from '@ansimclean/ui';
import { completeSignUp } from '../../api/customer.js';
import { SIGNUP_COMPLETE_ERROR } from '../../api/mock.js';
import styles from './SignUpComplete.module.css';

// 약관 화면으로 되돌려 보낼 오류 코드.
// TERMS_REQUIRED = 초안에 동의 기록이 없어 앱이 서버에 보내기 전에 만든 오류 (customer.js completeSignUp),
// VALIDATION = 서버가 필수 약관 누락으로 거부한 것 (backend/docs/api.md)
const TERMS_CODES = ['TERMS_REQUIRED', 'VALIDATION'];

/** 가입 완료 화면. 탭 → 가입 완료 요청 → 성공하면 /home, 약관 누락이면 약관 화면, 401(로그인이 풀림)이면 로그인 화면 */
export default function SignUpComplete() {
  const navigate = useNavigate();
  const start = useAction(async () => {
    try {
      await completeSignUp();
    } catch (e) {
      if (TERMS_CODES.includes(e?.code)) {
        // replace: 방문 기록의 이 화면 자리를 약관 화면으로 바꾼다 (뒤로 가기로 이 화면에 다시 오지 않는다)
        navigate('/auth/terms?next=complete', { replace: true });
        return;
      }
      if (e?.status === 401) {
        navigate('/auth/login', { replace: true }); // 토큰이 사라졌다 — 이 화면은 RequireAuth 밖
        return;
      }
      throw e; // 그 밖의 실패(통신 오류 등)는 다시 던져 문구로 보인다
    }
    navigate('/home');
  });

  return (
    <ScreenLayout statusBar={false} noPadding gap="none" className={styles.screen}>
      <ImageButton image={ASSETS.signUpComplete} label="회원가입이 완료되었어요! 지금 시작하기" onClick={start.run} className={styles.image} />
      {/* 실패 문구는 서버 문구 대신 이 화면의 고정 문구(SIGNUP_COMPLETE_ERROR)로 보인다.
          styles.error 로 보이는 화면 아래쪽에 겹쳐 그린다 — 높이 852px 이미지 밑에 붙이면 그보다 짧은 화면에서는 스크롤해야 보인다 */}
      <ActionError error={start.error} message={SIGNUP_COMPLETE_ERROR} className={styles.error} />
    </ScreenLayout>
  );
}
