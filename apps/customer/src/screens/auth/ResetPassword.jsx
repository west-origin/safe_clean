// Auth/ResetPassword (Figma 에 없어 코드에서 정함 — 사용성 검토 2026-09-22: 비밀번호를 잊으면 들어올 길이 없었다)
// 로그인 화면 "비밀번호를 잊으셨나요?" → 이 화면. 휴대폰 인증(가입과 같은 /auth/phone/verify) → 새 비밀번호 → 바로 로그인 상태 → /home
// 문구는 api/mock.js 의 RESET_PASSWORD_COPY.
// 흐름: 번호 → "인증하기"(requestPasswordReset — 가입된 번호에만 문자) → 6자리 확인(verifyPasswordReset)
//   → 재설정 토큰은 이 화면 state 에만 둔다(가입 초안에 넣지 않는다) → 새 비밀번호 8자 이상
//   → resetPassword(POST /auth/password/reset, 성공하면 세션 저장) → /home.
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, BackButton, TextField, PhoneField, Button, cx, useAction, useCountdown, mmss, ActionError } from '@ansimclean/ui';
import { requestPasswordReset, verifyPasswordReset, resetPassword } from '../../api/customer.js';
import { RESET_PASSWORD_COPY as COPY, PASSWORD_OK_MESSAGE } from '../../api/mock.js';
import auth from './auth.module.css';
import styles from './ResetPassword.module.css';

/**
 * 비밀번호 재설정 화면. 인증이 끝나야(token 이 생겨야) 새 비밀번호 칸이 나타나고,
 * 번호를 고치면 인증을 처음부터 다시 한다. 인증이 끝나면 번호 칸과 인증 버튼이 잠기고(PhoneField disabled),
 * 변경 요청이 OTP_EXPIRED(토큰 만료·이미 사용)를 받으면 다시 열린다.
 */
export default function ResetPassword() {
  const navigate = useNavigate();
  const [phone, setPhone] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [resendAt, setResendAt] = useState(0);
  const resendIn = useCountdown(resendAt);
  const [code, setCode] = useState('');
  const [token, setToken] = useState(''); // 인증에 성공한 번호의 재설정 토큰
  const [password, setPassword] = useState('');
  const [notice, setNotice] = useState(''); // "가입되지 않은 번호예요"
  const phoneRef = useRef(''); // 응답이 오는 사이 번호를 고쳤으면 그 응답은 버린다 (ProfileInfo 와 같은 이유)

  // 인증번호 확인: 맞으면 재설정 토큰을 받아 둔다. 틀리면 verify.error 가 칸 아래에 뜬다
  const verify = useAction(async (value) => {
    const requested = phone;
    const res = await verifyPasswordReset(requested, value);
    if (requested !== phoneRef.current) return; // 보이는 번호와 다른 번호의 비밀번호를 바꾸지 않게
    setToken(res.verificationToken);
  });

  // 확인 요청 중에 고친 6자리는 앞 요청이 끝난 뒤 한 번 더 확인한다 — useAction 은 진행 중 run 을 버려 '맞는 번호가 보이는데 틀렸다' 가 남았다 (자체 점검 #37, 가입 화면과 같다)
  const verifying = useRef(false);
  const pendingCode = useRef(null);
  const runVerify = async (value) => {
    if (verifying.current) {
      pendingCode.current = value;
      return;
    }
    verifying.current = true;
    try {
      await verify.run(value); // 실패도 여기서 삼킨다(useAction)
    } finally {
      verifying.current = false;
    }
    const next = pendingCode.current;
    pendingCode.current = null;
    if (next && next !== value) runVerify(next);
  };

  // "인증하기"/"재전송": 앞선 인증번호·토큰·대기 중인 확인을 지우고 문자를 요청한다.
  // 보냈으면 재전송 대기 카운트다운을 시작하고, 가입되지 않은 번호면 안내 + 회원가입 링크를 띄운다
  const sendCode = useAction(async () => {
    setNotice('');
    setToken('');
    setCode('');
    pendingCode.current = null;
    verify.reset();
    const res = await requestPasswordReset(phone);
    if (res.sent) {
      setCodeSent(true);
      setResendAt(Date.now() + (res.resendAfter ?? 60) * 1000);
    } else if (res.reason === 'notFound') setNotice(res.message ?? COPY.notFound);
  });

  // 번호를 고치면 인증을 처음부터: 인증번호 칸·토큰·안내·실패 문구를 모두 지운다
  const onPhoneChange = (e) => {
    setPhone(e.target.value);
    phoneRef.current = e.target.value;
    setCodeSent(false);
    setCode('');
    setToken('');
    setNotice('');
    pendingCode.current = null; // 다른 번호로 옛 인증번호를 확인하지 않게
    verify.reset();
  };

  // 인증번호 입력: 숫자만 6자리까지 남기고, 6자리가 되면 바로 확인한다
  const onCodeChange = (e) => {
    const value = e.target.value.replace(/\D/g, '').slice(0, 6);
    setCode(value);
    if (value.length === 6) runVerify(value);
  };

  // "비밀번호 변경하기": 성공하면 로그인 상태로 홈. 실패 코드별 처리 —
  // PASSWORD_CHANGED = 비밀번호는 바뀌었고 새 세션 발급만 실패 → 로그인 화면에 안내를 넘긴다(LoginPhone 의 state.notice)
  // OTP_EXPIRED = 토큰이 만료됐거나 이미 쓰였다 → 인증 입력을 다시 연다.
  // PASSWORD_CHANGED 가 아닌 실패(OTP_EXPIRED 포함)는 다시 던져 submit.error 로 문구를 보인다
  const submit = useAction(async () => {
    try {
      await resetPassword({ verificationToken: token, password });
    } catch (e) {
      if (e?.code === 'PASSWORD_CHANGED') {
        navigate('/auth/login', { replace: true, state: { notice: e.message } }); // 비밀번호는 바뀌었다 — 새 비밀번호로 로그인 (자체 점검 #22)
        return;
      }
      if (e?.code === 'OTP_EXPIRED') {
        setToken(''); // 서버가 비밀번호를 바꾸기 전에 토큰을 소비한다(감사 H-01) — 같은 토큰으로는 다시 못 보낸다. 인증부터 다시
        setCodeSent(false);
        setCode('');
      }
      throw e;
    }
    navigate('/home', { replace: true });
  });

  const canSubmit = Boolean(token) && password.length >= 8; // 서버도 8자 미만이면 PASSWORD_TOO_SHORT 로 거부한다

  return (
    <ScreenLayout noPadding gap="none" className={auth.screen}>
      <BackButton className={auth.back} onClick={() => navigate(-1)} />
      <div className={cx(auth.body, styles.body)}>
        <div className={cx(auth.stack, styles.stack)}>
          <header className={auth.header}>
            <div className={auth.titleWrap}>
              <h1 className={cx('text-heading-l', auth.title)}>{COPY.title}</h1>
              <p className={cx('text-body-s', styles.description)}>{COPY.description}</p>
            </div>
          </header>

          <div className={cx(auth.form, styles.form)}>
            <PhoneField
              label={COPY.phoneLabel}
              value={phone}
              onChange={onPhoneChange}
              verify
              verifyActive={phone.length > 0 && !token}
              verifyLabel={token ? '인증완료' : resendIn > 0 ? mmss(resendIn) : codeSent ? '재전송' : '인증하기'}
              verifyDisabled={resendIn > 0}
              onVerify={() => sendCode.run()}
              disabled={Boolean(token)}
            />
            <ActionError error={notice ? { message: notice } : null}>
              {' '}
              <button type="button" className={cx('text-body-s', styles.link)} onClick={() => navigate('/signup')}>
                {COPY.signUpLink}
              </button>
            </ActionError>
            <ActionError error={sendCode.error} />

            {codeSent && !token && (
              <TextField
                label={COPY.codeLabel}
                placeholder={COPY.codePlaceholder}
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                value={code}
                onChange={onCodeChange}
                error={verify.error ? verify.error.message : undefined}
                success={verify.loading ? '확인 중…' : undefined}
              />
            )}

            {/* 새 비밀번호 칸: 인증이 끝나 토큰이 생긴 뒤에만 보인다 */}
            {token && (
              <TextField
                label={COPY.passwordLabel}
                placeholder={COPY.passwordPlaceholder}
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                hint={COPY.passwordHint}
                success={password.length >= 8 ? PASSWORD_OK_MESSAGE : undefined}
              />
            )}
          </div>
        </div>

        <div className={auth.cta}>
          <ActionError error={submit.error} fallback={COPY.failed} />
          <Button variant="primary" chevron={false} disabled={!canSubmit} loading={submit.loading} onClick={submit.run}>
            {COPY.submit}
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
