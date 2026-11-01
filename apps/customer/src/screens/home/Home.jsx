import { useNavigate } from 'react-router';
import {
  ScreenLayout,
  ReservationCard,
  ServiceStoreIcon,
  ServiceHomeIcon,
  ServiceStudioIcon,
  cx,
  useAsync,
  usePolling,
  ScreenError,
  ScreenLoading,
} from '@ansimclean/ui';
import { getHome, getNotices, getSupportPhone } from '../../api/customer.js';
import { UPCOMING_HOME_COPY, CHANGE_LOCK_COPY, CHANGE_DEADLINE_HOURS } from '../../api/mock.js';
import CustomerFooter from '../CustomerFooter.jsx';
import { onPushReceived } from '../../push/pushClient.js';
import { setBookingDraft } from '../booking/bookingDraft.js';
import BrandMarkIcon from './BrandMarkIcon.jsx';
import homeArt from '@ansimclean/ui/assets/housing-house@3x.webp';
import giftArt from '@ansimclean/ui/assets/gift-illustration@3x.webp';
import HomePromotions from './HomePromotions.jsx';
import styles from './Home.module.css';

// 기존 예약·실시간 상태 갱신은 새 홈에서도 유지한다.
export default function Home() {
  const navigate = useNavigate();
  const { data, error, reload } = usePolling(() => getHome(), [], {
    interval: 30_000,
    subscribe: onPushReceived,
  });
  const notices = useAsync(() => getNotices(), []);
  const locked = data?.upcoming?.changeable === false;
  const { data: supportPhone } = useAsync(
    () => (locked ? getSupportPhone() : Promise.resolve(null)),
    [locked]
  );
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
        </section>
        {data.upcoming && (
          <section className={styles.section}>
            <h2 className="text-heading-s">{UPCOMING_HOME_COPY.title}</h2>
            <ReservationCard
              title={data.upcoming.title}
              status={data.upcoming.status}
              rows={data.upcoming.rows}
              changeable={false}
              notChangeableNote={
                locked
                  ? CHANGE_LOCK_COPY.card(
                      data.changeDeadlineHours ?? CHANGE_DEADLINE_HOURS,
                      supportPhone
                    )
                  : UPCOMING_HOME_COPY.note
              }
              onWatch={() => navigate(`/service/live?booking=${data.upcoming.id}`)}
            />
          </section>
        )}
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
        <HomePromotions />
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
