// 래스터 에셋 목록 (WebP 1x/2x/3x). 화면은 여기서 가져다 <img src srcSet> 로 쓴다.
// 원본: Figma PNG → PIL 로 변환 (CLAUDE.md §8). 온보딩 슬라이드는 원본이 2x 크기라 1x/2x 만 있다.
// 이미지 파일을 import 하면 Vite 가 그 주소(URL 문자열)를 변수에 넣어 준다. 빌드 때 파일은 결과물(dist)로 복사되고,
// 4KB 보다 작은 파일은 복사 대신 data: 주소로 코드 안에 바로 들어간다 (Vite 기본 동작).
// 변수 이름 끝의 1·2·3(또는 _1·_2·_3)은 @1x·@2x·@3x 파일이다.
// 새 이미지를 더할 때는 이 폴더에 파일을 넣고 → 여기 import 를 쓰고 → 아래 ASSETS 에 항목을 추가한다.
import slide1x1 from './onboarding-slide1@1x.webp';
import slide1x2 from './onboarding-slide1@2x.webp';
import slide2x1 from './onboarding-slide2@1x.webp';
import slide2x2 from './onboarding-slide2@2x.webp';
import slide3x1 from './onboarding-slide3@1x.webp';
import slide3x2 from './onboarding-slide3@2x.webp';
import kakao1 from './social-kakao@1x.webp';
import kakao2 from './social-kakao@2x.webp';
import kakao3 from './social-kakao@3x.webp';
import google1 from './social-google@1x.webp';
import google2 from './social-google@2x.webp';
import google3 from './social-google@3x.webp';
import naver1 from './social-naver@1x.webp';
import naver2 from './social-naver@2x.webp';
import naver3 from './social-naver@3x.webp';
import phone1 from './signup-phone@1x.webp';
import phone2 from './signup-phone@2x.webp';
import phone3 from './signup-phone@3x.webp';
import hero1 from './signup-hero@1x.webp';
import hero2 from './signup-hero@2x.webp';
import hero3 from './signup-hero@3x.webp';
import heroRoom1 from './hero-room@1x.webp';
import heroRoom2 from './hero-room@2x.webp';
import heroRoom3 from './hero-room@3x.webp';
import heroPeek1 from './hero-peek@1x.webp';
import heroPeek2 from './hero-peek@2x.webp';
import heroPeek3 from './hero-peek@3x.webp';
import note1 from './terms-note-icon@1x.webp';
import note2 from './terms-note-icon@2x.webp';
import note3 from './terms-note-icon@3x.webp';
import hApt1 from './housing-apartment@1x.webp';
import hApt2 from './housing-apartment@2x.webp';
import hApt3 from './housing-apartment@3x.webp';
import hStu1 from './housing-studio@1x.webp';
import hStu2 from './housing-studio@2x.webp';
import hStu3 from './housing-studio@3x.webp';
import hHou1 from './housing-house@1x.webp';
import hHou2 from './housing-house@2x.webp';
import hHou3 from './housing-house@3x.webp';
import hOth1 from './housing-other@1x.webp';
import hOth2 from './housing-other@2x.webp';
import hOth3 from './housing-other@3x.webp';
import hInfo1 from './housing-info-icon@1x.webp';
import hInfo2 from './housing-info-icon@2x.webp';
import hInfo3 from './housing-info-icon@3x.webp';
import petYes1 from './pet-yes@1x.webp';
import petYes2 from './pet-yes@2x.webp';
import petYes3 from './pet-yes@3x.webp';
import petNo1 from './pet-no@1x.webp';
import petNo2 from './pet-no@2x.webp';
import petNo3 from './pet-no@3x.webp';
import rApp1 from './repair-appliance@1x.webp';
import rApp2 from './repair-appliance@2x.webp';
import rApp3 from './repair-appliance@3x.webp';
import rFur1 from './repair-furniture@1x.webp';
import rFur2 from './repair-furniture@2x.webp';
import rFur3 from './repair-furniture@3x.webp';
import rPlu1 from './repair-plumbing@1x.webp';
import rPlu2 from './repair-plumbing@2x.webp';
import rPlu3 from './repair-plumbing@3x.webp';
import rEle1 from './repair-electric@1x.webp';
import rEle2 from './repair-electric@2x.webp';
import rEle3 from './repair-electric@3x.webp';
import rClog1 from './repair-toilet-clog@1x.webp';
import rClog2 from './repair-toilet-clog@2x.webp';
import rClog3 from './repair-toilet-clog@3x.webp';
import rToi1 from './repair-toilet-repair@1x.webp';
import rToi2 from './repair-toilet-repair@2x.webp';
import rToi3 from './repair-toilet-repair@3x.webp';
import rDoor1 from './repair-door@1x.webp';
import rDoor2 from './repair-door@2x.webp';
import rDoor3 from './repair-door@3x.webp';
import rOth1 from './repair-other@1x.webp';
import rOth2 from './repair-other@2x.webp';
import rOth3 from './repair-other@3x.webp';
import rHero1 from './repair-hero@1x.webp';
import rHero2 from './repair-hero@2x.webp';
import rHero3 from './repair-hero@3x.webp';
import hrt1 from './hero-realtime@1x.webp';
import hrt2 from './hero-realtime@2x.webp';
import hrt3 from './hero-realtime@3x.webp';
import hrtPeek1 from './hero-realtime-peek@1x.webp';
import hrtPeek2 from './hero-realtime-peek@2x.webp';
import hrtPeek3 from './hero-realtime-peek@3x.webp';
import addr1 from './address-results-preview@1x.webp';
import addr2 from './address-results-preview@2x.webp';
import optHome1_1 from './option-home-1@1x.webp';
import optHome1_2 from './option-home-1@2x.webp';
import optHome1_3 from './option-home-1@3x.webp';
import optHome2_1 from './option-home-2@1x.webp';
import optHome2_2 from './option-home-2@2x.webp';
import optHome2_3 from './option-home-2@3x.webp';
import optHome3_1 from './option-home-3@1x.webp';
import optHome3_2 from './option-home-3@2x.webp';
import optHome3_3 from './option-home-3@3x.webp';
import optHome4_1 from './option-home-4@1x.webp';
import optHome4_2 from './option-home-4@2x.webp';
import optHome4_3 from './option-home-4@3x.webp';
import optHome5_1 from './option-home-5@1x.webp';
import optHome5_2 from './option-home-5@2x.webp';
import optHome5_3 from './option-home-5@3x.webp';
import optOffice1_1 from './option-office-1@1x.webp';
import optOffice1_2 from './option-office-1@2x.webp';
import optOffice1_3 from './option-office-1@3x.webp';
import optOffice2_1 from './option-office-2@1x.webp';
import optOffice2_2 from './option-office-2@2x.webp';
import optOffice2_3 from './option-office-2@3x.webp';
import optOffice3_1 from './option-office-3@1x.webp';
import optOffice3_2 from './option-office-3@2x.webp';
import optOffice3_3 from './option-office-3@3x.webp';
import optOffice4_1 from './option-office-4@1x.webp';
import optOffice4_2 from './option-office-4@2x.webp';
import optOffice4_3 from './option-office-4@3x.webp';
import optOffice5_1 from './option-office-5@1x.webp';
import optOffice5_2 from './option-office-5@2x.webp';
import optOffice5_3 from './option-office-5@3x.webp';
import optAppliance1_1 from './option-appliance-1@1x.webp';
import optAppliance1_2 from './option-appliance-1@2x.webp';
import optAppliance1_3 from './option-appliance-1@3x.webp';
import optAppliance2_1 from './option-appliance-2@1x.webp';
import optAppliance2_2 from './option-appliance-2@2x.webp';
import optAppliance2_3 from './option-appliance-2@3x.webp';
import optAppliance3_1 from './option-appliance-3@1x.webp';
import optAppliance3_2 from './option-appliance-3@2x.webp';
import optAppliance3_3 from './option-appliance-3@3x.webp';
import optAppliance4_1 from './option-appliance-4@1x.webp';
import optAppliance4_2 from './option-appliance-4@2x.webp';
import optAppliance4_3 from './option-appliance-4@3x.webp';
import optAppliance5_1 from './option-appliance-5@1x.webp';
import optAppliance5_2 from './option-appliance-5@2x.webp';
import optAppliance5_3 from './option-appliance-5@3x.webp';
import optMovein1_1 from './option-movein-1@1x.webp';
import optMovein1_2 from './option-movein-1@2x.webp';
import optMovein1_3 from './option-movein-1@3x.webp';
import optMovein2_1 from './option-movein-2@1x.webp';
import optMovein2_2 from './option-movein-2@2x.webp';
import optMovein2_3 from './option-movein-2@3x.webp';
import optMovein3_1 from './option-movein-3@1x.webp';
import optMovein3_2 from './option-movein-3@2x.webp';
import optMovein3_3 from './option-movein-3@3x.webp';
import optMovein4_1 from './option-movein-4@1x.webp';
import optMovein4_2 from './option-movein-4@2x.webp';
import optMovein4_3 from './option-movein-4@3x.webp';
import optMovein5_1 from './option-movein-5@1x.webp';
import optMovein5_2 from './option-movein-5@2x.webp';
import optMovein5_3 from './option-movein-5@3x.webp';
import cSvc_1 from './confirm-service@1x.webp';
import cSvc_2 from './confirm-service@2x.webp';
import cSvc_3 from './confirm-service@3x.webp';
import cAddr_1 from './confirm-address@1x.webp';
import cAddr_2 from './confirm-address@2x.webp';
import cAddr_3 from './confirm-address@3x.webp';
import cSch_1 from './confirm-schedule@1x.webp';
import cSch_2 from './confirm-schedule@2x.webp';
import cSch_3 from './confirm-schedule@3x.webp';
import cFreq_1 from './confirm-frequency@1x.webp';
import cFreq_2 from './confirm-frequency@2x.webp';
import cFreq_3 from './confirm-frequency@3x.webp';
import cOpt_1 from './confirm-options@1x.webp';
import cOpt_2 from './confirm-options@2x.webp';
import cOpt_3 from './confirm-options@3x.webp';
import cNote_1 from './confirm-notes@1x.webp';
import cNote_2 from './confirm-notes@2x.webp';
import cNote_3 from './confirm-notes@3x.webp';
import cReq_1 from './confirm-requests@1x.webp';
import cReq_2 from './confirm-requests@2x.webp';
import cReq_3 from './confirm-requests@3x.webp';
import cHero_1 from './confirm-hero@1x.webp';
import cHero_2 from './confirm-hero@2x.webp';
import cHero_3 from './confirm-hero@3x.webp';
import tip_1 from './request-tip-icon@1x.webp';
import tip_2 from './request-tip-icon@2x.webp';
import tip_3 from './request-tip-icon@3x.webp';
import avatar1 from './manager-avatar@1x.webp';
import avatar2 from './manager-avatar@2x.webp';
import avatar3 from './manager-avatar@3x.webp';
import start1 from './service-start@1x.webp';
import start2 from './service-start@2x.webp';
import bcomplete_1 from './booking-complete@1x.webp';
import bcomplete_2 from './booking-complete@2x.webp';
import complete1 from './signup-complete@1x.webp';
import complete2 from './signup-complete@2x.webp';

// <img srcSet> 에 넣을 문자열을 만든다. 브라우저가 화면 배율(1x·2x·3x)에 맞는 파일 하나만 골라 내려받는다.
// set2 는 3x 파일이 없는 이미지용 (3x 화면에서는 2x 를 확대해 쓴다)
const set3 = (a, b, c) => `${a} 1x, ${b} 2x, ${c} 3x`;
const set2 = (a, b) => `${a} 1x, ${b} 2x`;

// 항목마다 { src: 1x 주소, srcSet, width, height }. width·height 는 1x 기준 표시 크기(px)로,
// 화면이 <img width height> 로 넘기면 이미지가 도착하기 전에도 자리를 잡아 화면이 밀리지 않는다.
// 주석의 괄호 안 숫자(예: 67:678)는 Figma 노드 ID 다.
export const ASSETS = {
  /** Onboarding/Slide1~3 (67:678 · 67:667 · 68:1052) — 480×852 full-bleed, 문구는 이미지에 포함 */
  onboardingSlides: [
    { src: slide1x1, srcSet: set2(slide1x1, slide1x2), width: 480, height: 852 },
    { src: slide2x1, srcSet: set2(slide2x1, slide2x2), width: 480, height: 852 },
    { src: slide3x1, srcSet: set2(slide3x1, slide3x2), width: 480, height: 852 },
  ],
  /** Auth/SignUp › Button/SocialLogin-* (79:189 · 79:196 · 79:199), 300×100 flat image */
  socialKakao: { src: kakao1, srcSet: set3(kakao1, kakao2, kakao3), width: 300, height: 100 },
  socialGoogle: { src: google1, srcSet: set3(google1, google2, google3), width: 300, height: 100 },
  socialNaver: { src: naver1, srcSet: set3(naver1, naver2, naver3), width: 300, height: 100 },
  /** Auth/SignUp › Button/SignUpWithPhone (80:413) */
  signUpPhone: { src: phone1, srcSet: set3(phone1, phone2, phone3), width: 300, height: 100 },
  /** Auth/SignUp › HeroArt (96:158) 262×253 */
  signUpHero: { src: hero1, srcSet: set3(hero1, hero2, hero3), width: 262, height: 253 },
  /** Auth/LoginPhone › HeroArt (96:156) · Home/* › HeroArt (95:139) 262×263 — SignUp 과 같은 원본 이미지, 자르기(crop)만 다름 */
  heroRoom: { src: heroRoom1, srcSet: set3(heroRoom1, heroRoom2, heroRoom3), width: 262, height: 263 },
  /** Home/* › HeroArtPeek (96:154) 51×155 — 같은 원본의 다른 부분(이전 슬라이드가 왼쪽에 살짝 보이는 장식) */
  heroPeek: { src: heroPeek1, srcSet: set3(heroPeek1, heroPeek2, heroPeek3), width: 51, height: 155 },
  /** Auth/TermsConsent › PrivacyNoteCard › Icon (79:294) 50×48 3D 자물쇠 */
  termsNoteIcon: { src: note1, srcSet: set3(note1, note2, note3), width: 50, height: 48 },
  /** Auth/ProfileHousing › HousingTypeItem › Icon (83:142 · 84:157 · 83:143 · 84:159) 3D 건물 */
  housingApartment: { src: hApt1, srcSet: set3(hApt1, hApt2, hApt3), width: 79, height: 65 },
  housingStudio: { src: hStu1, srcSet: set3(hStu1, hStu2, hStu3), width: 74, height: 65 },
  housingHouse: { src: hHou1, srcSet: set3(hHou1, hHou2, hHou3), width: 80, height: 60 },
  housingOther: { src: hOth1, srcSet: set3(hOth1, hOth2, hOth3), width: 78, height: 60 },
  /** Auth/ProfileHousing › InfoBox › Icon (84:164) 3D 전구 */
  housingInfoIcon: { src: hInfo1, srcSet: set3(hInfo1, hInfo2, hInfo3), width: 50, height: 49 },
  /** Auth/ProfileHousing › PetItem › Icon (84:183 · 84:190) */
  petYes: { src: petYes1, srcSet: set3(petYes1, petYes2, petYes3), width: 100, height: 96 },
  petNo: { src: petNo1, srcSet: set3(petNo1, petNo2, petNo3), width: 100, height: 100 },
  /** Auth/SignUpComplete › CompleteIllustration (flat image) (84:198) 401×852 — 문구·버튼이 이미지에 포함. 원본이 2x 크기라 1x/2x 만 */
  signUpComplete: { src: complete1, srcSet: set2(complete1, complete2), width: 401, height: 852 },
  /** Booking/AddressSearch › MapPreview (flat image) (92:398) 346×231 — 주소 검색 결과 목록이 그려진 평면 이미지 (실제 목록은 주소 API 연동 시 교체) */
  addressResultsPreview: { src: addr1, srcSet: set2(addr1, addr2), width: 346, height: 231 },
  /** Booking/Options › OptionItem › Icon (교체필요: …) — 카테고리별 5종(무료 3 + 유료 2), 80×h. TODO(asset): 교체필요 — Figma 에 있는 임시 3D 아이콘 */
  // 키는 예약 카테고리 id — BookingOptions 화면이 bookingOptionIcons[category] 로 찾고(모르는 카테고리면 home),
  // 옵션 데이터(mock·서버)의 icon 값이 이 배열의 순번이다
  bookingOptionIcons: {
    home: [
      { src: optHome1_1, srcSet: set3(optHome1_1, optHome1_2, optHome1_3), width: 80, height: 89 },
      { src: optHome2_1, srcSet: set3(optHome2_1, optHome2_2, optHome2_3), width: 80, height: 83 },
      { src: optHome3_1, srcSet: set3(optHome3_1, optHome3_2, optHome3_3), width: 80, height: 65 },
      { src: optHome4_1, srcSet: set3(optHome4_1, optHome4_2, optHome4_3), width: 80, height: 84 },
      { src: optHome5_1, srcSet: set3(optHome5_1, optHome5_2, optHome5_3), width: 80, height: 86 },
    ],
    office: [
      { src: optOffice1_1, srcSet: set3(optOffice1_1, optOffice1_2, optOffice1_3), width: 80, height: 89 },
      { src: optOffice2_1, srcSet: set3(optOffice2_1, optOffice2_2, optOffice2_3), width: 80, height: 83 },
      { src: optOffice3_1, srcSet: set3(optOffice3_1, optOffice3_2, optOffice3_3), width: 80, height: 65 },
      { src: optOffice4_1, srcSet: set3(optOffice4_1, optOffice4_2, optOffice4_3), width: 80, height: 84 },
      { src: optOffice5_1, srcSet: set3(optOffice5_1, optOffice5_2, optOffice5_3), width: 80, height: 86 },
    ],
    appliance: [
      { src: optAppliance1_1, srcSet: set3(optAppliance1_1, optAppliance1_2, optAppliance1_3), width: 80, height: 89 },
      { src: optAppliance2_1, srcSet: set3(optAppliance2_1, optAppliance2_2, optAppliance2_3), width: 80, height: 83 },
      { src: optAppliance3_1, srcSet: set3(optAppliance3_1, optAppliance3_2, optAppliance3_3), width: 80, height: 65 },
      { src: optAppliance4_1, srcSet: set3(optAppliance4_1, optAppliance4_2, optAppliance4_3), width: 80, height: 84 },
      { src: optAppliance5_1, srcSet: set3(optAppliance5_1, optAppliance5_2, optAppliance5_3), width: 80, height: 86 },
    ],
    'move-in': [
      { src: optMovein1_1, srcSet: set3(optMovein1_1, optMovein1_2, optMovein1_3), width: 80, height: 89 },
      { src: optMovein2_1, srcSet: set3(optMovein2_1, optMovein2_2, optMovein2_3), width: 80, height: 83 },
      { src: optMovein3_1, srcSet: set3(optMovein3_1, optMovein3_2, optMovein3_3), width: 80, height: 65 },
      { src: optMovein4_1, srcSet: set3(optMovein4_1, optMovein4_2, optMovein4_3), width: 80, height: 84 },
      { src: optMovein5_1, srcSet: set3(optMovein5_1, optMovein5_2, optMovein5_3), width: 80, height: 86 },
    ],
  },
  /** Booking/Confirm › SummaryRow › Icon 7종 (2039:1658 · 90:164 · 90:170 · 90:187 · 90:178 · 90:184 · 90:173) — 4 카테고리 공통 */
  confirmIcons: {
    service: { src: cSvc_1, srcSet: set3(cSvc_1, cSvc_2, cSvc_3), width: 50, height: 46 },
    address: { src: cAddr_1, srcSet: set3(cAddr_1, cAddr_2, cAddr_3), width: 50, height: 46 },
    schedule: { src: cSch_1, srcSet: set3(cSch_1, cSch_2, cSch_3), width: 50, height: 46 },
    frequency: { src: cFreq_1, srcSet: set3(cFreq_1, cFreq_2, cFreq_3), width: 50, height: 52 },
    options: { src: cOpt_1, srcSet: set3(cOpt_1, cOpt_2, cOpt_3), width: 50, height: 46 },
    notes: { src: cNote_1, srcSet: set3(cNote_1, cNote_2, cNote_3), width: 50, height: 55 },
    requests: { src: cReq_1, srcSet: set3(cReq_1, cReq_2, cReq_3), width: 50, height: 46 },
  },
  /** Booking/Confirm › HeroIllustration (90:155) 200×194 */
  confirmHero: { src: cHero_1, srcSet: set3(cHero_1, cHero_2, cHero_3), width: 200, height: 194 },
  /** Booking/NotesRequests › TipCard › Icon (86:334) 50×49 3D 전구 */
  requestTipIcon: { src: tip_1, srcSet: set3(tip_1, tip_2, tip_3), width: 50, height: 49 },
  /** Booking/Complete › CompleteIllustration (flat image) (70:1113) 504×896 — 문구가 이미지에 포함. 원본이 약 1.9x 라 1x/2x */
  bookingComplete: { src: bcomplete_1, srcSet: set2(bcomplete_1, bcomplete_2), width: 504, height: 896 },
  /** Service/LivePortrait › ManagerAvatar (2167:2255) 48 · LiveLandscape (2165:2227) 44 — 같은 이미지. 원본이 96px 이라 3x 는 확대 */
  managerAvatar: { src: avatar1, srcSet: set3(avatar1, avatar2, avatar3), width: 48, height: 48 },
  /** Service/Start › VideoPoster (placeholder · 코드에서 교체) (92:768) 403×852 — 문구·버튼이 든 소개 이미지. TODO(asset): 교체필요 */
  serviceStart: { src: start1, srcSet: set2(start1, start2), width: 403, height: 852 },
  /** Home/Realtime › HeroArt (96:141) 262×253 · HeroArtPeek (96:150) 49×183 — 실시간 청소 슬라이드 (오른쪽에 다음 슬라이드가 살짝 보임) */
  heroRealtime: { src: hrt1, srcSet: set3(hrt1, hrt2, hrt3), width: 262, height: 253 },
  heroRealtimePeek: { src: hrtPeek1, srcSet: set3(hrtPeek1, hrtPeek2, hrtPeek3), width: 49, height: 183 },
  /** Repair › HeroArt (2067:1824) 262×262 */
  repairHero: { src: rHero1, srcSet: set3(rHero1, rHero2, rHero3), width: 262, height: 262 },
  /** Repair › RepairCategoryItem › Icon (2069:1828 …) 46×46 3D 아이콘 8종 */
  repairAppliance: { src: rApp1, srcSet: set3(rApp1, rApp2, rApp3), width: 46, height: 46 },
  repairFurniture: { src: rFur1, srcSet: set3(rFur1, rFur2, rFur3), width: 46, height: 46 },
  repairPlumbing: { src: rPlu1, srcSet: set3(rPlu1, rPlu2, rPlu3), width: 46, height: 46 },
  repairElectric: { src: rEle1, srcSet: set3(rEle1, rEle2, rEle3), width: 46, height: 46 },
  repairToiletClog: { src: rClog1, srcSet: set3(rClog1, rClog2, rClog3), width: 46, height: 46 },
  repairToiletRepair: { src: rToi1, srcSet: set3(rToi1, rToi2, rToi3), width: 46, height: 46 },
  repairDoor: { src: rDoor1, srcSet: set3(rDoor1, rDoor2, rDoor3), width: 46, height: 46 },
  repairOther: { src: rOth1, srcSet: set3(rOth1, rOth2, rOth3), width: 46, height: 46 },
};
