// Figma: Home/Realtime (2:323) · Home/Standard-A (63:346) — 히어로 캐러셀 2장(실시간 청소 / 일반 청소) + ServiceCategoryList + GiftBanner + TabBar(Home)
// 프로토타입: 두 프레임이 서로를 가리킨다(탭으로 토글) → 히어로 탭·가로 스와이프로 슬라이드 전환. 배경 그라디언트·이미지 위치는 슬라이드마다 다르다.
// 스와이프 (코드에서 정함, 2026-09-24 사용자 승인): 슬라이드가 손가락을 따라 움직이고 놓으면 가까운 장에 붙는다 — 가로 scroll-snap 이 따라오기·관성·
//   세로 스크롤과의 구분을 맡는다. Figma 의 HeroArtPeek(옆 장이 흐릿하게 삐져나온 그림)가 '옆에 붙어 있다' 는 뜻이라 순간 교체 대신 이렇게 했다.
//   배경은 두 그라디언트를 겹쳐 300ms 크로스페이드, 점은 제자리. 자동 넘김 없음. 동작 줄이기 설정이면 애니메이션 없이 바로.
// ServiceCategoryList → 각 카테고리 Frequency 화면. DecoBackdrop (flat image) 은 Figma 에 채우기가 없어 그리지 않는다.
// 경로 /home (하단 탭 '홈'). 데이터는 GET /customer/home → { slides, upcoming, changeDeadlineHours } 이고,
// upcoming 은 '예정' 탭 예약(배정 중·방문 예정·진행 중) 중 가장 이른 1건(없으면 null, 서버 BookingService.nextVisit)이라
// 있을 때만 '다가오는 방문' 카드를 그린다. changeDeadlineHours(변경·취소 기한 시간)는 잠긴 카드 안내에 넣는다.
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { ScreenLayout, TabBar, ServiceCategoryList, GiftBanner, SlideCounter, CarouselDots, ReservationCard, ASSETS, cx, useAsync, usePolling, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getHome, getSupportPhone } from '../../api/customer.js';
import { UPCOMING_HOME_COPY, CHANGE_LOCK_COPY, CHANGE_DEADLINE_HOURS } from '../../api/mock.js';
import { useCustomerTabs } from '../useCustomerTabs.js';
import { onPushReceived } from '../../push/pushClient.js';
import styles from './Home.module.css';

// '다가오는 방문' 카드의 매니저 배정·작업 상태가 화면에 머무는 동안 바뀌어도 보이게 — 내 예약과 같은 주기 (푸시가 오면 바로)
const HOME_REFRESH_MS = 30_000;

/** ServiceCategoryList 의 키 → 예약 플로우 카테고리 경로 (Figma: Home → Booking, Move → MoveIn, Appliance → Appliance, Office → Office) */
const CATEGORY_PATHS = { home: 'home', moveIn: 'move-in', appliance: 'appliance', office: 'office' };

// OS 의 '동작 줄이기' 설정이 켜져 있으면 true — 탭으로 넘길 때 부드러운 스크롤 대신 바로 이동한다.
// window·matchMedia 가 없는 환경이면 false (꺼진 것으로 본다)
const reducedMotion = () => typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** Home 화면: 히어로 캐러셀 + 다가오는 방문 카드 + 청소 카테고리 목록 + 선물 배너 */
export default function Home() {
  const navigate = useNavigate();
  const selectTab = useCustomerTabs();
  const { data, error, reload } = usePolling(() => getHome(), [], { interval: HOME_REFRESH_MS, subscribe: onPushReceived });
  const slides = data?.slides ?? [];
  const [index, setIndex] = useState(0); // 지금 보이는 장 — 가로 스크롤 위치에서 읽는다
  const trackRef = useRef(null); // 가로 스크롤 트랙 DOM — 탭·키보드로 넘길 때 scrollTo 를 부른다
  // 기한이 지난 방문에도 "변경·취소는 '내 예약' 에서 할 수 있어요" 라 했다 → 내 예약 카드와 같은 안내 (전체 점검, 직접 사용 점검 U2 의 남은 자리)
  const locked = data?.upcoming?.changeable === false;
  // 앱에서 못 바꾸는 방문일 때만 고객센터 번호를 받는다 (안내 문구에 넣는다). 번호가 없으면 null → '1:1 문의' 안내
  const { data: supportPhone } = useAsync(() => (locked ? getSupportPhone() : Promise.resolve(null)), [locked]);


  // 번호용 useAsync 도 이 return 들보다 위에 둔다 (훅은 매 렌더 같은 순서로 불려야 한다). 오류·로딩 중에도 하단 탭은 그대로
  if (error) return <ScreenError error={error} onRetry={reload} tabBar={<TabBar active="home" onSelect={selectTab} />} />;
  if (!data) return <ScreenLoading tabBar={<TabBar active="home" onSelect={selectTab} />} />;

  const slide = slides[index]; // 지금 장 — 그 id 로 화면 배경 클래스 bg-<id> 를 붙인다 (CSS 에는 bg-standard 만 있고 첫 장은 기본 배경)
  // 탭·키보드로 넘길 때도 같은 트랙을 스크롤한다 — 점·배경은 onScroll 이 바꾼다(스와이프와 한 길)
  // delta 만큼 옆 장으로. 양 끝에서는 반대쪽 끝으로 돈다(나머지 연산). scrollTo 가 없으면 아무것도 안 한다
  const move = (delta) => {
    const track = trackRef.current;
    if (!track || !slides.length) return;
    const target = (index + delta + slides.length) % slides.length;
    track.scrollTo?.({ left: target * track.clientWidth, behavior: reducedMotion() ? 'auto' : 'smooth' });
  };
  // 트랙이 스크롤될 때마다(손가락으로 밀 때·move 가 스크롤할 때 모두) 스크롤 위치에서 지금 장 번호를 다시 계산한다
  const onScroll = (e) => {
    const { scrollLeft, clientWidth } = e.currentTarget;
    if (!clientWidth) return; // 폭이 0 이면(아직 그려지지 않음 등) 나눗셈을 하지 않는다
    const next = Math.min(slides.length - 1, Math.max(0, Math.round(scrollLeft / clientWidth))); // 반 넘게 밀면 다음 장
    if (next !== index) setIndex(next);
  };

  // 키보드: Enter/Space/→ 다음, ← 이전
  const onKeyDown = (e) => {
    const delta = { ArrowRight: 1, ArrowLeft: -1, Enter: 1, ' ': 1 }[e.key];
    if (!delta) return;
    e.preventDefault(); // 키의 기본 동작(Space 의 페이지 스크롤, 화살표의 트랙 스크롤)을 막고 한 장씩만 넘긴다
    move(delta);
  };

  // 트랙은 탭(onClick)·키보드(onKeyDown, tabIndex=0 이라 포커스를 받는다)·스크롤(onScroll)을 모두 받는다.
  // 지금 장이 아닌 장은 aria-hidden 으로 낭독기에서 뺀다. 헤드라인은 첫 장만 h1, 나머지는 p 라 h1 이 장 수만큼 생기지 않는다.
  return (
    <ScreenLayout noPadding gap="none" className={cx(styles.screen, slide && styles[`bg-${slide.id}`])} tabBar={<TabBar active="home" onSelect={selectTab} />}>
      <div className={styles.content}>
        {/* HeroCarousel: 탭 = 다음 슬라이드, 스와이프 = 손가락을 따라가다 가까운 장에 붙는다 */}
        <section className={styles.hero} aria-roledescription="carousel" aria-label="서비스 소개">
          <div ref={trackRef} className={styles.track} onScroll={onScroll} onClick={() => move(1)} onKeyDown={onKeyDown} tabIndex={0} role="group" aria-label="서비스 소개 (탭하거나 밀어서, 화살표 키로 넘기기)">
            {slides.map((s, i) => (
              <div key={s.id} className={styles.slide} aria-hidden={i !== index} aria-roledescription="slide" aria-label={`${i + 1} / ${slides.length}`}>
                {ASSETS[s.peek] && <img className={cx(styles.peek, styles[`peek-${s.id}`])} src={ASSETS[s.peek].src} srcSet={ASSETS[s.peek].srcSet} width={ASSETS[s.peek].width} height={ASSETS[s.peek].height} alt="" draggable="false" />}
                {ASSETS[s.image] && <img className={cx(styles.heroArt, styles[`art-${s.id}`])} src={ASSETS[s.image].src} srcSet={ASSETS[s.image].srcSet} width={ASSETS[s.image].width} height={ASSETS[s.image].height} alt="" draggable="false" />}
                <header className={styles.heroHeader}>
                  <p className={cx('text-body-m', styles.eyebrow)}>{s.eyebrow}</p>
                  {i === 0 ? <h1 className={cx('text-heading-m', styles.title)}>{s.headline}</h1> : <p className={cx('text-heading-m', styles.title)}>{s.headline}</p>}
                </header>
                <div className={styles.caption}>
                  <SlideCounter current={i + 1} total={slides.length} />
                  <h2 className={cx('text-heading-xl', styles.slideTitle)}>{s.title}</h2>
                  <p className={cx('text-body-xs', styles.slideDescription)}>{s.description}</p>
                </div>
              </div>
            ))}
          </div>
          {/* 점은 트랙 밖 제자리 — 장이 움직이는 동안 같이 밀려 가지 않게 */}
          <CarouselDots index={index} count={slides.length} className={styles.dots} />
        </section>

        {/* 홈이 예약을 전혀 보여 주지 않아 내일 방문이 있어도 배너만 보였다 (사용성 검토 2026-09-22) */}
        {data.upcoming && (
          <section className={styles.upcoming}>
            <h2 className={cx('text-title-card', styles.upcomingTitle)}>{UPCOMING_HOME_COPY.title}</h2>
            {/* 홈 카드는 변경·취소 버튼 없이 안내 한 줄만 (changeable=false). 진행 중이면 '실시간 영상 보기' → 라이브 화면 */}
            <ReservationCard
              title={data.upcoming.title}
              status={data.upcoming.status}
              rows={data.upcoming.rows}
              changeable={false}
              // 잠긴 카드의 '방문 N시간 전' 은 서버 설정 값(없으면 기본 24) — 카드 잠금(changeable)과 같은 기준 (문제 목록 25번)
              notChangeableNote={locked ? CHANGE_LOCK_COPY.card(data.changeDeadlineHours ?? CHANGE_DEADLINE_HOURS, supportPhone) : UPCOMING_HOME_COPY.note}
              onWatch={() => navigate(`/service/live?booking=${data.upcoming.id}`)}
            />
          </section>
        )}

        {/* 카테고리를 누르면 그 카테고리 예약 플로우의 1단계(정기청소·1회 청소 선택, /booking/:category/frequency)로 간다 */}
        <ServiceCategoryList
          onSelect={(key) => navigate(`/booking/${CATEGORY_PATHS[key]}/frequency`)}
        />
        {/* TODO(figma): "청소 선물하러 가기 >" 의 연결 화면이 Figma 에 없음 */}
        <GiftBanner />
      </div>
    </ScreenLayout>
  );
}
