// 금액권·선물 API 어댑터 (backend/docs/api.md '금액권·선물' · '금액권 사용·환불'). 선물 화면(screens/gifts)과 결제 화면의 금액권 칸은 이 함수들만 부른다.
// 응답 모양은 서버 그대로:
//   GiftView = { id, direction: sent|received, method: purchase|voucher, status, amount, amountLabel, label, message, person: FriendUser, expiresLabel, … }
//   VoucherView = { id, label, faceAmount, balance, balanceLabel, status: held|gifting|refund_requested, validUntilLabel, giftable, expiringSoon, blockedReason, usable, useBlockedReason }
// 선물 보내기·환불 신청은 멱등 키를 받는다 — 화면이 한 번 만든 키를 다시 누를 때도 같이 보내 같은 선물·환불이 두 번 만들어지지 않게.
import { http } from './http.js';

const id = (v) => encodeURIComponent(v);

/** 선물 목록(요금표) → { items: [{ key, group, label, amount, amountLabel }], acceptDays, validDays } */
export const getGiftCatalog = () => http.get('/customer/gifts/catalog');
/** 내 금액권 (보유·선물 중·환불 접수) → { vouchers: VoucherView[] } */
export const getVouchers = () => http.get('/customer/vouchers');
/**
 * 선물 보내기 → { gift: GiftView, checkout: 결제 응답 | null }. 구매면 gift.status = awaiting_payment + checkout(토스 결제창을 연다)
 * @param {{ recipientId: string, method: 'purchase'|'voucher', catalogKey?: string, voucherId?: string, message?: string }} req
 * @param {string} idempotencyKey
 */
export const sendGift = (req, idempotencyKey) => http.post('/customer/gifts', req, { idempotencyKey });
/** box = sent | received → { box, gifts: GiftView[] } */
export const getGifts = (box) => http.get(`/customer/gifts?box=${id(box)}`);
/** 선물 한 건 → { gift, checkout } — checkout 은 내가 보낸 결제 전 선물일 때만 */
export const getGift = (giftId) => http.get(`/customer/gifts/${id(giftId)}`);
/** 받기 (받는 사람만) → { gift, checkout: null } */
export const acceptGift = (giftId) => http.post(`/customer/gifts/${id(giftId)}/accept`);
/** 거절 (받는 사람만) — 금액권은 보낸 사람에게 */
export const rejectGift = (giftId) => http.post(`/customer/gifts/${id(giftId)}/reject`);
/** 취소 (보낸 사람만, 받기 전까지) */
export const cancelGift = (giftId) => http.post(`/customer/gifts/${id(giftId)}/cancel`);

/** 예약에 쓰는 금액권 → { voucherId|null, label, appliedAmount, appliedLabel, remainingDue, remainingLabel, coveredByVoucher } */
export const getBookingVoucher = (bookingId) => http.get(`/customer/bookings/${id(bookingId)}/voucher`);
/** 예약에 금액권 쓰기 (예약당 1장) → 위와 같은 모양 */
export const applyVoucher = (bookingId, voucherId) => http.post(`/customer/bookings/${id(bookingId)}/voucher`, { voucherId });
/** 예약에서 금액권 빼기 → 위와 같은 모양 */
export const removeVoucher = (bookingId) => http.delete(`/customer/bookings/${id(bookingId)}/voucher`);

/** 지금 환불하면 → { eligible, method: card|bank|null, rate, amount, amountLabel, reason, needsAccount, openStatus } */
export const getVoucherRefund = (voucherId) => http.get(`/customer/vouchers/${id(voucherId)}/refund`);
/**
 * 환불 신청 → { refundId, method, status: processing|requested, rate, amount, amountLabel }. 카드 환불이면 account 를 비워도 된다
 * @param {string} voucherId
 * @param {{ bankName?: string, accountNumber?: string, accountHolder?: string }} account
 * @param {string} idempotencyKey
 */
export const requestVoucherRefund = (voucherId, account, idempotencyKey) =>
  http.post(`/customer/vouchers/${id(voucherId)}/refund`, account, { idempotencyKey });
