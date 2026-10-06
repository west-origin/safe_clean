// 오류 객체(useAsync 의 error / http.js 의 ApiError)를 화면 문구로. ErrorState·ScreenError 가 쓴다.
/** 오류 종류별 기본 문구 (검증 검토 — 서버가 만든 한국어 message 를 화면이 버리고 늘 같은 문구를 보여줬다) */
export const ERROR_COPY = {
  default: { title: '잠시 문제가 생겼어요', description: '네트워크 상태를 확인하고 다시 시도해주세요.' },
  network: { title: '네트워크에 연결할 수 없어요', description: '연결 상태를 확인하고 다시 시도해주세요.' },
  notFound: { title: '찾을 수 없어요', description: '삭제됐거나 더 이상 볼 수 없는 정보예요.' },
};

/**
 * error 객체(useAsync 의 error)로 종류를 나눈다: NETWORK → 연결 안내, 404 → "찾을 수 없어요"(다시 시도 없음), 그 외 → 서버 message 가 있으면 그것.
 * @param {{ code?: string, status?: number, message?: string }|null|undefined} error
 * @returns {{ title: string, description: string, retryable: boolean }}
 *   retryable 이 false 면 ErrorState 가 '다시 시도' 버튼을 숨긴다
 */
export function describeError(error) {
  // error 없이 불리면(ErrorState 에 error 를 넘기지 않은 경우) 기본 문구
  if (!error) return { ...ERROR_COPY.default, retryable: true };
  // 두 앱의 api/http.js 가 연결 실패·응답 시간 초과를 code 'NETWORK' 로 던진다 — 서버 문구가 없으니 연결 안내
  if (error.code === 'NETWORK') return { ...ERROR_COPY.network, retryable: true };
  // 404 는 다시 불러도 결과가 같아 재시도를 끈다. 서버 message 가 있으면 설명으로 쓴다
  if (error.status === 404) return { ...ERROR_COPY.notFound, description: error.message || ERROR_COPY.notFound.description, retryable: false };
  return { ...ERROR_COPY.default, description: error.message || ERROR_COPY.default.description, retryable: true };
}

