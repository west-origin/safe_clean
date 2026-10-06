# API inventory

2026-09-24 재점검, 기준 `9b131e4` + 현재 수정. Controller 선언에서 재생성하고 SecurityConfig 대조. 메서드 매핑 92개(결제 stub 1개 포함). Actuator health는 별도. 인증 뒤 서비스 소유권/상태 검사도 적용되므로 Gate만으로 모든 권한을 표현하지 않는다.

| Method | Path | Gate | Source |
|---|---|---|---|
| POST | `/api/v1/auth/phone/otp` | public | [AuthController.java:46](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:46>) |
| POST | `/api/v1/auth/phone/verify` | public | [AuthController.java:51](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:51>) |
| POST | `/api/v1/auth/password/otp` | public | [AuthController.java:57](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:57>) |
| POST | `/api/v1/auth/password/reset` | public | [AuthController.java:62](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:62>) |
| POST | `/api/v1/auth/signup` | public | [AuthController.java:67](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:67>) |
| POST | `/api/v1/auth/signup/complete` | Bearer + 소유권 | [AuthController.java:72](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:72>) |
| POST | `/api/v1/auth/login` | public | [AuthController.java:77](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:77>) |
| POST | `/api/v1/auth/refresh` | public | [AuthController.java:82](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:82>) |
| POST | `/api/v1/auth/logout` | Bearer + 소유권 | [AuthController.java:87](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:87>) |
| GET | `/api/v1/auth/me` | Bearer + 소유권 | [AuthController.java:96](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:96>) |
| DELETE | `/api/v1/auth/account` | Bearer + 소유권 | [AuthController.java:101](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/auth/AuthController.java:101>) |
| POST | `/api/v1/customer/bookings/quote` | CUSTOMER | [BookingController.java:38](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingController.java:38>) |
| POST | `/api/v1/customer/bookings` | CUSTOMER | [BookingController.java:43](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingController.java:43>) |
| GET | `/api/v1/customer/bookings/{id}` | CUSTOMER | [BookingController.java:49](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingController.java:49>) |
| GET | `/api/v1/customer/reservations` | CUSTOMER | [BookingController.java:54](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingController.java:54>) |
| PATCH | `/api/v1/customer/reservations/{id}/schedule` | CUSTOMER | [BookingController.java:59](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingController.java:59>) |
| POST | `/api/v1/customer/reservations/{id}/cancel` | CUSTOMER | [BookingController.java:64](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/BookingController.java:64>) |
| POST | `/api/v1/customer/repair-photos` | CUSTOMER | [RepairPhotoController.java:26](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/booking/RepairPhotoController.java:26>) |
| GET | `/api/v1/catalog/categories/{category}` | public | [CatalogController.java:30](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/catalog/CatalogController.java:30>) |
| GET | `/api/v1/catalog/categories/{category}/options` | public | [CatalogController.java:35](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/catalog/CatalogController.java:35>) |
| GET | `/api/v1/catalog/categories/{category}/notice-chips` | public | [CatalogController.java:40](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/catalog/CatalogController.java:40>) |
| GET | `/api/v1/catalog/categories/{category}/confirm-copy` | public | [CatalogController.java:45](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/catalog/CatalogController.java:45>) |
| GET | `/api/v1/catalog/time-slots` | public | [CatalogController.java:50](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/catalog/CatalogController.java:50>) |
| GET | `/api/v1/catalog/reasons` | public | [CatalogController.java:56](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/catalog/CatalogController.java:56>) |
| GET | `/api/v1/catalog/address` | public | [CatalogController.java:61](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/catalog/CatalogController.java:61>) |
| GET | `/api/v1/customer/home` | CUSTOMER | [CustomerScreenController.java:23](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/customer/CustomerScreenController.java:23>) |
| GET | `/api/v1/customer/repair` | CUSTOMER | [CustomerScreenController.java:28](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/customer/CustomerScreenController.java:28>) |
| GET | `/api/v1/customer/settings` | CUSTOMER | [CustomerScreenController.java:33](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/customer/CustomerScreenController.java:33>) |
| GET | `/api/v1/manager/jobs/active` | MANAGER | [JobController.java:39](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:39>) |
| GET | `/api/v1/manager/jobs/active/pause` | MANAGER | [JobController.java:44](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:44>) |
| GET | `/api/v1/manager/jobs/last-completed` | MANAGER | [JobController.java:49](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:49>) |
| GET | `/api/v1/manager/jobs/{id}` | MANAGER | [JobController.java:54](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:54>) |
| GET | `/api/v1/manager/jobs/{id}/contact` | MANAGER | [JobController.java:59](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:59>) |
| POST | `/api/v1/manager/jobs/{id}/start` | MANAGER | [JobController.java:64](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:64>) |
| POST | `/api/v1/manager/jobs/{id}/pause` | MANAGER | [JobController.java:69](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:69>) |
| POST | `/api/v1/manager/jobs/{id}/resume` | MANAGER | [JobController.java:74](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:74>) |
| POST | `/api/v1/manager/jobs/{id}/cancel` | MANAGER | [JobController.java:80](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:80>) |
| POST | `/api/v1/manager/jobs/{id}/complete` | MANAGER | [JobController.java:86](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:86>) |
| PUT | `/api/v1/manager/jobs/{id}/checklist/{itemId}` | MANAGER | [JobController.java:91](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/job/JobController.java:91>) |
| POST | `/api/v1/admin/devices` | X-Admin-Token | [AdminDeviceController.java:30](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/AdminDeviceController.java:30>) |
| GET | `/api/v1/manager/device` | MANAGER | [DeviceController.java:24](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/DeviceController.java:24>) |
| GET | `/api/v1/manager/device/status` | MANAGER | [DeviceController.java:29](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/DeviceController.java:29>) |
| POST | `/api/v1/live/sessions/{id}/start` | Bearer + 소유권 | [LiveController.java:33](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:33>) |
| GET | `/api/v1/live/sessions/{id}` | Bearer + 소유권 | [LiveController.java:38](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:38>) |
| POST | `/api/v1/live/sessions/{id}/pause` | Bearer + 소유권 | [LiveController.java:43](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:43>) |
| POST | `/api/v1/live/sessions/{id}/resume` | Bearer + 소유권 | [LiveController.java:48](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:48>) |
| POST | `/api/v1/live/sessions/{id}/stop` | Bearer + 소유권 | [LiveController.java:53](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:53>) |
| POST | `/api/v1/live/sessions/{id}/token` | Bearer + 소유권 | [LiveController.java:58](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:58>) |
| GET | `/api/v1/live/sessions/{id}/recording` | Bearer + 소유권 | [LiveController.java:63](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:63>) |
| GET | `/api/v1/customer/bookings/{id}/live` | CUSTOMER | [LiveController.java:68](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveController.java:68>) |
| POST | `/api/v1/live/webhooks/livekit` | LiveKit 서명 | [LiveWebhookController.java:23](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/live/LiveWebhookController.java:23>) |
| GET | `/api/v1/manager/home` | MANAGER | [ManagerHomeController.java:26](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/managerhome/ManagerHomeController.java:26>) |
| GET | `/api/v1/manager/schedule` | MANAGER | [ManagerHomeController.java:31](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/managerhome/ManagerHomeController.java:31>) |
| GET | `/api/v1/manager/offers` | MANAGER | [OfferController.java:32](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/offer/OfferController.java:32>) |
| GET | `/api/v1/manager/offers/{id}` | MANAGER | [OfferController.java:37](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/offer/OfferController.java:37>) |
| POST | `/api/v1/manager/offers/{id}/accept` | MANAGER | [OfferController.java:42](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/offer/OfferController.java:42>) |
| POST | `/api/v1/manager/offers/{id}/reject` | MANAGER | [OfferController.java:47](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/offer/OfferController.java:47>) |
| GET | `/api/v1/customer/payment-methods` | CUSTOMER | [PaymentMethodController.java:23](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/payment/PaymentMethodController.java:23>) |
| PUT | `/api/v1/customer/profile/address` | CUSTOMER / PENDING_CUSTOMER | [CustomerProfileController.java:43](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:43>) |
| PUT | `/api/v1/customer/profile/housing` | CUSTOMER / PENDING_CUSTOMER | [CustomerProfileController.java:48](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:48>) |
| GET | `/api/v1/customer/profile` | CUSTOMER | [CustomerProfileController.java:53](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:53>) |
| GET | `/api/v1/customer/profile/suggested-notices` | CUSTOMER | [CustomerProfileController.java:59](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:59>) |
| PATCH | `/api/v1/customer/profile` | CUSTOMER | [CustomerProfileController.java:64](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:64>) |
| GET | `/api/v1/customer/addresses` | CUSTOMER | [CustomerProfileController.java:69](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:69>) |
| POST | `/api/v1/customer/addresses` | CUSTOMER | [CustomerProfileController.java:74](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:74>) |
| GET | `/api/v1/customer/addresses/default` | CUSTOMER | [CustomerProfileController.java:79](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:79>) |
| PUT | `/api/v1/customer/addresses/{id}/default` | CUSTOMER | [CustomerProfileController.java:84](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:84>) |
| DELETE | `/api/v1/customer/addresses/{id}` | CUSTOMER | [CustomerProfileController.java:89](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:89>) |
| PUT | `/api/v1/customer/notifications/{key}` | CUSTOMER | [CustomerProfileController.java:94](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/CustomerProfileController.java:94>) |
| GET | `/api/v1/manager/penalties` | MANAGER | [ManagerProfileController.java:32](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ManagerProfileController.java:32>) |
| GET | `/api/v1/manager/cancel-reasons` | MANAGER | [ManagerProfileController.java:38](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ManagerProfileController.java:38>) |
| GET | `/api/v1/manager/profile` | MANAGER | [ManagerProfileController.java:43](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ManagerProfileController.java:43>) |
| GET | `/api/v1/manager/profile/{key}` | MANAGER | [ManagerProfileController.java:48](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ManagerProfileController.java:48>) |
| GET | `/api/v1/manager/notifications` | MANAGER | [ManagerProfileController.java:53](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ManagerProfileController.java:53>) |
| PUT | `/api/v1/manager/notifications/{key}` | MANAGER | [ManagerProfileController.java:58](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/profile/ManagerProfileController.java:58>) |
| PUT | `/api/v1/push/token` | Bearer + 소유권 | [PushController.java:25](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/push/PushController.java:25>) |
| DELETE | `/api/v1/push/token` | Bearer + 소유권 | [PushController.java:32](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/push/PushController.java:32>) |
| GET | `/api/v1/manager/availability` | MANAGER | [AvailabilityController.java:34](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/schedule/AvailabilityController.java:34>) |
| PUT | `/api/v1/manager/availability/hours` | MANAGER | [AvailabilityController.java:39](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/schedule/AvailabilityController.java:39>) |
| DELETE | `/api/v1/manager/availability/unavailable/{id}` | MANAGER | [AvailabilityController.java:44](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/schedule/AvailabilityController.java:44>) |
| POST | `/api/v1/manager/availability/unavailable` | MANAGER | [AvailabilityController.java:50](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/schedule/AvailabilityController.java:50>) |
| GET | `/api/v1/manager/settlements` | MANAGER | [SettlementController.java:27](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/settlement/SettlementController.java:27>) |
| GET | `/api/v1/manager/settlements/{id}` | MANAGER | [SettlementController.java:32](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/settlement/SettlementController.java:32>) |
| POST | `/api/v1/admin/inquiries/{id}/answer` | X-Admin-Token | [AdminInquiryController.java:33](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/AdminInquiryController.java:33>) |
| GET | `/api/v1/support/faq` | public | [SupportController.java:35](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportController.java:35>) |
| GET | `/api/v1/support/notices` | public | [SupportController.java:40](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportController.java:40>) |
| GET | `/api/v1/support/notices/{id}` | public | [SupportController.java:45](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportController.java:45>) |
| GET | `/api/v1/support/contact` | public | [SupportController.java:51](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportController.java:51>) |
| GET | `/api/v1/support/inquiries` | Bearer + 소유권 | [SupportController.java:56](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportController.java:56>) |
| POST | `/api/v1/support/inquiries` | Bearer + 소유권 | [SupportController.java:61](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/SupportController.java:61>) |
| GET | `/api/v1/terms` | public | [TermsController.java:23](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/TermsController.java:23>) |
| GET | `/api/v1/terms/{id}` | public | [TermsController.java:29](</Users/shinsangwoon/Desktop/청소앱 프론트엔드/backend/src/main/java/kr/ansimclean/api/support/TermsController.java:29>) |
