import { useEffect, useRef, useState } from 'react';
import { Sheet, cx } from '@ansimclean/ui';
// TODO(asset): 임시 배너 이미지 (기존 3D 에셋 + 문구를 Edge 로 렌더링한 PNG). 디자인 배너가 오면 WebP 로 교체
import couponBanner from '@ansimclean/ui/assets/home-banner-coupon.png';
import dailyBanner from '@ansimclean/ui/assets/home-banner-daily.png';
import giftBanner from '@ansimclean/ui/assets/home-banner-gift.png';
import styles from './HomePromotions.module.css';
import HomePromotionCard from './HomePromotionCard.jsx';

const BANNERS = [
  {
    title: '첫 청소 15,000원 쿠폰팩',
    description: '즉시 지급 · 신규 고객 웰컴',
    tone: 'coupon',
    image: couponBanner,
  },
  {
    title: '청소는 맡기고, 일상은 여유롭게',
    description: '우리 집에 맞는 청소를 만나보세요',
    tone: 'daily',
    image: dailyBanner,
  },
  {
    title: '소중한 사람에게 깨끗한 하루',
    description: '깨끗함을 넘어, 안심을 전해요',
    tone: 'gift',
    image: giftBanner,
  },
];
const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** 5초마다 이동. 마지막 뒤에 첫 장을 복제해 한 방향으로 끊김 없이 순환한다. */
export default function HomePromotions() {
  const [position, setPosition] = useState(0);
  const [resetting, setResetting] = useState(false);
  const [paused, setPaused] = useState(false);
  const [eventsOpen, setEventsOpen] = useState(false);
  const [interacting, setInteracting] = useState(false);
  const [hidden, setHidden] = useState(document.hidden);
  const startX = useRef(null);
  const index = position % BANNERS.length;
  useEffect(() => {
    const visibility = () => setHidden(document.hidden);
    document.addEventListener('visibilitychange', visibility);
    return () => document.removeEventListener('visibilitychange', visibility);
  }, []);
  useEffect(() => {
    if (paused || interacting || eventsOpen || hidden || reducedMotion()) return;
    const timer = setTimeout(() => setPosition((p) => (p + 1) % (BANNERS.length + 1)), 5000);
    return () => clearTimeout(timer);
  }, [position, paused, interacting, eventsOpen, hidden]);
  useEffect(() => {
    if (!resetting) return;
    // 한 프레임은 전환 없이 첫 장을 그린 뒤 애니메이션을 다시 켠다.
    let frame = requestAnimationFrame(() => {
      frame = requestAnimationFrame(() => setResetting(false));
    });
    return () => cancelAnimationFrame(frame);
  }, [resetting]);
  const settle = () => {
    if (position !== BANNERS.length) return;
    setResetting(true);
    setPosition(0);
  };
  const move = (delta) => {
    setResetting(false);
    setPosition((p) => ((p % BANNERS.length) + delta + BANNERS.length) % BANNERS.length);
  };
  return (
    <section className={styles.carousel} aria-label="안심클린 혜택" aria-roledescription="carousel">
      <div
        className={styles.viewport}
        role="group"
        aria-label="혜택 배너 (좌우로 밀거나 화살표 키로 넘기기)"
        tabIndex={0}
        onMouseEnter={() => setInteracting(true)}
        onMouseLeave={() => setInteracting(false)}
        onFocus={() => setInteracting(true)}
        onBlur={() => setInteracting(false)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
            e.preventDefault();
            move(e.key === 'ArrowRight' ? 1 : -1);
          }
        }}
        onPointerDown={(e) => {
          startX.current = e.clientX;
          setInteracting(true);
          e.currentTarget.setPointerCapture?.(e.pointerId);
        }}
        onPointerUp={(e) => {
          if (startX.current !== null && Math.abs(e.clientX - startX.current) > 40)
            move(e.clientX < startX.current ? 1 : -1);
          startX.current = null;
          setInteracting(false);
        }}
        onPointerCancel={() => {
          startX.current = null;
          setInteracting(false);
        }}
      >
        <div
          className={cx(styles.track, resetting && styles.reset)}
          style={{ transform: `translateX(-${position * 100}%)` }}
          onTransitionEnd={settle}
        >
          {[...BANNERS, BANNERS[0]].map((banner, i) => (
            <div
              key={i}
              className={styles.slide}
              aria-hidden={i !== position}
              aria-roledescription="slide"
              aria-label={`${(i % BANNERS.length) + 1} / ${BANNERS.length}`}
            >
              <HomePromotionCard banner={banner} />
            </div>
          ))}
        </div>
        <div className={styles.controls}>
          <button
            type="button"
            className={styles.counter}
            aria-label={`진행 중인 이벤트 전체 보기, ${index + 1} / ${BANNERS.length}`}
            aria-haspopup="dialog"
            aria-expanded={eventsOpen}
            onClick={() => setEventsOpen(true)}
          >
            <span className="text-caption-l-bold">
              {index + 1}/{BANNERS.length}
            </span>
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              aria-hidden="true"
              focusable="false"
            >
              <circle cx="12" cy="12" r="11" fill="currentColor" />
              <path
                d="M7 12h10M12 7v10"
                stroke="var(--color-text-inverse)"
                strokeWidth="2"
                strokeLinecap="round"
              />
            </svg>
          </button>
          <button
            type="button"
            className={styles.pause}
            aria-label={paused ? '배너 자동 넘김 재생' : '배너 자동 넘김 일시정지'}
            aria-pressed={paused}
            onClick={() => setPaused((p) => !p)}
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 32 32"
              fill="currentColor"
              aria-hidden="true"
              focusable="false"
            >
              {paused ? (
                <path d="M11 6 26 16 11 26Z" />
              ) : (
                <>
                  <rect x="8" y="6" width="6" height="20" rx="2" />
                  <rect x="19" y="6" width="6" height="20" rx="2" />
                </>
              )}
            </svg>
          </button>
        </div>
      </div>
      <Sheet
        open={eventsOpen}
        onClose={() => setEventsOpen(false)}
        title="진행 중인 이벤트"
        className={styles.eventSheet}
      >
        <p className="text-body-s">현재 진행 중인 배너 {BANNERS.length}개를 확인해보세요.</p>
        <ul className={styles.eventList}>
          {BANNERS.map((banner, i) => (
            <li key={banner.tone}>
              <button
                type="button"
                className={styles.eventButton}
                aria-label={`${i + 1}번 배너 보기: ${banner.title}`}
                onClick={() => {
                  setResetting(false);
                  setPosition(i);
                  setEventsOpen(false);
                }}
              >
                <HomePromotionCard banner={banner} />
              </button>
            </li>
          ))}
        </ul>
      </Sheet>
    </section>
  );
}
