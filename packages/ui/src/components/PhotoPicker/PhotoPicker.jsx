// Figma(수리 예약): 사진 첨부(1:483) — 제목(Title/Small) → 사진 목록(가로 10 간격: 사진 추가 104×104 surface/subtle + 1px border, radius sm / 첨부 사진 104×104 radius sm cover) → 도움말(Caption/L secondary)
// 사진 삭제 UI 는 Figma 에 없음 — 첨부 사진을 탭하면 지운다 (코드에서 정함, PROGRESS.md 판단 항목)
//
// 사진 첨부 칸. 예: 고객 앱 수리 예약의 상세 입력 화면(RepairDetail).
// 이 컴포넌트는 파일을 고르게 하고 목록을 그리며, max 장이 차면 추가 칸을 숨기기만 한다.
// 올리기(업로드)·지우기, 한 번에 여러 장을 골랐을 때 남은 장수로 자르기는 화면이 onAdd/onRemove 에서 한다
// (RepairDetail 은 남은 장수만큼만 올리고, 올린 사진을 photos 로 다시 넘긴다).
import { useRef } from 'react';
import { cx } from '../../utils/cx.js';
import styles from './PhotoPicker.module.css';

/**
 * @param {object} props
 * @param {string} [props.title='사진 첨부 · 선택']
 * @param {{ id: string, src: string }[]} props.photos 첨부한 사진 (미리보기 URL)
 * @param {(files: File[]) => void} [props.onAdd] 파일을 골랐을 때
 * @param {(id: string) => void} [props.onRemove] 첨부 사진을 탭했을 때
 * @param {number} [props.max=5] 최대 장수 — 차면 추가 칸을 숨긴다
 * @param {string} [props.helper] 도움말
 * @param {boolean} [props.disabled] 업로드 중 등
 * @param {string} [props.className]
 * disabled 는 "사진 추가" 칸에만 걸린다. 이미 붙은 사진을 탭해 지우는 것은 막지 않는다.
 */
export default function PhotoPicker({ title = '사진 첨부 · 선택', photos, onAdd, onRemove, max = 5, helper, disabled, className }) {
  // 숨겨 둔 <input type="file"> 을 가리키는 참조(useRef). "사진 추가" 버튼을 누르면 이 input 을 대신 눌러 파일 고르기 창을 연다.
  const input = useRef(null);
  // 파일 고르기 창에서 고른 파일들을 배열로 바꿔 onAdd 에 넘긴다. 아무것도 안 고르고 닫으면 부르지 않는다.
  const pick = (e) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = ''; // 같은 파일을 다시 고를 수 있게
    if (files.length && onAdd) onAdd(files);
  };
  // 순서: [사진 추가 칸(장수가 max 보다 적을 때만)] → [첨부 사진들] → 숨긴 파일 input.
  // 파일 input 은 눈에 안 보이게만 하고(CSS .input) tabIndex={-1} 로 키보드 이동에서 뺀다 — 들어가는 입구는 "사진 추가" 버튼 하나다.
  // 첨부 사진 버튼 이름은 "사진 N 삭제" 로, 누르면 지워진다는 것을 보조기기에도 알린다.
  return (
    <section className={cx(styles.picker, className)} aria-label={title}>
      <h2 className={cx('text-title-small', styles.title)}>{title}</h2>
      <div className={styles.list}>
        {photos.length < max && (
          <button type="button" className={styles.add} disabled={disabled} onClick={() => input.current && input.current.click()}>
            <span className={cx('text-heading-m', styles.plus)} aria-hidden="true">＋</span>
            <span className={cx('text-caption-l', styles.addLabel)}>사진 추가</span>
          </button>
        )}
        {photos.map((p, i) => (
          <button key={p.id} type="button" className={styles.photo} aria-label={`사진 ${i + 1} 삭제`} onClick={() => onRemove && onRemove(p.id)}>
            <img src={p.src} alt="" className={styles.image} />
          </button>
        ))}
        <input ref={input} type="file" accept="image/*" multiple className={styles.input} onChange={pick} aria-label="사진 파일 선택" tabIndex={-1} />
      </div>
      {helper && <p className={cx('text-caption-l', styles.helper)}>{helper}</p>}
    </section>
  );
}
