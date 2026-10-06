// Figma: ServiceCategoryList › ServiceCategoryItem/Home|Move|Appliance|Office. 아이콘은 3D 래스터(WebP 1x/2x/3x).
// 홈 서비스 카테고리의 데이터 표. ServiceCategoryList 가 이 순서대로 그린다.
// 이미지 import 는 Vite 가 빌드 때 파일 주소(URL 문자열)로 바꿔 준다.
import home1 from '../../assets/category-home@1x.webp';
import home2 from '../../assets/category-home@2x.webp';
import home3 from '../../assets/category-home@3x.webp';
import move1 from '../../assets/category-move-in@1x.webp';
import move2 from '../../assets/category-move-in@2x.webp';
import move3 from '../../assets/category-move-in@3x.webp';
import app1 from '../../assets/category-appliance@1x.webp';
import app2 from '../../assets/category-appliance@2x.webp';
import app3 from '../../assets/category-appliance@3x.webp';
import off1 from '../../assets/category-office@1x.webp';
import off2 from '../../assets/category-office@2x.webp';
import off3 from '../../assets/category-office@3x.webp';

/** 1x/2x/3x 주소 세 개로 img srcSet 문자열을 만든다. 예: "a.webp 1x, b.webp 2x, c.webp 3x" */
const set = (a, b, c) => `${a} 1x, ${b} 2x, ${c} 3x`;

// key: onSelect 로 넘어가는 값. width/height: img 에 그대로 넣는 표시 크기(px) — 이미지가 오기 전에도 자리를 잡아 둔다.
/** lines: Figma 의 줄바꿈 그대로 (두 줄 라벨) */
export const SERVICE_CATEGORIES = [
  { key: 'home', lines: ['가정/원룸', '청소'], src: home1, srcSet: set(home1, home2, home3), width: 45, height: 43 },
  { key: 'moveIn', lines: ['입주(이사)', '청소'], src: move1, srcSet: set(move1, move2, move3), width: 45, height: 48 },
  { key: 'appliance', lines: ['가전청소'], src: app1, srcSet: set(app1, app2, app3), width: 45, height: 52 },
  { key: 'office', lines: ['사무실 청소'], src: off1, srcSet: set(off1, off2, off3), width: 45, height: 49 },
];
