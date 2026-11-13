// 친구 초대 링크 공유 (코드에서 정함). 앱(Capacitor)에서는 OS 공유 시트(@capacitor/share — 사용자가 카카오톡을 고른다),
// 웹에서는 navigator.share, 둘 다 없으면 클립보드에 복사한다. 화면은 돌려준 값으로 토스트를 고른다.
import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';

/**
 * @param {{ title: string, text: string, url: string }} invite
 * @returns {Promise<'shared'|'copied'|'cancelled'>}
 */
export async function shareInvite({ title, text, url }) {
  try {
    if (Capacitor.isNativePlatform()) {
      await Share.share({ title, text, url, dialogTitle: title });
      return 'shared';
    }
    if (typeof navigator !== 'undefined' && navigator.share) {
      await navigator.share({ title, text, url });
      return 'shared';
    }
  } catch (e) {
    // 사용자가 공유 시트를 닫으면 오류로 온다 — 실패로 보지 않는다
    if (e?.name === 'AbortError' || /cancel/i.test(e?.message ?? '')) return 'cancelled';
  }
  await navigator.clipboard.writeText(`${text}\n${url}`);
  return 'copied';
}
