import { Navigate, useNavigate, useSearchParams } from 'react-router';
import { ScreenLayout, NavBar, ScreenError, ScreenLoading, EmptyState, useAsync } from '@ansimclean/ui';
import { getCleaningPhotos } from '../../api/customer.js';
import { CLEANING_PHOTOS_COPY } from '../../api/mock.js';
import CleaningPhotoGallery from './CleaningPhotoGallery.jsx';

/** 완료 예약의 사진 전용 화면. 라이브 토큰·녹화 URL을 요청하지 않는다. */
export default function ServicePhotos() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const bookingId = params.get('booking');
  const { data, error, reload } = useAsync(() => bookingId ? getCleaningPhotos(bookingId) : Promise.resolve(null), [bookingId]);
  const navBar = <NavBar title={CLEANING_PHOTOS_COPY.title} onBack={() => navigate('/reservations')} />;
  if (!bookingId) return <Navigate to="/reservations" replace />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!data) return <ScreenLoading navBar={navBar} />;
  return (
    <ScreenLayout navBar={navBar} gap="md">
      {!data.ready ? <EmptyState icon="calendar" title="청소가 완료되면 전·후 사진을 볼 수 있어요" /> : data.photos.length === 0 ? (
        <EmptyState icon="calendar" title="등록된 청소 전·후 사진이 없어요" />
      ) : (
        <>
          <p className="text-body-s">{CLEANING_PHOTOS_COPY.hint}</p>
          <CleaningPhotoGallery photos={data.photos} onRetry={reload} />
        </>
      )}
    </ScreenLayout>
  );
}
