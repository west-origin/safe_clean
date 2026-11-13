import { useEffect } from 'react';
import { useNavigate } from 'react-router';
import {
  ScreenLayout,
  ServiceStoreIcon,
  BrandMarkIcon,
  ServiceHomeIcon,
  ServiceStudioIcon,
  ServiceDisassemblyIcon,
  ServiceRepairIcon,
  ServicePhotoIcon,
  cx,
  useAsync,
  usePolling,
  ScreenError,
  ScreenLoading,
} from '@ansimclean/ui';
import { getHome, getNotices, getHomeBanners } from '../../api/customer.js';
import { UPCOMING_HOME_COPY } from '../../api/mock.js';
import CustomerFooter from '../CustomerFooter.jsx';
import { onPushReceived } from '../../push/pushClient.js';
import { setBookingDraft } from '../booking/bookingDraft.js';
import homeArt from '@ansimclean/ui/assets/housing-house@3x.webp';
import giftArt from '@ansimclean/ui/assets/gift-illustration@3x.webp';
import HomePromotions from './HomePromotions.jsx';
import UpcomingVisitCard from './UpcomingVisitCard.jsx';
import { takePendingInvite } from '../../invite/inviteLink.js';
import styles from './Home.module.css';

// 기존 예약·실시간 상태 갱신은 새 홈에서도 유지한다.
export default function Home() {
  const navigate = useNavigate();
  // 로그인 전에 친구 초대 링크를 열었으면(App.jsx 가 기억함) 가입·로그인 뒤 처음 온 홈에서 초대 화면으로 이어 간다 (2026-11-13)
  useEffect(() => {
    const code = takePendingInvite();
    if (code) navigate(`/friends/invite/${code}`);
  }, [navigate]);
  const { data, error, reload } = usePolling(() => getHome(), [], {
    interval: 30_000,
    subscribe: onPushReceived,
  });
  const notices = useAsync(() => getNotices(), []);
  const banners = useAsync(() => getHomeBanners(), []); // 혜택 배너 — 없거나 못 받으면 배너 영역을 숨긴다 (rbac.md §15-22 판단 항목)
  const tabBar = <CustomerFooter active="home" />;
  if (error) return <ScreenError error={error} onRetry={reload} tabBar={tabBar} />;
  if (!data) return <ScreenLoading tabBar={tabBar} />;
  const book = (service) => {
    // 두 진입점은 기존 가정/원룸 예약을 함께 사용하며 선택한 종류를 초안에 기억한다.
    setBookingDraft('home', { homeService: service, submitKey: undefined });
    navigate('/booking/home/frequency');
  };
  const notice = notices.data?.[0];
  return (
    <ScreenLayout noPadding gap="none" className={styles.screen} tabBar={tabBar}>
      <div className={styles.content}>
        <header className={styles.brand}>
          <BrandMarkIcon />
          <h1 className="text-heading-m">안심클린</h1>
        </header>
        {/* 동그란 아이콘 + 아래 이름 (2026-10-31 사용자 요청). 매장 = 매장 청소 예약(store, 서버 V058) */}
        <section className={styles.categories} aria-label="청소 서비스">
          <button type="button" className={styles.category} onClick={() => navigate('/booking/store/frequency')}>
            <span className={styles.categoryIcon}><ServiceStoreIcon /></span>
            <span className={cx('text-body-default', styles.categoryLabel)}>매장</span>
          </button>
          <button type="button" className={styles.category} onClick={() => book('house')}>
            <span className={styles.categoryIcon}><ServiceHomeIcon /></span>
            <span className={cx('text-body-default', styles.categoryLabel)}>가정</span>
          </button>
          <button type="button" className={styles.category} onClick={() => book('studio')}>
            <span className={styles.categoryIcon}><ServiceStudioIcon /></span>
            <span className={cx('text-body-default', styles.categoryLabel)}>원룸</span>
          </button>
          {/* 둘째 줄 (2026-11-08 사용자 요청): 분해청소 = 가전청소 예약(appliance), 수리 = 수리 탭, 사진 문의 = 1:1 문의.
              시안 문구는 '사진 견적' 이지만 사용자 지시로 '사진 문의'. '무료' 배지와 파란 강조도 시안 그대로 */}
          <button type="button" className={styles.category} onClick={() => navigate('/booking/appliance/frequency')}>
            <span className={styles.categoryIcon}><ServiceDisassemblyIcon /></span>
            <span className={cx('text-body-default', styles.categoryLabel)}>분해청소</span>
          </button>
          <button type="button" className={styles.category} onClick={() => navigate('/repair')}>
            <span className={styles.categoryIcon}><ServiceRepairIcon /></span>
            <span className={cx('text-body-default', styles.categoryLabel)}>수리</span>
          </button>
          <button type="button" className={cx(styles.category, styles.categoryHighlight)} onClick={() => navigate('/settings/inquiry')}>
            <span className={styles.categoryIcon}>
              <ServicePhotoIcon />
              <span className={cx('text-caption-bold', styles.freeBadge)}>무료</span>
            </span>
            <span className={cx('text-body-bold', styles.categoryLabel)}>사진 문의</span>
          </button>
        </section>
          <section className={cx(styles.section, styles.upcoming)}>
            <h2 className="text-heading-s">{UPCOMING_HOME_COPY.title}</h2>
            <UpcomingVisitCard reservation={data.upcoming} onClick={() => navigate('/reservations', { state: { focusBookingId: data.upcoming.id } })} />
          </section>
        <section className={styles.section} aria-labelledby="recommended-title">
          <div className={styles.sectionHeader}>
            <h2 id="recommended-title" className="text-heading-s">
              추천 서비스
            </h2>
          </div>
          <button type="button" className={styles.recommendation} onClick={() => book('house')}>
            <span className={styles.artCircle}>
              <img src={homeArt} width="86" height="86" alt="" />
            </span>
            <span className={styles.categoryText}>
              <strong className="text-heading-s">가정 청소</strong>
              <span className="text-body-s">일상에 여유를, 우리 집을 더 깨끗하게</span>
            </span>
          </button>
        </section>
        {banners.data?.length > 0 && <HomePromotions banners={banners.data} />}
        <section className={styles.section} aria-labelledby="news-title">
          <div className={styles.sectionHeader}>
            <h2 id="news-title" className="text-heading-s">
              안심클린 소식
            </h2>
          </div>
          <button
            type="button"
            className={styles.notice}
            onClick={() =>
              navigate(notice ? `/settings/notices/${notice.id}` : '/settings/notices')
            }
          >
            <span className={cx('text-caption-l-bold', styles.noticeBadge)}>공지</span>
            <span className={cx('text-body-s', styles.noticeTitle)}>
              {notice?.title ??
                (notices.error ? '공지사항을 확인해주세요' : '안심클린의 새로운 소식을 만나보세요')}
            </span>
          </button>
        </section>
        <section className={styles.gift} aria-label="청소 선물 안내">
          <img src={giftArt} width="90" height="86" alt="" />
          <p className="text-title-card">깨끗함을 넘어, 안심까지 선물하세요</p>
        </section>
      </div>
    </ScreenLayout>
  );
}
