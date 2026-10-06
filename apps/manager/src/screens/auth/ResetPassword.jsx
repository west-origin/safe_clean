// Manager/Auth/ResetPassword (코드에서 정함 — 사용성 검토 2026-09-22: 비밀번호를 잊으면 들어올 길이 없었다)
// 로그인 화면 "비밀번호를 잊으셨나요?" → 이 화면. 휴대폰 인증(/auth/phone/verify) → 새 비밀번호 → 바로 로그인 상태 → /
// 문구는 api/mock.js 의 AUTH_COPY.reset.
// 단계: ① 번호 → "인증하기"(requestPasswordReset, 서버 POST /auth/password/otp — 가입된 매니저 번호만)
// ② 6자리를 다 치면 바로 확인(verifyPasswordReset → 재설정 토큰) ③ 새 비밀번호 → "비밀번호 변경하기"(resetPassword,
// POST /auth/password/reset — 성공하면 세션 저장). 토큰을 받으면 번호 칸은 잠기고 새 비밀번호 칸이 열린다.
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, PhoneField, TextField, Button, ScreenError, ScreenLoading, ActionError, useAsync, useAction, useCountdown, mmss, cx } from '@ansimclean/ui';
import { getAuthCopy, requestPasswordReset, verifyPasswordReset, resetPassword } from '../../api/manager.js';
import styles from './auth.module.css';

const PASSWORD_MIN = 8; // 버튼을 켜는 최소 길이. 서버도 8자 미만이면 거부한다 (AuthService PASSWORD_MIN)

/**
 * 비밀번호 재설정 화면 (/auth/reset-password, 로그인 없이 열리는 공개 화면).
 * 재설정 토큰은 이 화면 state 에만 두고 가입 초안(localStorage)과 섞지 않는다 (api/manager.js verifyPasswordReset).
 */
export default function ResetPassword() {
  const navigate = useNavigate();
  const { data: copy, error, reload } = useAsync(() => getAuthCopy('reset'), []);
  const [phone, setPhone] = useState('');
  const [codeSent, setCodeSent] = useState(false); // 인증번호를 보냈다 → 인증번호 칸이 열리고 버튼 문구가 '재전송'
  const [resendAt, setResendAt] = useState(0); // 재전송을 다시 누를 수 있는 시각(ms). 서버가 60초 안의 재요청을 막는다
  const resendIn = useCountdown(resendAt); // 그때까지 남은 초 (1초마다 다시 그린다). 0 보다 크면 버튼에 m:ss
  const [code, setCode] = useState('');
  const [token, setToken] = useState(''); // 인증에 성공하면 받는 재설정 토큰(서버 유효 15분). 있으면 새 비밀번호 칸이 열린다
  const [password, setPassword] = useState('');
  const [notice, setNotice] = useState(''); // '가입되지 않은 번호' 안내 문구
  const phoneRef = useRef(''); // 응답이 오는 사이 번호를 고쳤으면 그 응답은 버린다

  // 6자리 확인: 번호+인증번호를 보내 재설정 토큰을 받는다. phone(state)은 이 렌더 때의 값이라
  // 응답이 온 뒤의 최신 입력은 phoneRef 로 본다 — 둘이 다르면 그 사이 번호를 고친 것이라 토큰을 버린다
  const verify = useAction(async (value) => {
    const requested = phone;
    const res = await verifyPasswordReset(requested, value);
    if (requested !== phoneRef.current) return;
    setToken(res.verificationToken);
  });

  // 확인 요청 중에 고친 6자리는 앞 요청이 끝난 뒤 한 번 더 확인한다 — useAction 은 진행 중 run 을 버려 '맞는 번호가 보이는데 틀렸다' 가 남았다 (자체 점검 #37, 가입 화면과 같다)
  const verifying = useRef(false); // 확인 요청이 진행 중인지
  const pendingCode = useRef(null); // 진행 중에 들어온 마지막 6자리 (앞 요청이 끝나면 이것으로 한 번 더)
  // 6자리 확인을 한 번에 하나씩 보낸다. 진행 중이면 값만 기억하고, 끝난 뒤 기억한 값이 다르면 다시 확인한다
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
    if (next && next !== value) runVerify(next); // 방금 확인한 값과 같으면 다시 묻지 않는다
  };

  // "인증하기"/"재전송": 이전 인증 상태를 모두 지우고 인증번호를 요청한다. 성공하면 인증번호 칸을 열고
  // 재전송 대기(서버 resendAfter, 없으면 60초)를 건다. 가입되지 않은 번호면 안내만 보인다
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
    } else if (res.reason === 'notFound') setNotice(res.message ?? copy.notFound);
  });

  // "비밀번호 변경하기": 성공하면 resetPassword 가 세션을 저장하므로 바로 홈(/)으로 간다.
  // 실패: PASSWORD_CHANGED → 로그인 화면에 안내를 넘긴다, OTP_EXPIRED → 인증 단계로 되돌린다.
  // PASSWORD_CHANGED 말고는 오류를 다시 던져 submit.error 로 입력 칸 아래(ActionError)에 보인다
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
    navigate('/', { replace: true });
  });

  // 번호를 고치면 앞 번호로 받은 인증은 무효 — 인증번호 칸·토큰·안내·확인 대기를 모두 처음으로
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

  // 숫자만 남기고 6자리까지 자른다. 6자리가 되면 바로 확인 요청을 보낸다 (따로 확인 버튼이 없다)
  const onCodeChange = (e) => {
    const value = e.target.value.replace(/\D/g, '').slice(0, 6);
    setCode(value);
    if (value.length === 6) runVerify(value);
  };

  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!copy) return <ScreenLoading />;

  return (
    <ScreenLayout
      topPadding="lg"
      stickyCta={
        <Button chevron={false} disabled={!token || password.length < PASSWORD_MIN} loading={submit.loading} onClick={submit.run}>
          {copy.submit}
        </Button>
      }
    >
      <header className={styles.header}>
        <h1 className={cx('text-heading-l', styles.title)}>{copy.title}</h1>
        <p className={cx('text-body-s', styles.secondary)}>{copy.description}</p>
      </header>

      <div className={styles.form}>
        {/* 인증 버튼 문구: 인증 끝 → '인증완료', 재전송 대기 중 → 남은 m:ss, 보낸 적 있음 → '재전송', 처음 → '인증하기'.
            토큰을 받으면 번호 칸을 잠근다 */}
        <PhoneField
          label={copy.phoneLabel}
          value={phone}
          onChange={onPhoneChange}
          verify
          verifyActive={phone.length > 0 && !token}
          verifyLabel={token ? '인증완료' : resendIn > 0 ? mmss(resendIn) : codeSent ? '재전송' : '인증하기'}
          verifyDisabled={resendIn > 0}
          onVerify={() => sendCode.run()}
          disabled={Boolean(token)}
        />
        {/* 가입되지 않은 번호 안내, 그리고 인증번호 요청이 실패했을 때의 문구 */}
        <ActionError error={notice ? { message: notice } : null} />
        <ActionError error={sendCode.error} />

        {/* 인증번호 칸: 보낸 뒤부터 인증에 성공하기 전까지만. 확인 요청 중에는 '확인 중…' */}
        {codeSent && !token && (
          <TextField
            label={copy.codeLabel}
            placeholder={copy.codePlaceholder}
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={onCodeChange}
            error={verify.error ? verify.error.message : undefined}
            success={verify.loading ? '확인 중…' : undefined}
          />
        )}

        {/* 새 비밀번호 칸: 인증에 성공해 토큰이 있을 때만 */}
        {token && (
          <TextField
            label={copy.passwordLabel}
            placeholder={copy.passwordPlaceholder}
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            hint={copy.passwordPlaceholder}
          />
        )}
        <ActionError error={submit.error} fallback={copy.failed} />
      </div>

      <p className={cx('text-body-s', styles.prompt)}>
        {copy.signUpPrompt}
        <button type="button" className={cx('text-title-small', styles.link)} onClick={() => navigate('/auth/signup')}>
          {copy.signUpLink}
        </button>
      </p>
    </ScreenLayout>
  );
}
