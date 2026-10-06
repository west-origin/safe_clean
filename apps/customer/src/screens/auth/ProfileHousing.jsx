// Figma: Auth/ProfileHousing (79:311) — 393×1275 (긴 화면, 스크롤)
// 프로토타입 연결 없음 → "다음" 은 /auth/complete, 뒤로 = 이전 화면. 선택 카드 Selected 모양은 코드에서 정함 (PROGRESS.md 판단 항목)
// 고객 가입 4/4단계: 거주형태·평수·방/화장실 수·반려동물 입력 (/auth/profile-housing). 주소 화면(ProfileAddress) 다음.
// 선택지는 getHousingOptions(서버 없이 mock.js 의 HOUSING_TYPES·PET_OPTIONS)에서 받는다.
// "다음" → saveProfileHousing(PUT /customer/profile/housing) → 가입 완료 화면(/auth/complete).
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, BackButton, StepDots, ImageOption, NoteCard, TextField, Checkbox, Button, ASSETS, cx, useAsync, useAction, ScreenError, ScreenLoading, ActionError } from '@ansimclean/ui';
import { getHousingOptions, saveProfileHousing } from '../../api/customer.js';
import { HOUSING_INFO_NOTE } from '../../api/mock.js';
import auth from './auth.module.css';
import styles from './ProfileHousing.module.css';

/** 가입 중 토큰이 사라지면(401) 로그인으로 — 이 화면들은 RequireAuth 밖이라 세션 삭제를 스스로 봐야 한다 (코드에서 정함) */
const toLoginIfSignedOut = (navigate, e) => { // true 면 이미 이동했다 — 부른 쪽은 오류 문구 없이 끝낸다
  if (e?.status === 401) {
    navigate('/auth/login', { replace: true });
    return true;
  }
  return false;
};

/**
 * 거주형태 입력 화면. 모든 항목이 채워져야(평수는 입력 또는 '기억이 안나요') "다음" 이 켜진다 (아래 canNext).
 * "기억이 안나요" 를 체크하면 평수 칸을 끄고, 평수는 빈 값 + sizeUnknown 으로 보낸다.
 */
export default function ProfileHousing() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const method = params.get('method') ?? 'phone'; // 저장에 넘기지만 saveProfileHousing 은 method 를 보내지 않는다
  const { data: options, error, reload } = useAsync(() => getHousingOptions(), []);
  const [housingType, setHousingType] = useState('');
  const [size, setSize] = useState('');
  const [sizeUnknown, setSizeUnknown] = useState(false);
  const [rooms, setRooms] = useState('');
  const [bathrooms, setBathrooms] = useState('');
  const [pet, setPet] = useState('');


  // "다음": 저장이 성공한 뒤에만 완료 화면으로. 401 이면 로그인으로, 그 밖의 실패는 다시 던져 next.error 로 보인다
  const next = useAction(async () => {
    try {
      await saveProfileHousing({ method, housingType, size: sizeUnknown ? '' : size, sizeUnknown, rooms, bathrooms, pet });
    } catch (e) {
      if (toLoginIfSignedOut(navigate, e)) return;
      throw e;
    }
    navigate('/auth/complete');
  });

  // 새 훅은 이 early return 들보다 위에 둔다 (CLAUDE.md §10)
  if (error) return <ScreenError error={error} onRetry={reload} onBack={() => navigate(-1)} />;
  if (!options) return <ScreenLoading />;

  const canNext = housingType && (size || sizeUnknown) && rooms && bathrooms && pet; // 평수는 입력 또는 '기억이 안나요' 중 하나면 된다


  return (
    <ScreenLayout noPadding gap="none" className={auth.screen}>
      <BackButton className={auth.back} onClick={() => navigate(-1)} />
      <div className={cx(auth.body, styles.body)}>
        <div className={cx(auth.stack, styles.stack)}>
          <header className={auth.header}>
            <StepDots step={4} total={4} />
            <div className={auth.titleWrap}>
              <h1 className={cx('text-heading-l', auth.title)}>{'거주형태를 \n알려주세요!'}</h1>
            </div>
          </header>

          <section className={cx(styles.section, styles.sectionXs)} aria-labelledby="housing-type-label">
            <h2 id="housing-type-label" className={cx('text-title-card', styles.sectionLabel)}>
              거주형태
            </h2>
            <div className={styles.grid} role="radiogroup" aria-labelledby="housing-type-label">
              {/* t.icon 은 공용 UI ASSETS 의 키 (mock.js HOUSING_TYPES). 어느 카드를 골랐는지는 이 화면 state(housingType)가 든다 */}
              {options.housingTypes.map((t) => (
                <ImageOption key={t.id} image={ASSETS[t.icon]} label={t.label} selected={housingType === t.id} onSelect={() => setHousingType(t.id)} />
              ))}
            </div>
            <div className={styles.infoWrap}>
              <NoteCard tone="brand" icon={ASSETS.housingInfoIcon}>
                {HOUSING_INFO_NOTE}
              </NoteCard>
            </div>
          </section>

          <section className={cx(styles.section, styles.sectionSm)}>
            {/* Figma Input 은 높이 52 / 좌 패딩 11 (TextField 50 / 10). 공통 컴포넌트를 써서 1~2px 차이 (PROGRESS.md) */}
            <TextField label="몇 평인가요?" placeholder="평수를 입력해주세요." textStyle="labelM" type="number" inputMode="numeric" value={size} onChange={(e) => setSize(e.target.value)} disabled={sizeUnknown} />
            <div className={styles.checkRow}>
              <Checkbox label="기억이 안나요" checked={sizeUnknown} onChange={setSizeUnknown} reverse muted labelStyle="labelM" />
            </div>
          </section>

          <TextField label="화장실과 거실을 제외한 방이 몇 개인가요?" placeholder="여기에 입력해주세요." type="number" inputMode="numeric" value={rooms} onChange={(e) => setRooms(e.target.value)} />
          <TextField label="화장실은 몇 개인가요?" placeholder="여기에 입력해주세요." type="number" inputMode="numeric" value={bathrooms} onChange={(e) => setBathrooms(e.target.value)} />

          <section className={cx(styles.section, styles.sectionSm)} aria-labelledby="pet-label">
            <h2 id="pet-label" className={cx('text-title-card', styles.sectionLabel)}>
              반려동물 여부
            </h2>
            <div className={styles.grid} role="radiogroup" aria-labelledby="pet-label">
              {options.petOptions.map((p) => (
                <ImageOption key={p.id} image={ASSETS[p.icon]} label={p.label} selected={pet === p.id} onSelect={() => setPet(p.id)} size="lg" />
              ))}
            </div>
          </section>
        </div>

        <div className={auth.cta}>
          <ActionError error={next.error} fallback={'저장하지 못했어요. 다시 시도해주세요.'} />
          <Button variant="primary" loading={next.loading} disabled={!canNext} onClick={next.run}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
