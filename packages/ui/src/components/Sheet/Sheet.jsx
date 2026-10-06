// Figma: 바텀시트 공통 껍데기. Manager/Call/RejectSheet(2122:2395), Job/PauseSheet, Job/CompleteConfirm, Schedule/AddUnavailable 이 같은 형태.
// 흰 배경, 상단 radius md, 패딩 상 12 / 좌우 24 / 하 32, 간격 16, DragHandle 36×4, 제목 Heading/S.
// 뒤 배경(스크림)과 열림/닫힘 동작은 Figma 에 없어 코드에서 정함 (PROGRESS.md 판단 항목).
// 화면 아래에서 올라오는 대화상자(바텀시트)의 껍데기. 안의 내용(문구·버튼)은 부르는 쪽이 children 으로 넣는다.
// 예: 매니저 앱 CallDetail(거절 확인), 고객 앱 Reservations(예약 취소 확인).
// 열고 닫기는 부모가 open 으로 정하고, 닫고 싶을 때 이 컴포넌트는 onClose 만 부른다.
// 열려 있는 동안: 뒤 화면 스크롤 잠금, 포커스를 시트 안에 가둠, ESC·배경 탭·핸들 탭으로 닫기.
import { useEffect, useId, useLayoutEffect, useRef } from 'react';
import { cx } from '../../utils/cx.js';
import styles from './Sheet.module.css';

/** 열린 시트 수. 두 장이 겹치거나 화면째 언마운트돼도 마지막 시트가 닫힐 때 원래 overflow 로 돌아간다 */
let openSheets = 0;
let bodyOverflowBefore = '';
/** 뒤 화면(body) 스크롤을 막는다. 첫 시트가 열릴 때만 원래 overflow 값을 기억해 둔다. */
function lockScroll() {
  if (openSheets === 0) {
    bodyOverflowBefore = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }
  openSheets += 1;
}
/** 시트 하나가 닫힐 때 부른다. 열린 시트가 0 이 되면 기억해 둔 overflow 로 되돌린다 (0 아래로는 내려가지 않는다). */
function unlockScroll() {
  openSheets = Math.max(0, openSheets - 1);
  if (openSheets === 0) document.body.style.overflow = bodyOverflowBefore;
}

/**
 * 바텀시트 본체. open 이 false 면 아무것도 그리지 않는다.
 * @param {object} props
 * @param {boolean} props.open
 * @param {() => void} props.onClose ESC, 배경 탭, 드래그 핸들 탭
 * @param {string} [props.title] 제목 (Heading/S). 없으면 ariaLabel 을 넘길 것
 * @param {string} [props.ariaLabel] 제목이 없을 때 대화상자 이름
 * @param {'md'|'lg'} [props.gap='md'] 세로 간격 16 / 18 (Schedule/AddUnavailable 은 18)
 * @param {import('react').ReactNode} props.children 본문 (버튼 포함)
 * @param {string} [props.className]
 */
export default function Sheet({ open, onClose, title, ariaLabel, gap = 'md', children, className }) {
  // useId: 이 시트만의 고유 id. 제목 h2 의 id 로 쓰고 대화상자의 aria-labelledby 로 이어 준다.
  const titleId = useId();
  const panelRef = useRef(null); // 패널 DOM — 열 때 포커스를 옮기고, Tab 순환 범위를 찾는 데 쓴다
  const pressedOnScrim = useRef(false); // 누르기(pointerdown)가 배경(스크림)에서 시작됐는지
  const lastFocused = useRef(null); // 시트를 열기 직전에 포커스가 있던 요소 — 닫을 때 그리로 돌려준다
  // onClose 는 부모가 렌더마다 새 함수를 넘기기 쉽다(인라인 화살표). 아래 효과가 그때마다 다시 돌면 입력 중에 포커스가 패널로 옮겨가므로,
  // 최신 onClose 는 ref 로 들고 효과는 open 이 바뀔 때만 돈다.
  const onCloseRef = useRef(onClose);
  // 레이아웃 효과로 — 화면이 바뀐 직후 수동 효과가 돌기 전에 ESC 가 오면 한 렌더 전의 onClose(옛 상태를 본 함수)가 불렸다.
  // 일시정지 실패 문구가 뜬 바로 뒤 ESC 로 닫으면 '영상 다시 켜기' 가 빠지던 간헐 실패의 원인 (자체 점검 후속)
  // 의존성 배열이 없어 렌더마다 돈다 — ref 에는 늘 가장 최근 렌더의 onClose 가 들어 있다.
  useLayoutEffect(() => {
    onCloseRef.current = onClose;
  });

  // 열릴 때: ESC 로 닫기, 포커스 이동, 배경 스크롤 잠금. 닫힐 때: 원래 요소로 포커스 복귀.
  useEffect(() => {
    if (!open) return undefined;
    // 아래 return 의 뒷정리는 닫힐 때뿐 아니라 열린 채 화면째 사라질 때(화면 이동)도 불린다 — 그래서 스크롤 잠금이 남지 않는다.
    lastFocused.current = document.activeElement;
    const onKey = (e) => {
      if (e.key === 'Escape') return onCloseRef.current();
      // Tab 이 시트 밖으로 나가지 않게 첫/마지막 요소에서 순환한다
      if (e.key !== 'Tab' || !panelRef.current) return;
      // 시트 안에서 Tab 으로 갈 만한 요소들(DOM 순서). disabled 인 버튼·입력칸은 빼고, tabindex 로만 잡히는 요소는 -1 이면 뺀다.
      // (버튼·링크·입력칸은 tabindex="-1" 이어도 목록에 들어간다 — 그런 요소를 시트 끝에 두면 순환이 어긋난다.)
      const focusables = panelRef.current.querySelectorAll('button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])');
      if (focusables.length === 0) return;
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      // 열린 직후에는 패널 자체(tabIndex=-1)에 포커스가 있다. 이때 Shift+Tab 도 마지막 요소로 보낸다.
      const onPanelItself = document.activeElement === panelRef.current;
      if (e.shiftKey && (document.activeElement === first || onPanelItself)) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKey);
    lockScroll();
    // 포커스를 시트 패널로 옮긴다. 패널은 tabIndex={-1} 이라 Tab 으로는 가지 않고 이렇게 코드로만 포커스된다.
    if (panelRef.current) panelRef.current.focus();
    return () => {
      document.removeEventListener('keydown', onKey);
      unlockScroll();
      if (lastFocused.current && lastFocused.current.focus) lastFocused.current.focus();
    };
  }, [open]);

  // 닫혀 있으면 DOM 에 아무것도 남기지 않는다. 훅들은 이 줄보다 위에 있어야 한다 (훅 호출 순서가 렌더마다 같아야 함).
  if (!open) return null;

  // 구조: 스크림(화면 전체를 덮는 반투명 배경) > 패널(role=dialog) > [핸들 버튼, 제목, children]
  return (
    <div
      className={styles.scrim}
      data-testid="sheet-scrim"
      onPointerDown={(e) => {
        // 누르기 시작한 곳이 배경 자체인지 기억한다 (패널 안에서 시작했으면 false). 아래 onClick 이 이 값을 본다.
        pressedOnScrim.current = e.target === e.currentTarget;
      }}
      onClick={(e) => {
        // 패널 안에서 드래그를 시작해 스크림에서 뗀 경우(click 이 공통 조상인 스크림에서 발생)는 닫지 않는다
        if (e.target !== e.currentTarget || !pressedOnScrim.current) return;
        onClose();
      }}
    >
      {/* 패널: 제목이 있으면 aria-labelledby 로, 없으면 aria-label 로 대화상자 이름을 준다.
          onClick 의 stopPropagation 은 패널 안 클릭이 바깥 스크림의 onClick 으로 올라가지 않게 막는다 */}
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-label={title ? undefined : ariaLabel}
        tabIndex={-1}
        className={cx(styles.sheet, gap === 'lg' && styles.gapLg, className)}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 상단 회색 막대(DragHandle). 끌어서 닫기는 없고 탭하면 닫힌다. 글자가 없어 aria-label 로 이름을 준다 */}
        <button type="button" className={styles.handle} onClick={onClose} aria-label="닫기" />
        {title && (
          <h2 id={titleId} className={cx('text-heading-s', styles.title)}>
            {title}
          </h2>
        )}
        {children}
      </div>
    </div>
  );
}
