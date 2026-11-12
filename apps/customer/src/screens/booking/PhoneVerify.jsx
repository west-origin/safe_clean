// (코드에서 정함) 첫 1회 청소 휴대폰 인증 — Figma 에 없다. 2026-11-10 사용자 첨부 시안 2장(로고·앱 이름은 빼고, 버튼은 앱의 파란 버튼).
// 오는 곳: 서비스 주기 화면(BookingFrequency)에서 1회를 고르면 뜨는 안내 시트(CustomerPhoneProof)의 '휴대폰 인증하기'.
// 1단계 '휴대폰 번호 인증': 번호 입력 → '인증번호 보내기'(requestCustomerPhoneProof → 서버 → Supabase 가 6자리 생성·발송).
//   **전송이 성공했을 때만** 2단계로 넘어간다. 실패하면 1단계에 오류 문구.
// 2단계 '인증번호를 입력해주세요': 6칸(OtpCodeField) — 6자리를 다 치면 자동으로 확인하고, '인증하고 계속하기' 도 그때 켜진다.
//   남은 시간 3분(서버 expiresIn = Supabase sms_otp_exp 180초), '인증번호 다시 받기'(60초 뒤), '휴대폰 번호 수정하기'(1단계로).
// 성공하면 주기 화면으로 돌아간다(자격을 다시 불러 '다음' 이 열린다). 문구는 mock.js PHONE_VERIFY_COPY.
import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Phone, ShieldCheck } from 'lucide-react';
import { ScreenLayout, NavBar, TextField, OtpCodeField, InfoNote, Button, ActionError, useAction, useCountdown, showToast, cx } from '@ansimclean/ui';
import { requestCustomerPhoneProof, verifyCustomerPhoneProof } from '../../api/customer.js';
import { PHONE_VERIFY_COPY as COPY } from '../../api/mock.js';
import common from './booking.module.css';
import styles from './PhoneVerify.module.css';

/** 숫자만 남겨 '010-1234-5678' 꼴로 (11자리까지) */
const formatPhone = (raw) => {
  const d = String(raw ?? '').replace(/\D/g, '').slice(0, 11);
  if (d.length <= 3) return d;
  if (d.length <= 7) return `${d.slice(0, 3)}-${d.slice(3)}`;
  return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`;
};
/** 남은 초 → '02:58' */
const mmss2 = (sec) => `${String(Math.floor(sec / 60)).padStart(2, '0')}:${String(sec % 60).padStart(2, '0')}`;

/** 휴대폰 인증 화면 (/booking/:category/phone-verify) */
export default function PhoneVerify() {
  const navigate = useNavigate();
  const { category } = useParams();
  const [step, setStep] = useState('phone'); // 'phone' | 'code'
  const [phone, setPhone] = useState(''); // 화면에 보이는 '010-1234-5678'
  const [code, setCode] = useState('');
  const [resendAt, setResendAt] = useState(0); // 다시 받기 가능 시각(ms)
  const [expiresAt, setExpiresAt] = useState(0); // 인증번호 만료 시각(ms)
  const resendIn = useCountdown(resendAt);
  const expiresIn = useCountdown(expiresAt);
  const tried = useRef(''); // 자동 확인을 이미 보낸 6자리 — 같은 값으로 두 번 보내지 않게
  const digits = phone.replace(/\D/g, '');
  const validPhone = /^010\d{8}$/.test(digits);

  const finish = () => {
    showToast(COPY.done);
    navigate(`/booking/${category}/frequency`, { replace: true });
  };

  // 확인: 5번 틀리거나 만료되면 서버가 인증번호를 버리므로 '다시 받기' 만 남긴다
  const verify = useAction(async (value) => {
    try {
      await verifyCustomerPhoneProof(digits, value);
    } catch (e) {
      if (e?.code === 'OTP_EXPIRED' || /너무 여러 번/.test(e?.message ?? '')) setExpiresAt(0);
      throw e;
    }
    finish();
  });
  // 보내기·다시 받기. 성공했을 때만 2단계로 간다 (실패하면 그 단계에 오류 문구)
  const send = useAction(async () => {
    const res = await requestCustomerPhoneProof(digits);
    if (res?.verified) return finish(); // 이미 확인된 번호(이전 시도의 마무리) — 문자 없이 끝
    verify.reset();
    setCode('');
    tried.current = '';
    setResendAt(Date.now() + (res?.resendAfter ?? 60) * 1000);
    setExpiresAt(Date.now() + (res?.expiresIn ?? 180) * 1000);
    setStep('code');
  });

  const expired = step === 'code' && expiresIn === 0;
  const onCodeChange = (value) => {
    setCode(value);
    verify.reset();
    // 6자리가 되면 바로 확인 (같은 값은 한 번만)
    if (value.length === 6 && !expired && tried.current !== value && !verify.loading) {
      tried.current = value;
      verify.run(value);
    }
  };
  const editPhone = () => {
    setStep('phone');
    setCode('');
    send.reset();
    verify.reset();
  };

  if (step === 'phone') {
    return (
      <ScreenLayout navBar={<NavBar onBack={() => navigate(-1)} />}>
        <header className={styles.header}>
          <h1 className={cx('text-heading-m', styles.title)}>{COPY.phoneTitle}</h1>
          <p className={cx('text-body-l', styles.description)}>{COPY.phoneDescription}</p>
        </header>
        <div className={styles.form}>
          <TextField
            label={COPY.phoneLabel}
            placeholder={COPY.phonePlaceholder}
            type="tel"
            inputMode="numeric"
            autoComplete="tel-national"
            icon={<Phone size={20} strokeWidth={2} />}
            value={phone}
            onChange={(e) => { setPhone(formatPhone(e.target.value)); send.reset(); }}
          />
          <ActionError error={send.error} />
          <Button variant="primary" chevron={false} className={common.ctaRounded} loading={send.loading} disabled={!validPhone} onClick={send.run}>
            {COPY.send}
          </Button>
        </div>
        <InfoNote tone="neutral" layout="inline" icon={<ShieldCheck size={20} strokeWidth={2} />} className={styles.privacy}>
          {COPY.privacy.before}<strong>{COPY.privacy.strong}</strong>{COPY.privacy.after}
        </InfoNote>
      </ScreenLayout>
    );
  }

  return (
    <ScreenLayout navBar={<NavBar onBack={editPhone} />}>
      <header className={styles.header}>
        <h1 className={cx('text-heading-m', styles.title)}>{COPY.codeTitle}</h1>
        <p className={cx('text-body-l', styles.description)}>{COPY.sentTo(phone)}</p>
      </header>
      <div className={styles.form}>
        <OtpCodeField
          label={COPY.codeLabel}
          value={code}
          onChange={onCodeChange}
          autoFocus
          error={verify.error?.message ?? (expired ? COPY.expired : undefined)}
          timer={mmss2(expiresIn)}
        />
        <div className={styles.actions}>
          <Button variant="primary" chevron={false} className={common.ctaRounded}
            loading={verify.loading} disabled={expired || code.length !== 6} onClick={() => verify.run(code)}>
            {COPY.submit}
          </Button>
          <ActionError error={send.error} />
          <Button variant="outline" chevron={false} className={common.ctaRounded}
            loading={send.loading} disabled={resendIn > 0} onClick={send.run}>
            {COPY.resend}
          </Button>
          <button type="button" className={cx('text-body-default', styles.link)} onClick={editPhone}>
            {COPY.editPhone}
          </button>
        </div>
      </div>
    </ScreenLayout>
  );
}
