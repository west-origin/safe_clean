// Figma: Button/Primary › Spinner (2171:2394). 22×22, 3/4 원호, stroke 2.383.
// path 데이터는 Figma 원본 그대로. 회전 애니메이션은 Figma 에 없어 코드에서 정함 (PROGRESS.md 판단 항목).
import styles from './Spinner.module.css';
// 쓰는 곳(예): Button 의 loading 상태 (라벨 앞에 붙고, 색은 Button.module.css 의 .spinner 가 정한다).

/**
 * 계속 도는 로딩 원호. 회전은 Spinner.module.css 의 .spinner(애니메이션)가 맡는다.
 * @param {object} props
 * @param {number} [props.size=22] 한 변 크기(px)
 * @param {string} [props.className]
 */
export default function Spinner({ size = 22, className }) {
  // 다른 아이콘과 달리 aria-hidden 이 아니라 role="img" + aria-label="로딩 중" 이라 화면 읽기 도구가 "로딩 중" 을 읽는다.
  // 부모가 준 className 은 회전 클래스(styles.spinner)에 덧붙는다 — 대신하지 않으므로 늘 돈다.
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 22 22"
      fill="none"
      role="img"
      aria-label="로딩 중"
      className={[styles.spinner, className].filter(Boolean).join(' ')}
    >
      <path
        d="M11 2.75C9.3683 2.75 7.77325 3.23385 6.41655 4.14038C5.05984 5.0469 4.00242 6.33537 3.37799 7.84286C2.75357 9.35035 2.59019 11.0092 2.90852 12.6095C3.22685 14.2098 4.01259 15.6798 5.16637 16.8336C6.32015 17.9874 7.79016 18.7732 9.39051 19.0915C10.9908 19.4098 12.6496 19.2464 14.1571 18.622C15.6646 17.9976 16.9531 16.9402 17.8596 15.5835C18.7661 14.2267 19.25 12.6317 19.25 11"
        stroke="currentColor"
        strokeWidth="2.38333"
        strokeLinecap="round"
      />
    </svg>
  );
}
