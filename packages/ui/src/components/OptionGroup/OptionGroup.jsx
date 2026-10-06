// Figma(수리 예약): 가전 종류(1:506) — 연분홍 배경(fff7f5 원본색 → extra.css --raw-repair-option-bg) + 1px border/default + radius md, 패딩 4, 옵션 간격 10
//                  고장 증상(1:528) — 같은 배경, 테두리 없음, radius sm
//
// 여러 선택지 중 하나만 고르는 목록. 선택지 한 줄은 RadioOption 이 그린다.
// 예: 고객 앱 수리 예약의 가전 종류(RepairAppliance, 기본 모양), 고장 증상(RepairSymptom, bordered={false}).
// 선택한 key 는 부모(화면)가 들고 있고, 이 컴포넌트는 눌린 key 를 onChange 로 알리기만 한다.
import RadioOption from '../RadioOption/RadioOption.jsx';
import { cx } from '../../utils/cx.js';
import styles from './OptionGroup.module.css';

/**
 * 단일 선택 옵션 묶음 (RadioOption 을 라디오 그룹으로).
 * @param {object} props
 * @param {{ key: string, label: string }[]} props.options
 * @param {string} [props.value] 선택한 key
 * @param {(key: string) => void} [props.onChange]
 * @param {string} props.ariaLabel 그룹 이름 (예: "가전 종류")
 * @param {boolean} [props.bordered=true] 1px 테두리 + radius md (가전 종류). false 면 radius sm (고장 증상)
 * @param {string} [props.className]
 */
export default function OptionGroup({ options, value, onChange, ariaLabel, bordered = true, className }) {
  // role="radiogroup" + aria-label 로 보조기기가 "가전 종류, 라디오 그룹" 처럼 묶음 이름을 읽는다.
  // 각 줄은 key 가 value 와 같을 때만 선택 표시가 된다.
  return (
    <div role="radiogroup" aria-label={ariaLabel} className={cx(styles.group, bordered ? styles.bordered : styles.plain, className)}>
      {options.map((o) => (
        <RadioOption key={o.key} label={o.label} selected={o.key === value} onSelect={() => onChange && onChange(o.key)} />
      ))}
    </div>
  );
}
