// Figma: Booking/Step2 (92:242) — "기존 주소를 사용할까요?" 네/아니오 + "옵션 선택" 필드. 4 카테고리 동일.
// 어떤 주소를 '기존 주소' 로 쓰는지 화면에 안 보여 무엇에 동의하는지 알 수 없었다 → 기본 주소를 한 줄로 보여 준다 (사용성 검토 2026-09-22)
// 프로토타입 연결 없음 → 네 = /options (주소 단계 건너뜀), 아니오 = /address-search. 옵션 필드 탭 = /options, 단 "아니오" 인데 새 주소가 아직 없으면 주소 검색으로 (PROGRESS.md 판단 항목)
// 예약 플로우 3단계 (경로 /booking/:category/step2). 고른 값은 예약 초안(bookingDraft)의 reuseAddress('yes' | 'no')에 둔다.
// '기존 주소' 는 회원 기본 주소(GET /customer/addresses/default)이고, 없으면 서버가 404 → null 로 받아 안내 문구를 보인다.
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, RadioOption, SelectField, Button, cx, useAsync, ScreenError, ScreenLoading } from '@ansimclean/ui';
import { getAddressReuseOptions, getSavedAddress, saveBookingStep } from '../../api/customer.js';
import { useBookingDraft } from './bookingDraft.js';
import common from './booking.module.css';
import { useReturnTo } from './useReturnTo.js';
import styles from './BookingStep2.module.css';

/** Booking/Step2 화면. 기존 주소를 쓸지 고르고, 옵션 선택 칸에는 옵션 화면에서 만든 요약 문구를 보인다 */
export default function BookingStep2() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo(); // 확인 화면 '수정하기'(?returnTo=)에서 왔으면 그 화면으로 돌아간다 (수리 예약은 이 화면을 쓰지 않는다)
  const [draft, update] = useBookingDraft(category);
  // 선택지(네/아니오, 앱 안 목록)와 기본 주소(서버)를 한 번에 받는다
  const { data, error, reload } = useAsync(async () => ({ options: await getAddressReuseOptions(), saved: await getSavedAddress() }), []);


  if (error) return <ScreenError error={error} onRetry={reload} navBar={<StepNavBar step={3} onBack={() => navigate(-1)} />} />;
  if (!data) return <ScreenLoading navBar={<StepNavBar step={3} onBack={() => navigate(-1)} />} />;
  const { options, saved } = data;
  // 도로명 주소 + 상세 주소를 한 줄로 (상세 주소가 비었으면 도로명만)
  const savedLine = saved ? [saved.roadAddress, saved.detailAddress].filter(Boolean).join(' ') : null;

  const reuse = draft.reuseAddress ?? 'yes'; // Figma 기본 선택: 네
  // "아니오" 를 골랐는데 새 주소를 아직 안 넣었으면 어느 버튼으로 가든 주소 검색부터 (기존 주소로 예약되는 일이 없게)
  const needsAddress = reuse === 'no' && !draft.roadAddress;

  // '다음': 화면에 보이는 선택(아무것도 안 눌렀으면 기본값 '네')을 초안에 적고 이동한다.
  // 네 → 옵션 화면(또는 returnTo). 아니오 → 주소 검색 → 주소 확인 → 옵션 화면 순서이고,
  // returnTo 가 있으면 carry 로 붙여 넘겨 주소 확인 화면이 그 곳으로 돌아가게 한다
  const next = async () => {
    await saveBookingStep(category, 'step2', { reuseAddress: reuse });
    update({ reuseAddress: reuse });
    navigate(reuse === 'yes' ? back.next(`/booking/${category}/options`) : back.carry(`/booking/${category}/address-search`)); // 새 주소면 검색·확인을 거친 뒤 돌아온다
  };

  return (
    <ScreenLayout noPadding gap="none" navBar={<StepNavBar step={3} onBack={() => navigate(-1)} />}>
      <div className={cx(common.body, styles.body)}>
        <div className={cx(common.stack, styles.stack)}>
          <section className={common.section}>
            <h1 className={cx('text-heading-m', common.sectionTitle)}>기존 주소를 사용할까요?</h1>
            <div className={common.options} role="radiogroup" aria-label="기존 주소 사용">
              {options.map((o) => (
                <RadioOption key={o.key} label={o.label} selected={reuse === o.key} onSelect={() => update({ reuseAddress: o.key })} />
              ))}
            </div>
            {/* 무엇이 '기존 주소' 인지 항상 같은 자리에 보여 준다 (고른 뒤에 나타나면 아래 버튼이 밀린다) */}
            <p className={cx('text-body-s', styles.savedAddress)}>{savedLine ? `기존 주소: ${savedLine}` : '저장된 주소가 없어요. "아니오" 를 골라 주소를 입력해주세요.'}</p>
          </section>

          <section className={common.section}>
            <h2 className={cx('text-heading-m', common.sectionTitle)}>옵션 선택</h2>
            <SelectField placeholder="옵션을 선택해주세요" value={draft.optionsSummary} onClick={() => navigate(`/booking/${category}/${needsAddress ? 'address-search' : 'options'}`)} />
          </section>
        </div>

        <div className={common.cta}>
          <Button variant="primary" onClick={next}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
