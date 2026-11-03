// Figma: Button/Primary (2171:2396) · Button/Secondary (2171:2401) · Button/Destructive (2171:2406)
//
// variant     Figma 컴포넌트          State variant
// primary     Button/Primary          Default | Pressed(:active) | Disabled | Loading
// secondary   Button/Secondary        Default | Pressed(:active) | Disabled
// destructive Button/Destructive      Default | Pressed(:active) | Disabled
// outline     Button/Secondary 의 인스턴스 오버라이드 (흰 배경 + 1.5px brand 테두리 + brand 글자). Manager/Home 등 화면에서 쓰임.
// outlineGray Button/Secondary 의 인스턴스 오버라이드 (흰 배경 + 1.5px border/default + text/primary). Job/Detail 의 지도 보기·고객 연락.
//             Figma 에 variant 로는 없음 → Pressed 는 outline = brand/subtle, outlineGray = surface/pressed 로
//             정함 (PROGRESS.md 판단 항목)
//
// - 라벨은 children 으로 받는다 (Figma 에서는 인스턴스 오버라이드 / Label TEXT property).
// - Primary 만 chevron 이 있다. Disabled 면 숨기고, Loading 이면 스피너 + loadingLabel 로 바뀐다.
// - 너비는 기본 100% (부모 폭을 따라간다. 393 폭 화면이면 좌우 여백을 뺀 345px). 나란히 놓을 때는
//   부모 flex 로 조절.
// - 저장·보내기 같은 동작은 화면의 useAction 과 짝을 이뤄 `loading={x.loading}` 으로 넘겨
//   처리 중 두 번 눌리지 않게 한다.
import ChevronRight from '../../icons/ChevronRight.jsx';
import Spinner from '../../icons/Spinner.jsx';
import styles from './Button.module.css';

/**
 * @param {object} props
 * @param {'primary'|'secondary'|'destructive'|'outline'|'outlineGray'} [props.variant='primary']
 * @param {import('react').ReactNode} props.children 버튼 라벨
 * @param {boolean} [props.disabled] Disabled 상태. chevron 숨김
 * @param {boolean} [props.loading] Loading 상태: 스피너 + loadingLabel, chevron 제거, 클릭 불가. Figma 는 Primary 에만 Loading variant 가 있지만 다른 variant 도 같은 방식으로 그린다(코드에서 정함 — 탈퇴·취소 버튼의 더블탭 차단)
 * @param {string} [props.loadingLabel='처리 중'] Loading 중 라벨 (Figma 기본값 "처리 중")
 * @param {boolean} [props.chevron] chevron 표시 여부. 기본: primary 면 true
 * @param {'button'|'submit'|'reset'} [props.type='button']
 * @param {string} [props.className] 레이아웃용 추가 클래스 (색·폰트 지정 금지)
 * @param {(e: import('react').MouseEvent<HTMLButtonElement>) => void} [props.onClick]
 * 위에 없는 나머지 props(aria-label, form 등)는 그대로 <button> 에 붙는다 (...rest).
 */
export default function Button({
  variant = 'primary',
  children,
  disabled = false,
  loading = false,
  loadingLabel = '처리 중',
  chevron,
  type = 'button',
  className,
  onClick,
  ...rest
}) {
  const isPrimary = variant === 'primary';
  const isLoading = Boolean(loading);
  // chevron 을 따로 넘기지 않으면(undefined) primary 일 때만 보인다. Disabled·Loading 이면 항상 숨긴다.
  const showChevron = (chevron ?? isPrimary) && !disabled && !isLoading;

  // 공통 모양(button) + variant 모양(primary 등) + 화면이 넘긴 레이아웃 클래스를 합친다.
  const classes = [styles.button, styles[variant], className].filter(Boolean).join(' ');

  // Loading 만으로는 disabled 속성을 붙이지 않는다 (붙이면 :disabled 규칙이 걸려 회색 Disabled 모양이 된다).
  // 대신 aria-busy·aria-disabled 로 "처리 중" 을 알리고, onClick 을 떼어 두 번 눌려도 아무 일이 없게 한다.
  // 주의: onClick 만 막으므로 type="submit" 으로 form 안에 두면 Loading 중에도 제출은 된다.
  return (
    <button
      type={type}
      className={classes}
      disabled={disabled}
      aria-busy={isLoading || undefined}
      aria-disabled={isLoading || undefined}
      onClick={isLoading ? undefined : onClick}
      {...rest}
    >
      {isLoading ? (
        <>
          <Spinner className={styles.spinner} />
          <span className={`text-button-label ${styles.label}`}>{loadingLabel}</span>
        </>
      ) : (
        <span className={`text-button-label ${styles.label}`}>{children}</span>
      )}
      {showChevron && (
        <span className={styles.chevron} aria-hidden="true">
          <ChevronRight />
        </span>
      )}
    </button>
  );
}
