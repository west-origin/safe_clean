// Figma: Auth/ProfileAddress (84:202)
// 프로토타입 연결 없음 → "다음" 은 /auth/profile-housing, 뒤로 = 이전 화면 (PROGRESS.md 판단 항목)
// 고객 가입 3/4단계: 기본 주소(도로명주소 + 상세주소) 입력 (/auth/profile-address).
// 기본정보(ProfileInfo) 다음 화면이고, 가입이 덜 끝난 계정(signupStep 'address')을 로그인·RequireAuth 가 여기로 보내기도 한다.
// "다음" → saveProfileAddress(PUT /customer/profile/address) → 거주형태 화면(ProfileHousing).
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, BackButton, StepDots, SearchField, TextField, Button, cx, useAction, ActionError } from '@ansimclean/ui';
import { saveProfileAddress } from '../../api/customer.js';
import { ADDRESS_PRIVACY_NOTE, ADDRESS_SEARCH_COPY } from '../../api/mock.js';
import auth from './auth.module.css';
import styles from './ProfileAddress.module.css';

/** 가입 중 토큰이 사라지면(401) 로그인으로 — 이 화면들은 RequireAuth 밖이라 세션 삭제를 스스로 봐야 한다 (코드에서 정함) */
const toLoginIfSignedOut = (navigate, e) => { // true 면 이미 이동했다 — 부른 쪽은 오류 문구 없이 끝낸다
  if (e?.status === 401) {
    navigate('/auth/login', { replace: true });
    return true;
  }
  return false;
};

/** 주소 입력 화면. 두 칸 모두 공백 아닌 글자가 있어야 "다음" 이 켜지고, 앞뒤 공백을 떼어 저장한다. */
export default function ProfileAddress() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const method = params.get('method') ?? 'phone'; // 다음 화면 주소에 이어 붙인다. saveProfileAddress 는 method 를 보내지 않는다
  const [roadAddress, setRoadAddress] = useState('');
  const [detailAddress, setDetailAddress] = useState('');

  // "다음": 저장이 성공한 뒤에만 이동한다. 401 이면 로그인으로, 그 밖의 실패는 다시 던져 next.error(아래 ActionError)로 보인다
  const next = useAction(async () => {
    try {
      await saveProfileAddress({ method, roadAddress: roadAddress.trim(), detailAddress: detailAddress.trim() });
    } catch (e) {
      if (toLoginIfSignedOut(navigate, e)) return;
      throw e;
    }
    navigate(`/auth/profile-housing?method=${method}`);
  });

  return (
    <ScreenLayout noPadding gap="none" className={auth.screen}>
      <BackButton className={auth.back} onClick={() => navigate(-1)} />
      <div className={cx(auth.body, styles.body)}>
        <div className={cx(auth.stack, styles.stack)}>
          <header className={auth.header}>
            <StepDots step={3} total={4} />
            <div className={auth.titleWrap}>
              <h1 className={cx('text-heading-l', auth.title)}>{'주소를 \n입력해주세요'}</h1>
            </div>
          </header>

          <div className={styles.formBlock}>
            <div className={cx(auth.form, styles.fields)}>
              {/* TODO(figma): 도로명주소 검색(우편번호 API) 화면이 인증 플로우에 없음 → 일반 입력.
                  돋보기 아이콘 때문에 검색이 되는 줄 알았다 → 직접 입력임을 한 줄로 (사용성 검토 2026-09-22) */}
              <SearchField label="도로명주소" placeholder="도로명주소를 입력해주세요." autoComplete="street-address" value={roadAddress} onChange={(e) => setRoadAddress(e.target.value)} />
              <p className={cx('text-body-s', styles.hint)}>{ADDRESS_SEARCH_COPY.guide}</p>
              <TextField label="상세주소" placeholder="상세주소를 입력해주세요." value={detailAddress} onChange={(e) => setDetailAddress(e.target.value)} />
            </div>
            <p className={cx('text-label-s', styles.note)}>{ADDRESS_PRIVACY_NOTE}</p>
          </div>
        </div>

        <div className={auth.cta}>
          <ActionError error={next.error} fallback={'저장하지 못했어요. 다시 시도해주세요.'} />
          <Button variant="primary" loading={next.loading} disabled={!roadAddress.trim() || !detailAddress.trim()} onClick={next.run}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
