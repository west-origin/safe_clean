import styles from './HomePromotions.module.css';

/**
 * 롤링 영역과 전체 이벤트 목록이 같은 배너 이미지를 사용한다.
 * 이미지 안에 문구가 그려져 있으므로 같은 문구를 alt 로 읽어준다.
 * @param {object} props
 * @param {{ title: string, description: string, image: string }} props.banner
 */
export default function HomePromotionCard({ banner }) {
  return (
    <img
      className={styles.card}
      src={banner.image}
      width="360"
      height="108"
      alt={`${banner.title}. ${banner.description}`}
      draggable="false"
    />
  );
}
