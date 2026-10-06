// Figma: Onboarding/Slide1 (67:678) · Slide2 (67:667) · Slide3 (68:1052) — 480×852 full-bleed 이미지(문구 포함) 3장
// 사용자 결정(2026-09-21): 세 장을 넘겨 보는 캐러셀이 아니라 **접속할 때마다 한 장을 무작위로** 보여준다. 탭하면 가입 화면 (버튼·프로토타입 연결 없음 — 코드에서 정함)
// '/' 경로에서 Start.jsx 가 로그인·가입 중이 아닌 사용자에게 이 화면을 그린다. 화면 전체가 버튼 하나다.
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, ASSETS } from '@ansimclean/ui';
import { pickSlide } from './pickSlide.js';
import styles from './Onboarding.module.css';

// 장마다의 대체 문구 — 문구가 그림 안에 있어 낭독기용 글자를 따로 둔다. ASSETS.onboardingSlides 와 같은 순번으로 짝짓고, 버튼 이름으로 읽힌다
const SLIDE_ALTS = ['내가 없는 순간에도, 보이니까 더 안심되는 청소', '보이지 않는 곳까지 안심하는 깨끗함', '소중한 사람에게 깨끗함을 선물하세요'];

/**
 * 온보딩 슬라이드 한 장. 누르면 /signup 으로 이동한다.
 * @param {object} props
 * @param {number} [props.slideIndex] 보여 줄 장 번호 — 테스트에서 고정할 때만 넘긴다. 없으면 무작위(pickSlide)
 */
export default function Onboarding({ slideIndex }) {
  const navigate = useNavigate();
  const slides = ASSETS.onboardingSlides;
  const [index] = useState(() => (slideIndex ?? pickSlide(slides.length)) % slides.length); // 화면이 떠 있는 동안은 바뀌지 않는다
  const slide = slides[index];

  // statusBar={false}: 상태바 여백 없이 그림이 화면 맨 위까지 깔린다. 그림은 장식(alt="")이고 뜻은 버튼 aria-label 이 전한다
  return (
    <ScreenLayout statusBar={false} noPadding gap="none" className={styles.screen}>
      <button type="button" className={styles.slide} aria-label={`${SLIDE_ALTS[index]}. 탭하면 시작`} onClick={() => navigate('/signup')}>
        <img src={slide.src} srcSet={slide.srcSet} width={slide.width} height={slide.height} alt="" draggable="false" />
      </button>
    </ScreenLayout>
  );
}
