// Step2의 '아니요' 아래에 검색과 상세주소 입력을 함께 보여준다. 별도 화면으로 이동하지 않는다.
import { useEffect, useState } from 'react';
import { SearchField, AddressResultList, TextField, InfoNote, cx, useAsync } from '@ansimclean/ui';
import { searchAddress } from '../../api/customer.js';
import { ADDRESS_SEARCH_COPY, INPUT_LIMITS } from '../../api/mock.js';
import common from './booking.module.css';
import searchStyles from './BookingAddressSearch.module.css';
import styles from './BookingStep2.module.css';

/** @param {{ draft: object, onChange: (patch: object) => void }} props 예약 초안과 변경 함수 */
export default function BookingNewAddress({ draft, onChange }) {
  const query = draft.addressQuery ?? '';
  const road = draft.roadAddress ?? '';
  const detail = draft.detailAddress ?? '';
  const [searchQuery, setSearchQuery] = useState(query);
  useEffect(() => {
    const timer = setTimeout(() => setSearchQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);
  const { data, loading, error } = useAsync(() => searchAddress(searchQuery), [searchQuery]);
  const searching = loading || searchQuery !== query;
  const results = searching ? [] : (data?.results ?? []);

  return (
    <div className={cx(common.section, styles.newAddress)}>
      <section className={searchStyles.searchBlock} aria-labelledby="new-address-search-title">
        <h2 id="new-address-search-title" className={cx('text-heading-m', common.sectionTitle)}>
          주소를 검색해주세요
        </h2>
        <div className={searchStyles.fieldAndMap}>
          <SearchField
            filled
            ariaLabel="주소 검색"
            value={query}
            autoComplete="street-address"
            onChange={(e) =>
              onChange({ addressQuery: e.target.value, roadAddress: '', detailAddress: '' })
            }
          />
          {!road.trim() &&
            (query.trim().length < 2 ? (
              <InfoNote glyph="ⓘ">{ADDRESS_SEARCH_COPY.guide}</InfoNote>
            ) : searching ? (
              <p className="text-body-s" role="status">
                주소를 검색하고 있어요.
              </p>
            ) : error ? (
              <InfoNote glyph="!">
                {error.message || '주소 검색에 실패했어요. 도로명주소를 직접 입력해주세요.'}
              </InfoNote>
            ) : results.length > 0 ? (
              <AddressResultList
                results={results}
                onSelect={(r) => onChange({ roadAddress: r.main, detailAddress: '' })}
              />
            ) : (
              <InfoNote glyph="ⓘ">{ADDRESS_SEARCH_COPY.noResults}</InfoNote>
            ))}
        </div>
      </section>
      <section className={styles.addressForm} aria-label="새 주소 입력">
        <TextField
          label="도로명주소"
          placeholder="서울특별시 강남구 테헤란로 123"
          value={road}
          autoComplete="address-line1"
          maxLength={INPUT_LIMITS.roadAddress}
          onChange={(e) => onChange({ roadAddress: e.target.value, detailAddress: '' })}
        />
        <TextField
          label="상세주소"
          placeholder="604동 1002호"
          value={detail}
          autoComplete="address-line2"
          maxLength={INPUT_LIMITS.detailAddress}
          onChange={(e) => onChange({ detailAddress: e.target.value })}
        />
        <InfoNote glyph="ⓘ">
          도로명주소와 상세주소를 모두 입력하면 다음 단계로 진행할 수 있어요.
        </InfoNote>
      </section>
    </div>
  );
}
