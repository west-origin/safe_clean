// 타이포그래피 견본 페이지. 텍스트 스타일 23개를 실제 폰트로 확인하는 용도 (개발 중에만 표시).
import { textStyles } from '../tokens/tokens.js';
import styles from './TypographySpecimen.module.css';
// Gallery.jsx 의 마지막 "Typography" 섹션에 들어간다. 스타일 목록은 생성물 tokens.js 의 textStyles 에서 읽는다
// (Figma 텍스트 스타일 → npm run tokens 로 만든 표). 그래서 스타일이 추가·삭제되면 여기도 저절로 따라간다.

// 모든 스타일에 같은 예문을 써서 크기·굵기 차이만 비교한다. 예문에 한글·영문·숫자를 섞어 두었다
// (fonts.css 가 korean·latin 글꼴 파일을 따로 등록하므로 둘 다 보인다).
const SAMPLE = '안심클린 청소 매니저가 오늘 오후 2시에 방문합니다 Aa 0123';

/**
 * 텍스트 스타일마다 한 줄: 위에 "이름 · 크기px · 굵기", 아래에 그 스타일 클래스(.text-*)를 붙인 예문.
 * props 없음. 개발용 견본이라 프로덕션 화면에서는 쓰지 않는다.
 */
export default function TypographySpecimen() {
  return (
    <section className={styles.wrap} aria-label="타이포그래피 견본">
      <p className={`text-caption ${styles.meta}`}>텍스트 스타일 {Object.keys(textStyles).length}개</p>
      <ul className={styles.list}>
        {Object.entries(textStyles).map(([name, t]) => (
          <li key={name} className={styles.item}>
            <span className={`text-caption ${styles.meta}`}>
              {name} · {t.size}px · {t.fontWeight}
            </span>
            <span className={t.className}>{SAMPLE}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
