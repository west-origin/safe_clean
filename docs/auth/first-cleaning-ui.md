# 첫 1회 청소 UI (2026-11-10)

- 가정·원룸은 category_key=home이며 home_type=house/studio로 구분한다. 매장은 category_key=store다.
- 서버 GET /customer/identity/first-cleaning 응답의 completedOneTime=true이면 가정·원룸·매장의 1회 카드를 숨긴다.
- completedOneTime은 인증된 고객 ID와 bookings.customer_id가 같고, category_key IN ('home','store'), frequency='oneTime', status='done'인 예약의 존재 여부다. 카테고리를 바꿔도 동일한 판정이며 예약 요청/진행/취소 또는 정기 청소 완료는 포함하지 않는다.
- first_cleaning_claims는 예약 시 혜택 확보·중복 방지 원장이다. 실제 서비스 완료값과 다르므로 카드 숨김은 이 원장 대신 completedOneTime을 쓴다. 기존 서버의 최종 할인 제한은 유지한다.
- 신규 가정·원룸 고객이 1회 선택 시 휴대폰 안내 시트를 연다. 휴대폰 인증하기는 동작 없는 버튼이며 SMS API 요청, 번호 입력, 인증 완료 처리, 다음 화면 이동은 하지 않는다. 닫기/배경/ESC로 닫는다. 미인증 상태에서 다음을 눌러도 인증 안내로 돌아간다.
- 로그인 계정 ID 기준의 실제 예약 이력이며 다른 SNS 계정과의 동일인 판정이 아니다. 이메일 없는 별도 SNS 계정은 이력을 공유하지 않는다. 향후 SMS 번호 검증과 기존 혜택 원장으로 중복 이용을 막는다.
- DB 구조/기존 데이터 변경 없음. 서버 조회 응답 필드만 추가한다.

검증: 프론트 563개·서버 원격 빌드 634개 테스트 통과. 테스트 서버 ansimclean-api-test-00019-n76 배포(100%), 운영 미변경. 고객 Android staging 동기화 및 빌드 완료. 실제 완료 예약을 원격 DB에 임의 생성하지 않았으며 표시 조건은 로컬 SQL·UI 테스트로 검증했다.

2026-11-10 후속: '휴대폰 인증하기' 가 인증 화면(`/booking/:category/phone-verify`, PhoneVerify)으로 이동한다. 인증번호는 Supabase Auth phone_change — `docs/auth/phone-otp.md`. 위의 '동작 없는 버튼' 설명은 이것으로 대체.
