import { useState } from 'react';
import { BeforeAfterSlider, Button, Card, cx } from '@ansimclean/ui';
import styles from './ServicePhotos.module.css';

function PhotoPair({ photo, onRetry }) {
  const [failed, setFailed] = useState(false);
  const retry = () => { setFailed(false); onRetry(); };
  return (
    <figure className={styles.photo}>
      <figcaption className="text-heading-s">{photo.location}</figcaption>
      {failed || !photo.beforeUrl || !photo.afterUrl ? (
        <Card gap="sm">
          <p role="alert">{photo.location} 사진을 불러오지 못했어요.</p>
          <Button variant="secondary" onClick={retry}>사진 다시 불러오기</Button>
        </Card>
      ) : (
        <BeforeAfterSlider location={photo.location} beforeSrc={photo.beforeUrl} afterSrc={photo.afterUrl} onImageError={() => setFailed(true)} />
      )}
    </figure>
  );
}

/** 녹화 영상의 존재와 별개인 전·후 사진 비교. 만료·이미지 실패 시 새 서명 URL을 받는다. */
export default function CleaningPhotoGallery({ photos, onRetry }) {
  return <div className={styles.gallery}>
    {photos.length > 0 && <p className={cx('text-body-s', styles.guide)}>선을 좌우로 움직여 청소 전후를 비교해보세요</p>}
    {photos.map((photo) => <PhotoPair key={`${photo.id}:${photo.beforeUrl}:${photo.afterUrl}`} photo={photo} onRetry={onRetry} />)}</div>;
}
