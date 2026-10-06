// Figma: Auth/ProfileInfo-Phone (80:465) · Auth/ProfileInfo-Social (81:136)
// 두 프레임은 비밀번호 필드 유무만 다르다 → 한 화면(isPhone 으로 나눈다). 소셜 로그인은 결정(§13-1)대로 아직 없어 ?method 를 읽지 않고 항상 phone 형.
// 프로토타입 연결 없음 → "다음" 은 /auth/profile-address, 뒤로 = 이전 화면 (PROGRESS.md 판단 항목)
// 코드에서 정함(B2): "인증하기" 뒤에 인증번호 6자리 필드가 나타나고, 맞아야 "다음" 이 켜진다. "다음" 이 계정을 만든다(가입 중 로그인 상태).
// 고객 가입 2/4단계 (/auth/profile-info). 약관 동의(TermsConsent) 다음 화면.
// 흐름: 번호 입력 → "인증하기"(requestPhoneVerification, 문자 발송) → 6자리를 채우면 바로 확인(verifyPhone, 토큰은 가입 초안에 저장)
//   → "다음"(saveProfileInfo = POST /auth/signup) → 주소 화면(/auth/profile-address).
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, BackButton, StepDots, TextField, PhoneField, Button, cx, useAction, useCountdown, mmss, ActionError } from '@ansimclean/ui';
import { checkPassword, requestPhoneVerification, verifyPhone, saveProfileInfo } from '../../api/customer.js';
import { PASSWORD_HINT } from '../../api/mock.js';
import auth from './auth.module.css';
import styles from './ProfileInfo.module.css';

/**
 * 기본정보 입력 + 휴대폰 인증 화면. 입력값과 인증 진행(보냈는지·확인됐는지)은 이 화면 state 에 있고,
 * 확인으로 받은 인증 토큰은 가입 초안(verifyPhone 이 저장)에 있다. 번호를 고치면 인증을 처음부터 다시 한다.
 */
export default function ProfileInfo() {
  const navigate = useNavigate();
  const method = 'phone'; // 소셜 가입은 아직 없다(§13-1). ?method 를 믿으면 모르는 값에서 비밀번호 없는 가입 시도가 된다 — 항상 phone
  const isPhone = method === 'phone';

  const [name, setName] = useState('');
  const [nickname, setNickname] = useState('');
  const [password, setPassword] = useState('');
  const [passwordMessage, setPasswordMessage] = useState('');
  const passwordCheckSeq = useRef(0); // 키 입력마다 검사하므로 늦게 온 옛 응답은 버린다
  const [phone, setPhone] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [resendAt, setResendAt] = useState(0); // 서버가 같은 번호 재요청을 막는 시각(60초) — 그동안 버튼은 카운트다운 (검증 검토)
  const resendIn = useCountdown(resendAt);
  const [code, setCode] = useState('');
  const [verified, setVerified] = useState(false);
  const [phoneNotice, setPhoneNotice] = useState(''); // "이미 가입한 번호" 안내
  const phoneRef = useRef(''); // 지금 입력란의 번호 — 응답이 오는 사이 번호를 고쳤으면 그 응답(다른 번호의 인증)은 버린다

  // 6자리가 채워지면 바로 확인
  const verify = useAction(async (value) => {
    const requested = phone;
    try {
      await verifyPhone(requested, value);
    } catch (e) {
      if (requested !== phoneRef.current) return; // 옛 번호의 실패는 새 입력란에 보이지 않게
      throw e;
    }
    if (requested !== phoneRef.current) return; // 보이는 번호와 다른 번호로 가입되지 않게
    setVerified(true);
  });

  // 인증번호 요청 → 필드 표시. 이미 가입한 번호면 로그인 안내
  // 확인 요청 중에 고친 6자리는 앞 요청이 끝난 뒤 한 번 더 확인한다 — useAction 은 진행 중 run 을 버려 '맞는 번호가 보이는데 틀렸다' 가 남았다 (검수 3차)
  const verifying = useRef(false);
  const pendingCode = useRef(null);
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
  // 확인 요청을 한 번에 하나만 보낸다. 진행 중에 들어온 값은 pendingCode 에 마지막 것만 남겨 두고,
  // 앞 요청이 끝나면 그 값이 앞 값과 다를 때만 한 번 더 확인한다
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
  // 인증번호 입력: 숫자가 아닌 글자는 지우고 6자리까지만 남긴다. 6자리가 되면 버튼 없이 바로 확인한다
  const onCodeChange = (e) => {
    const value = e.target.value.replace(/\D/g, '').slice(0, 6);
    setCode(value);
    if (value.length === 6) runVerify(value);
  };
  // 번호를 고치면 인증을 처음부터: 보낸 인증번호 칸·인증 완료·안내·실패 문구를 모두 지운다
  const onPhoneChange = (e) => {
    setPhone(e.target.value);
    phoneRef.current = e.target.value;
    setCodeSent(false);
    setVerified(false);
    setCode('');
    setPhoneNotice('');
    verify.reset();
  };

  // 비밀번호 입력마다 checkPassword 로 '사용 가능' 문구를 받는다. 가장 최근 입력의 결과만 반영한다
  const onPasswordChange = async (e) => {
    const value = e.target.value;
    setPassword(value);
    const seq = ++passwordCheckSeq.current;
    const { message } = await checkPassword(value);
    if (seq === passwordCheckSeq.current) setPasswordMessage(message);
  };

  const canNext = name && nickname && verified && (!isPhone || password.length >= 8); // 비밀번호 8자는 서버도 다시 검사한다

  // "다음": 계정을 만든다. saveProfileInfo 는 name·nickname·password 만 쓴다 — 번호는 가입 초안의 인증 토큰으로 서버가 안다
  const next = useAction(async () => {
    try {
      await saveProfileInfo({ method, name, nickname, ...(isPhone ? { password } : {}), phone });
    } catch (e) {
      if (e?.code === 'OTP_EXPIRED') {
        setVerified(false); // 인증 토큰(15분)이 지났다 — 번호 입력을 다시 열어 재인증한다
        setCodeSent(false);
        setCode('');
      }
      throw e;
    }
    navigate(`/auth/profile-address?method=${method}`);
  });

  return (
    <ScreenLayout noPadding gap="none" className={auth.screen}>
      <BackButton className={auth.back} onClick={() => navigate(-1)} />
      <div className={cx(auth.body, styles.body)}>
        <div className={cx(auth.stack, styles.stack)}>
          <header className={auth.header}>
            <StepDots step={2} total={4} />
            <div className={auth.titleWrap}>
              <h1 className={cx('text-heading-l', auth.title)}>{'기본정보를 \n입력해주세요'}</h1>
            </div>
          </header>

          <div className={cx(auth.form, isPhone ? styles.formPhone : styles.formSocial)}>
            <TextField label="이름" placeholder="이름을 입력해주세요" autoComplete="name" value={name} onChange={(e) => setName(e.target.value)} />
            <TextField label="닉네임" placeholder="닉네임을 입력해주세요" autoComplete="nickname" value={nickname} onChange={(e) => setNickname(e.target.value)} />
            {isPhone && (
              <TextField label="비밀번호" placeholder="비밀번호를 입력해주세요" type="password" autoComplete="new-password" value={password} onChange={onPasswordChange} hint={PASSWORD_HINT} success={passwordMessage} />
            )}
            <PhoneField
              label="휴대폰 번호"
              value={phone}
              onChange={onPhoneChange}
              verify
              verifyActive={phone.length > 0 && !verified}
              verifyLabel={verified ? '인증완료' : resendIn > 0 ? mmss(resendIn) : codeSent ? '재전송' : '인증하기'}
              verifyDisabled={resendIn > 0}
              onVerify={() => sendCode.run()}
              disabled={verified}
            />
            {/* 이미 가입한 번호: 서버 문구 뒤에 "로그인하기" 링크를 붙인다 */}
            <ActionError error={phoneNotice ? { message: phoneNotice } : null}>
                {' '}                <button type="button" className={cx('text-body-s', styles.link)} onClick={() => navigate('/auth/login')}>
                  로그인하기
                </button>
              </ActionError>
            <ActionError error={sendCode.error} />
            {/* 인증번호 칸: 문자를 보낸 뒤부터 인증이 끝나기 전까지만 보인다. 틀리면 서버 문구(verify.error)를 칸 아래에 */}
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
            {verified && <p className={cx('text-body-s', styles.verifiedNote)}>휴대폰 인증이 완료됐어요</p>}
          </div>
        </div>

        <div className={auth.cta}>
          <ActionError error={next.error} />
          <Button variant="primary" disabled={!canNext} loading={next.loading} onClick={next.run}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
