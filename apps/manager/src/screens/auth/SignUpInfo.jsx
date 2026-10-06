// Manager/Auth/SignUp/Info (코드에서 정함) — 회원가입 2/3 이름·휴대폰(인증)·비밀번호·비밀번호 확인. 고객용 Auth/ProfileInfo-Phone 의 필드 구성에 비밀번호 확인만 더했다.
// "다음" → /auth/signup/complete. 뒤로 → 이전 화면(약관)
// B2: "인증하기" 뒤에 인증번호 6자리 필드, 맞아야 "다음". "다음" 이 계정을 만든다(가입 중 로그인 상태)
// 순서: 번호 "인증하기"(requestPhoneVerification, 서버 POST /auth/phone/otp) → 6자리를 다 치면 바로 확인
// (verifyPhone: 가입 토큰을 초안에 저장) → 비밀번호 8자 이상·확인 일치 → "다음"(saveManagerSignUp: POST /auth/signup,
// 초안의 가입 토큰을 쓴다) → 완료 화면. 인증 부분의 짜임은 비밀번호 재설정 화면(ResetPassword)과 비슷하다.
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, NavBar, StepDots, TextField, PhoneField, Button, ScreenError, ScreenLoading, useAsync, useAction, cx, useCountdown, mmss, ActionError } from '@ansimclean/ui';
import { getAuthCopy, checkPassword, requestPhoneVerification, verifyPhone, saveManagerSignUp } from '../../api/manager.js';
import styles from './auth.module.css';

/**
 * 회원가입 2단계 화면 (/auth/signup/info). 이름·비밀번호는 이 화면 state 에만 두고 "다음" 때 서버로 보낸다.
 * 휴대폰 인증으로 받은 가입 토큰과 그 번호만 가입 초안(localStorage)에 남는다 (api/manager.js verifyPhone).
 * 토큰은 계정을 만들면(saveManagerSignUp) 초안에서 지워지고, 약관 동의는 완료 화면까지 남는다.
 */
export default function SignUpInfo() {
  const navigate = useNavigate();
  const { data: copy, error, reload } = useAsync(() => getAuthCopy('info'), []);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [passwordOk, setPasswordOk] = useState(false); // checkPassword 결과 — 8자 이상이면 true
  const [passwordMessage, setPasswordMessage] = useState(''); // 통과하면 '사용가능한 비밀번호입니다!', 아니면 빈 문자열
  const passwordCheckSeq = useRef(0); // 키 입력마다 검사하므로 늦게 온 옛 응답은 버린다
  const [confirm, setConfirm] = useState(''); // 비밀번호 확인 칸
  const [codeSent, setCodeSent] = useState(false); // 인증번호를 보냈다 → 인증번호 칸이 열리고 버튼 문구가 '재전송'
  const [resendAt, setResendAt] = useState(0); // 서버가 같은 번호 재요청을 막는 시각(60초) — 그동안 버튼은 카운트다운 (검증 검토)
  const resendIn = useCountdown(resendAt);
  const phoneRef = useRef(''); // 지금 입력란의 번호 (늦게 온 인증 응답 대조용)
  const [code, setCode] = useState('');
  const [verified, setVerified] = useState(false); // 휴대폰 인증 성공 (가입 토큰이 초안에 있다)
  const [phoneNotice, setPhoneNotice] = useState(''); // 이미 가입한 번호 안내 (아래에 '로그인하기' 버튼을 같이 보인다)
  // 6자리 확인: 서버가 맞다고 하면 verifyPhone 이 가입 토큰을 초안에 저장하고, 여기서 verified 를 켠다.
  // phone(state)은 이 렌더 때의 값이고 phoneRef 는 지금 입력란의 값이다 — 둘을 비교해 옛 번호의 응답을 가려낸다
  const verify = useAction(async (value) => {
    const requested = phone; // 응답이 오는 사이 번호를 고쳤으면 그 응답은 버린다 (보이는 번호와 다른 번호로 가입되지 않게)
    try {
      await verifyPhone(requested, value);
    } catch (e) {
      if (requested !== phoneRef.current) return; // 옛 번호의 실패는 새 입력란에 보이지 않게
      throw e;
    }
    if (requested !== phoneRef.current) return;
    setVerified(true);
  });
  // 확인 요청 중에 고친 6자리는 앞 요청이 끝난 뒤 한 번 더 확인한다 — useAction 은 진행 중 run 을 버려 '맞는 번호가 보이는데 틀렸다' 가 남았다 (검수 3차)
  const verifying = useRef(false); // 확인 요청이 진행 중인지
  const pendingCode = useRef(null); // 진행 중에 들어온 마지막 6자리 (앞 요청이 끝나면 이것으로 한 번 더)
  // "인증하기"/"재전송": 이전 인증 상태를 지우고 인증번호를 요청한다. 성공하면 인증번호 칸을 열고 재전송 대기(서버 resendAfter,
  // 없으면 60초)를 건다. 이미 가입한 번호면(reason 'taken') 안내만 보이고 인증번호 칸은 열지 않는다
  const sendCode = useAction(async () => {
    setPhoneNotice('');
    setVerified(false);
    setCode('');
    verify.reset(); // 이전 '인증번호가 맞지 않아요' 가 새 입력란에 남지 않게
    const res = await requestPhoneVerification(phone);
    if (res.sent) {
      setCodeSent(true);
      setResendAt(Date.now() + (res.resendAfter ?? 60) * 1000);
    }
    else if (res.reason === 'taken') setPhoneNotice(res.message ?? '이미 가입한 번호예요');
  });
  // "다음": 계정을 만든다 (saveManagerSignUp 이 세션을 저장 → 가입 중 로그인 상태). 성공하면 완료 화면으로.
  // 가입 토큰이 없거나 만료면(OTP_EXPIRED) 인증 입력을 다시 열고, 오류는 다시 던져 버튼 위에 문구를 보인다
  const next = useAction(async () => {
    try {
      await saveManagerSignUp({ name: name.trim(), phone, password });
    } catch (e) {
      if (e?.code === 'OTP_EXPIRED') {
        setVerified(false); // 인증 토큰(15분)이 지났다 — 번호 입력을 다시 열어 재인증한다
        setCodeSent(false);
        setCode('');
      }
      throw e;
    }
    navigate('/auth/signup/complete');
  });
  const back = () => navigate(-1);
  if (error) return <ScreenError error={error} onRetry={reload} navBar={<NavBar title="회원가입" onBack={back} />} />;
  if (!copy) return <ScreenLoading navBar={<NavBar title="회원가입" onBack={back} />} />;

  // 아래 함수들은 훅이 아니라서 early return 뒤에 두어도 된다.
  // 키 입력마다 비밀번호 규칙을 검사한다. 검사가 비동기라 순서 번호(seq)로 마지막 입력의 결과만 반영한다
  const onPasswordChange = async (e) => {
    const value = e.target.value;
    setPassword(value);
    const seq = ++passwordCheckSeq.current;
    const { ok, message } = await checkPassword(value);
    if (seq === passwordCheckSeq.current) {
      setPasswordOk(ok);
      setPasswordMessage(message);
    }
  };

  const mismatch = confirm.length > 0 && confirm !== password; // 확인 칸이 비어 있을 때는 오류를 보이지 않는다
  const canNext = name.trim() && verified && passwordOk && confirm === password; // "다음" 조건: 이름(공백 제외)·인증 완료·규칙 통과·확인 일치
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
  // 숫자만 남기고 6자리까지 자른다. 6자리가 되면 바로 확인 요청을 보낸다 (따로 확인 버튼이 없다)
  const onCodeChange = (e) => {
    const value = e.target.value.replace(/\D/g, '').slice(0, 6);
    setCode(value);
    if (value.length === 6) runVerify(value);
  };
  // 번호를 고치면 앞 번호의 인증은 무효 — 인증번호 칸·인증 완료·안내를 처음으로
  const onPhoneChange = (e) => {
    setPhone(e.target.value);
    phoneRef.current = e.target.value;
    setCodeSent(false);
    setVerified(false);
    setCode('');
    setPhoneNotice('');
    verify.reset();
  };

  return (
    <ScreenLayout
      navBar={<NavBar title="회원가입" onBack={back} />}
      stickyCta={
        <>
          <ActionError error={next.error} />
          <Button loading={next.loading} disabled={!canNext} onClick={next.run}>
            {copy.next}
          </Button>
        </>
      }
    >
      <StepDots step={2} total={3} className={styles.steps} />
      <h2 className={cx('text-heading-l', styles.title)}>{copy.title}</h2>

      <div className={styles.form}>
        <TextField label={copy.nameLabel} placeholder={copy.namePlaceholder} autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
        {/* 인증 버튼 문구: 인증 끝 → '인증완료', 재전송 대기 중 → 남은 m:ss, 보낸 적 있음 → '재전송', 처음 → '인증하기'.
            인증이 끝나면 번호 칸을 잠근다 */}
        <PhoneField
          label={copy.phoneLabel}
          value={phone}
          onChange={onPhoneChange}
          verify
          verifyActive={phone.length > 0 && !verified}
          verifyLabel={verified ? '인증완료' : resendIn > 0 ? mmss(resendIn) : codeSent ? '재전송' : '인증하기'}
          verifyDisabled={resendIn > 0}
          onVerify={() => sendCode.run()}
          disabled={verified}
        />
        {/* 이미 가입한 번호 안내 + '로그인하기' 버튼 (ActionError 의 children 은 문구 뒤에 붙는다) */}
        <ActionError error={phoneNotice ? { message: phoneNotice } : null}>
            {' '}            <button type="button" className={cx('text-body-s', styles.link)} onClick={() => navigate('/auth/login')}>
              로그인하기
            </button>
          </ActionError>
        <ActionError error={sendCode.error} />
        {/* 인증번호 칸: 보낸 뒤부터 인증에 성공하기 전까지만. 확인 요청 중에는 '확인 중…' */}
        {codeSent && !verified && (
          <TextField
            label="인증번호"
            placeholder="문자로 받은 6자리"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={onCodeChange}
            error={verify.error ? verify.error.message : undefined}
            success={verify.loading ? '확인 중…' : undefined}
          />
        )}
        {verified && <p className={cx('text-body-s', styles.verified)}>휴대폰 인증이 완료됐어요</p>}
        {/* 비밀번호: '8자 이상' 안내(hint)를 입력 전부터 보이고, 규칙을 통과하면 성공 문구(success)가 그 자리를 대신한다 */}
        <TextField label={copy.passwordLabel} placeholder={copy.passwordPlaceholder} type="password" autoComplete="new-password" value={password} onChange={onPasswordChange} hint={copy.passwordPlaceholder} success={passwordMessage} />
        <TextField
          label={copy.passwordConfirmLabel}
          placeholder={copy.passwordConfirmPlaceholder}
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={mismatch ? copy.passwordMismatch : undefined}
        />
      </div>
    </ScreenLayout>
  );
}
