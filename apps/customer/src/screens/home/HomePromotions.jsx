import { Circle, Plus, Pause, Play } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Sheet, cx } from '@ansimclean/ui';
import styles from './HomePromotions.module.css';
import HomePromotionCard from './HomePromotionCard.jsx';

// 배너는 서버가 준다 (2026-11-12 — 어드민이 매니저 앱 배너 관리에서 올린다, rbac.md §15-22). 예전에는 이 파일에 3장이 고정돼 있었고,
// 그 이미지(@ansimclean/ui/assets/home-banner-*.png)는 scripts/seed-home-banners.mjs 가 처음 한 번 서버에 올린다.
const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/**
 * 홈 혜택 배너 캐러셀. 5초마다 이동. 마지막 뒤에 첫 장을 복제해 한 방향으로 끊김 없이 순환한다. 한 장뿐이면 넘기지 않는다.
 * @param {object} props
 * @param {{ id: string, title: string, description: string, image: string }[]} props.banners 서버 순서 그대로 (Home 이 getHomeBanners 로 받는다, 1장 이상)
 */
export default function HomePromotions({ banners }) {
  const [position, setPosition] = useState(0);
  const [resetting, setResetting] = useState(false);
  const [paused, setPaused] = useState(false);
  const [eventsOpen, setEventsOpen] = useState(false);
  const [interacting, setInteracting] = useState(false);
  const [hidden, setHidden] = useState(document.hidden);
  const startX = useRef(null);
  const index = position % banners.length;
  useEffect(() => {
    const visibility = () => setHidden(document.hidden);
    document.addEventListener('visibilitychange', visibility);
    return () => document.removeEventListener('visibilitychange', visibility);
  }, []);
  useEffect(() => {
    if (banners.length < 2 || paused || interacting || eventsOpen || hidden || reducedMotion()) return;
    const timer = setTimeout(() => setPosition((p) => (p + 1) % (banners.length + 1)), 5000);
    return () => clearTimeout(timer);
  }, [position, paused, interacting, eventsOpen, hidden, banners.length]);
  useEffect(() => {
    if (!resetting) return;
    // 한 프레임은 전환 없이 첫 장을 그린 뒤 애니메이션을 다시 켠다.
    let frame = requestAnimationFrame(() => {
      frame = requestAnimationFrame(() => setResetting(false));
    });
    return () => cancelAnimationFrame(frame);
  }, [resetting]);
  const settle = () => {
    if (position !== banners.length) return;
    setResetting(true);
    setPosition(0);
  };
  const move = (delta) => {
    setResetting(false);
    setPosition((p) => ((p % banners.length) + delta + banners.length) % banners.length);
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
          {[...banners, banners[0]].map((banner, i) => (
            <div
              key={i}
              className={styles.slide}
              aria-hidden={i !== position}
              aria-roledescription="slide"
              aria-label={`${(i % banners.length) + 1} / ${banners.length}`}
            >
              <HomePromotionCard banner={banner} />
            </div>
          ))}
        </div>
        <div className={styles.controls}>
          <button
            type="button"
            className={styles.counter}
            aria-label={`진행 중인 이벤트 전체 보기, ${index + 1} / ${banners.length}`}
            aria-haspopup="dialog"
            aria-expanded={eventsOpen}
            onClick={() => setEventsOpen(true)}
          >
            <span className="text-caption-l-bold">
              {index + 1}/{banners.length}
            </span>
            <Circle size={18} strokeWidth={2} fill="currentColor" aria-hidden="true" focusable="false">
              <Plus width={24} height={24} strokeWidth={2} color="var(--color-text-inverse)" />
            </Circle>
          </button>
          <button
            type="button"
            className={styles.pause}
            aria-label={paused ? '배너 자동 넘김 재생' : '배너 자동 넘김 일시정지'}
            aria-pressed={paused}
            onClick={() => setPaused((p) => !p)}
          >
            {paused ? <Play size={16} strokeWidth={2} aria-hidden="true" focusable="false" /> : <Pause size={16} strokeWidth={2} aria-hidden="true" focusable="false" />}
          </button>
        </div>
      </div>
      <Sheet
        open={eventsOpen}
        onClose={() => setEventsOpen(false)}
        title="진행 중인 이벤트"
        className={styles.eventSheet}
      >
        <p className="text-body-s">현재 진행 중인 배너 {banners.length}개를 확인해보세요.</p>
        <ul className={styles.eventList}>
          {banners.map((banner, i) => (
            <li key={banner.id}>
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
