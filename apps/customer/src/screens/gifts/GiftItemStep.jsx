// 선물하기 2단계: 무엇을 보낼지 (코드에서 정함 — GiftSend.jsx 가 그린다).
// 방식 두 가지: 새로 구매(서버 선물 목록 = 요금표, 첫 1회 체험 없음) | 내 금액권(선물할 수 있는 것만 — 쓰지 않았고, 유효기간이 넉넉한 것).
import { ScreenLayout, RadioOption, Button, InfoNote } from '@ansimclean/ui';
import { GIFTS_COPY as C } from '../../api/mock.js';
import styles from './gifts.module.css';

/** 요금표 항목을 group 별로 묶는다 (나온 순서 유지) → [[group, items], …] */
function byGroup(items) {
  const groups = new Map();
  for (const i of items) groups.set(i.group, [...(groups.get(i.group) ?? []), i]);
  return [...groups.entries()];
}

/**
 * @param {object} props
 * @param {import('react').ReactNode} props.navBar
 * @param {{ key: string, group: string, label: string, amountLabel: string }[]} props.catalog 선물 목록
 * @param {{ id: string, label: string, balanceLabel: string, validUntilLabel: string|null, giftable: boolean, expiringSoon: boolean }[]} props.vouchers 내 금액권
 * @param {{ method: 'purchase'|'voucher', catalogKey: string|null, voucherId: string|null }} props.draft
 * @param {(patch: object) => void} props.onChange
 * @param {() => void} props.onNext
 */
export default function GiftItemStep({ navBar, catalog, vouchers, draft, onChange, onNext }) {
  const giftable = vouchers.filter((v) => v.giftable);
  const ready = draft.method === 'purchase'
    ? catalog.some((i) => i.key === draft.catalogKey)
    : giftable.some((v) => v.id === draft.voucherId);
  return (
    <ScreenLayout navBar={navBar} gap="md" stickyCta={<Button disabled={!ready} onClick={onNext}>{C.next}</Button>}>
      <h1 className={`text-heading-m ${styles.plain}`}>{C.steps.item}</h1>
      <div className={styles.list} role="radiogroup" aria-label={C.steps.item}>
        {['purchase', 'voucher'].map((m) => (
          <RadioOption
            key={m}
            variant="card"
            label={C.methods[m].label}
            description={C.methods[m].description}
            selected={draft.method === m}
            onSelect={() => onChange({ method: m })}
          />
        ))}
      </div>

      <h2 className={`text-heading-s ${styles.plain}`}>{C.itemTitle[draft.method]}</h2>
      {draft.method === 'purchase' &&
        byGroup(catalog).map(([group, items]) => (
          <section key={group} className={styles.list} role="radiogroup" aria-label={group}>
            <h3 className={`text-title-card ${styles.plain}`}>{group}</h3>
            {items.map((i) => (
              <RadioOption
                key={i.key}
                label={i.label}
                description={i.amountLabel}
                selected={draft.catalogKey === i.key}
                onSelect={() => onChange({ catalogKey: i.key })}
              />
            ))}
          </section>
        ))}
      {draft.method === 'voucher' &&
        (giftable.length === 0 ? (
          <InfoNote tone="neutral">{C.noGiftableVoucher}</InfoNote>
        ) : (
          <div className={styles.list} role="radiogroup" aria-label={C.itemTitle.voucher}>
            {giftable.map((v) => (
              <RadioOption
                key={v.id}
                label={v.label}
                description={v.expiringSoon ? `${C.voucherLine(v)} · ${C.expiringSoon}` : C.voucherLine(v)}
                selected={draft.voucherId === v.id}
                onSelect={() => onChange({ voucherId: v.id })}
              />
            ))}
          </div>
        ))}
    </ScreenLayout>
  );
}
