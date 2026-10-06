// Figma: 증상 상세와 사진 첨부 (1:455) — StepProgress 3/8 → 질문 → 요약 카드(현재 선택 · 3/4 단계) → 증상 상세(Field/Text) → 사진 첨부 → "다음". 다음 → /address
// 사진은 고르는 즉시 서버(Storage)에 올리고 경로(와 미리보기 URL)만 초안에 둔다 — 새로고침해도 남고, 예약 본문이 가벼워진다 (코드에서 정함)
// 가전수리는 3단계, 다른 수리 종류는 첫 단계다 (수리 탭 Repair.jsx 가 가전수리 외에는 여기로 바로 보낸다).
// 흐름: 사진 고름 → shrinkImage 로 줄임 → uploadRepairPhoto(POST /customer/repair-photos) → 받은 { path, url } 과 올린 시각을 초안 photos 에 추가.
// 사진 만료: 미리보기 URL 과 서버 보관은 24시간뿐이라(booking/RepairPhotoService.PREVIEW_SECONDS·UNATTACHED_KEEP) 24시간 지난 사진은
// 이 화면이 초안에서 빼고 '첨부한 사진이 만료됐어요 …' 를 보인다(repairFlow.js freshPhotos). 확인 화면이 만료로 막혀 이리로 보낼 때도 같은 문구다.
// 화면을 연 채(앱을 내려 둔 사이 포함) 24시간을 넘겨도 useFreshPhotos 가 그 순간 다시 재서 같은 처리를 한다
import { useEffect } from 'react';
import { useNavigate, useParams } from 'react-router';
import { ScreenLayout, StepNavBar, StepQuestion, SelectionSummary, TextField, PhotoPicker, Button, ScreenError, ScreenLoading, useAsync, useAction, ActionError } from '@ansimclean/ui';
import { getRepairBooking, uploadRepairPhoto } from '../../../api/customer.js';
import { shrinkImage } from '../../../api/image.js';
import { useBookingDraft } from '../../booking/bookingDraft.js';
import { draftKey, path, stepOf, serviceName, categoryLabel, hasApplianceSteps, freshPhotos } from './repairFlow.js';
import { useFreshPhotos } from './useFreshPhotos.js';
import { useReturnTo } from '../../booking/useReturnTo.js';
import { INPUT_LIMITS } from '../../../api/mock.js';
import styles from './repair.module.css';

const MAX_PHOTOS = 5; // 첨부할 수 있는 사진 수 (PhotoPicker 의 max 로도 넘긴다)

/** 증상 설명(글)과 사진을 받는 화면. 둘 다 선택 사항이라 비워 둬도 '다음' 으로 갈 수 있다 */
export default function RepairDetail() {
  const navigate = useNavigate();
  const { category } = useParams();
  const back = useReturnTo();
  const [draft, update] = useBookingDraft(draftKey(category));
  const { data: catalog, error, reload } = useAsync(() => getRepairBooking(), []);
  // photos = 화면에 그릴 사진(24시간 안 — 지난 사진은 아래 effect 가 초안에서 빼기 전에도 그리지 않는다, 깨진 썸네일),
  // expired = 초안에 24시간 지난 사진이 있다. 화면을 연 채 만료 시각을 넘기거나 앱이 돌아오면 다시 잰다
  const { photos, expired } = useFreshPhotos(draft.photos);
  // 24시간 지난 사진을 초안(sessionStorage)에서 빼고 만료 안내 표시(photosExpired)를 켠다 — 렌더와 같은 판정(expired)을 보고 effect 에서만 바꾼다.
  // 예전에는 초안의 사진 목록이 바뀔 때만 검사해, 화면을 연 채 만료되면 사진이 안내 없이 격자에서만 사라지고 초안에는 남았다 (합친 38건 검토 R10).
  // 안내는 새 사진을 올리거나 '다음' 으로 떠날 때 끈다. 확인 화면이 만료로 사진을 비우고 보낼 때도 photosExpired 를 켜서 온다
  useEffect(() => {
    if (expired) update((prev) => ({ photos: freshPhotos(prev.photos), photosExpired: true }));
  }, [expired, update]);
  // 고른 사진들을 한 장씩 차례로 올린다. files 는 PhotoPicker 가 넘기는 File 배열
  const upload = useAction(async (files) => {
    // 남은 자리만큼만 올린다 — 한 번에 여러 장을 골라 5장을 넘으면 뒤쪽 사진은 버린다 (만료된 사진은 자리를 차지하지 않는다)
    const room = MAX_PHOTOS - freshPhotos(draft.photos).length;
    let failed = 0;
    let lastError = null;
    for (const file of files.slice(0, room)) {
      try {
        const u = await uploadRepairPhoto(await shrinkImage(file));
        // 함수 형태 update: 반복 중에는 위의 draft 가 옛 값이라, 저장된 최신 초안(prev)에 이어 붙인다. id 는 path 를 그대로 쓰고 src 는 미리보기 주소,
        // uploadedAt 은 24시간 만료를 재는 기준(응답을 받은 기기 시각). 새 사진이 들어왔으니 만료 안내는 끈다
        const photo = { id: u.path, path: u.path, src: u.url, uploadedAt: Date.now() };
        update((prev) => ({ photos: [...freshPhotos(prev.photos), photo], photosExpired: undefined })); // 성공한 장부터 반영 — 한 장이 실패해도 앞 장은 남는다 (검수 3차)
      } catch (e) {
        failed += 1;
        lastError = e;
      }
    }
    // 실패가 있으면 던져서 useAction 의 error 로 보여 준다. 여러 장이면 몇 장 실패했는지, 한 장이면 받은 오류의 문구(없으면 기본 문구)
    if (failed) throw Object.assign(new Error(files.length > 1 ? `사진 ${failed}장을 올리지 못했어요. 다시 시도해주세요.` : lastError?.message || '사진을 올리지 못했어요. 다시 시도해주세요.'), { cause: lastError });
  });
  const { step, total } = stepOf(category, 'detail');
  // 가전수리는 앞 단계(증상)로, 다른 수리 종류는 이 화면이 첫 단계라 수리 탭으로 돌아간다
  const navBar = <StepNavBar step={step} total={total} onBack={() => navigate(hasApplianceSteps(category) ? -1 : '/repair')} />;
  if (error) return <ScreenError error={error} onRetry={reload} navBar={navBar} />;
  if (!catalog) return <ScreenLoading navBar={navBar} />;

  const copy = catalog.copy.detail;
  const appliance = catalog.appliances.find((a) => a.key === draft.appliance);
  // '현재 선택' 요약 카드의 행. 가전을 고른 경우(가전수리)에만 '가전' 행이 들어간다
  const rows = [
    { label: copy.rows.service, value: categoryLabel(category) },
    ...(appliance ? [{ label: copy.rows.appliance, value: appliance.label }] : []),
    { label: copy.rows.photos, value: copy.photosValue(photos.length) },
  ];

  return (
    <ScreenLayout noPadding gap="none" navBar={navBar}>
      <div className={styles.body}>
        <div className={styles.stack}>
          <StepQuestion service={serviceName(category, catalog, draft)} title={copy.title} description={copy.description} />
          {/* 칩 문구: 가전수리(step 3, total 8)에서 Figma 의 "3/4 단계" 가 된다. 다른 수리 종류(step 1, total 6)는 "1/2 단계" */}
          <SelectionSummary title={copy.summaryTitle} badge={`${step}/${total - 4} 단계`} rows={rows} />
          <TextField label={copy.fieldLabel} placeholder={copy.fieldPlaceholder} value={draft.detail ?? ''} onChange={(e) => update({ detail: e.target.value })} maxLength={INPUT_LIMITS.repairDetail} />
          {/* 사진을 탭해 지우면 초안 목록에서만 뺀다 (이 화면은 서버에 삭제 요청을 보내지 않는다 — 예약에 붙지 않은 파일은 올린 지 24시간이 지나면 서버 RepairPhotoCleaner 가 다음 새벽 정리 때 지운다) */}
          <PhotoPicker
            title={copy.photosTitle}
            helper={copy.photosHelper}
            photos={photos}
            max={MAX_PHOTOS}
            disabled={upload.loading}
            onAdd={(files) => upload.run(files)}
            onRemove={(id) => update((prev) => ({ photos: (prev.photos ?? []).filter((p) => p.id !== id) }))}
          />
          {/* 만료 안내: 24시간 지난 사진을 뺐거나 확인 화면이 만료 오류로 보냈을 때 (role="alert" 라 나타나면 스크린리더가 읽는다) */}
          <ActionError error={draft.photosExpired ? { message: copy.photosExpired } : null} />
          <ActionError error={upload.error} fallback={'사진을 올리지 못했어요. 다시 시도해주세요.'} />
        </div>
        <div className={styles.cta}>
          {/* 사진을 올리는 동안에는 사진 추가와 '다음' 을 막는다 — 올리는 중인 사진이 초안에 들어가기 전에 화면을 떠나지 않게.
              떠날 때 만료 안내를 끈다 — 나중에 '수정' 으로 다시 와도 지난 안내가 남지 않게 */}
          <Button variant="primary" className={styles.ctaButton} disabled={upload.loading} onClick={() => {
            if (draft.photosExpired) update({ photosExpired: undefined });
            navigate(back.next(path(category, 'address')));
          }}>
            다음
          </Button>
        </div>
      </div>
    </ScreenLayout>
  );
}
