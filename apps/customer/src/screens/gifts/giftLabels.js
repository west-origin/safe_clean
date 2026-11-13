// 선물 화면들이 같이 쓰는 표시 함수 (코드에서 정함). 컴포넌트 파일에서 함수를 같이 내보내면 HMR 이 깨져 따로 둔다.
import { GIFTS_COPY as C } from '../../api/mock.js';

/** 선물 상대 표시: 친구 코드(닉네임#태그), 탈퇴했으면 '탈퇴한 사용자' */
export const personLabel = (person) => person?.handle ?? person?.maskedName ?? C.deletedUser;
