// Figma: Auth/SignUp (69:1089)
// 프로토타입 연결 없음 → 소셜 3종·전화번호 가입 → 약관 동의(/auth/terms), 로그인하기 → /auth/login (화면 이름·순서로 판단, PROGRESS.md)
// 고객 앱 가입 첫 화면 (/signup). 예: 온보딩, 로그인 전의 RequireAuth 가 여기로 보낸다
// (전부 찾으려면 '/signup' 을 검색한다).
// 지금 실제로 되는 가입은 전화번호뿐이다: 소셜 버튼은 안내 시트만 열고, 전화번호 가입 → /auth/terms?method=phone.
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ScreenLayout, ImageButton, Sheet, Button, ASSETS, cx, useAction, ActionError } from '@ansimclean/ui';
import { signUpWith } from '../../api/customer.js';
import { SOCIAL_SIGNUP_COPY } from '../../api/mock.js';
import styles from './SignUp.module.css';

/** 가입 방식 선택 화면. 화면 이동과 소셜 안내 시트(열림 여부)만 다루고, 입력값은 없다. */
export default function SignUp() {
  const navigate = useNavigate();
  const { state } = useLocation(); // RequireAuth 가 넘긴 원래 목적지 — 로그인까지 이어 준다

  // 소셜 로그인은 결정(§13-1)대로 아직 없다. 예전에는 카카오를 눌러도 말없이 전화번호 가입으로 갔다 —
  // 무엇이 되는지 먼저 알려 주고 사용자가 고르게 한다 (사용성 검토 2026-09-22)
  const [socialOpen, setSocialOpen] = useState(false);
  // 가입 시작: signUpWith 는 지금 목록(SIGN_UP_METHODS)에 있는 method 면 모두 { method: 'phone' } 을 돌려준다 → 약관 동의 화면.
  // 목록에 없는 method 면 던지고, 그 문구는 시트 안의 ActionError(start.error)에만 보인다
  const start = useAction(async (method) => {
    const res = await signUpWith(method);
    navigate(`/auth/terms?method=${res.method}`);
  });

  return (
    <ScreenLayout noPadding gap="none" className={styles.screen}>
      <div className={styles.body}>
        <header className={styles.header}>
          <h1 className="text-heading-l">
            깨끗한
            <br />
            일상이 시작되는 곳
          </h1>
          <p className={cx('text-body-s', styles.muted)}>
            믿을 수 있는 전문가와 함께
            <br />
            더 편안한 하루를 만들어보세요.
          </p>
        </header>

        {/* HeroArt 일러스트: 장식이라 alt="" (스크린리더가 건너뛴다). 위치·겹침 순서는 SignUp.module.css 의 .hero */}
        <img className={styles.hero} src={ASSETS.signUpHero.src} srcSet={ASSETS.signUpHero.srcSet} width={ASSETS.signUpHero.width} height={ASSETS.signUpHero.height} alt="" />

        <div className={styles.social}>
          {/* 소셜 3종: 이미지 버튼. 위 두 개는 .overlap 으로 다음 버튼과 겹친다(이미지의 투명 여백 때문) */}
          <ImageButton image={ASSETS.socialKakao} label="카카오톡으로 시작하기" onClick={() => setSocialOpen(true)} className={styles.overlap} />
          <ImageButton image={ASSETS.socialGoogle} label="구글로 시작하기" onClick={() => setSocialOpen(true)} className={styles.overlap} />
          <ImageButton image={ASSETS.socialNaver} label="네이버로 시작하기" onClick={() => setSocialOpen(true)} />
        </div>

        {/* Figma: OrDivider 는 절대 위치 (left 27, top 526) */}
        <div className={styles.or} aria-hidden="true">
          <span className={styles.line} />
          <span className={cx('text-heading-s', styles.secondary)}>또는</span>
          <span className={styles.line} />
        </div>

        <div className={styles.phone}>
          <ImageButton image={ASSETS.signUpPhone} label="전화번호로 가입하기" onClick={() => start.run('phone')} />
        </div>

        <p className={cx('text-title-small', styles.footer)}>
          이미 계정이 있으신가요?{'    '}
          <button type="button" className={cx('text-title-small', styles.link)} onClick={() => navigate('/auth/login', { state })}>
            로그인하기
          </button>
        </p>
      </div>

      {/* 소셜 안내 시트 (코드에서 정함): 첫 버튼은 전화번호 가입과 같고, 두 번째 버튼은 시트만 닫는다 */}
      <Sheet open={socialOpen} onClose={() => setSocialOpen(false)} title={SOCIAL_SIGNUP_COPY.title}>
        <p className={cx('text-body-default', styles.sheetText)}>{SOCIAL_SIGNUP_COPY.description}</p>
        <ActionError error={start.error} />
        <Button variant="primary" chevron={false} loading={start.loading} onClick={() => start.run('phone')}>
          {SOCIAL_SIGNUP_COPY.confirm}
        </Button>
        <Button variant="secondary" chevron={false} onClick={() => setSocialOpen(false)}>
          {SOCIAL_SIGNUP_COPY.back}
        </Button>
      </Sheet>
    </ScreenLayout>
  );
}
