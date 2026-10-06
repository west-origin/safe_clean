package kr.ansimclean.api.offer.dto;

import java.util.List;
import java.util.UUID;

/**
 * 콜 카드 1장 (앱 CALLS.calls 모양, §6.2-6). expiry = "4:32 후 만료", expiresAt = ISO(앱이 카운트다운을 그리고 싶을 때),
 * title = "9월 18일 목요일 오후 2:00", location = "서울특별시 강남구 테헤란로 · 가정/원룸 청소", note = "특이사항: 반려동물이 있어요" (없으면 null)
 * details = 수락 전에 알아야 할 것들 ["예상 소요 3시간", "아파트·빌라 · 25평 · 방 3 · 화장실 2", "청소 범위: 설거지, 냉장고 정리 및 청소"]
 *   — 예전에는 날짜·주소·금액만 보여 줘서 3시간짜리 일을 정보 없이 수락해야 했다 (사용성 검토 2026-09-22)
 * eta = 이동 시간. 거리 계산이 없어 지금은 null (앱은 없으면 그 줄을 그리지 않는다)
 * id = 콜(offer) id — 상세·수락·거절 주소의 {id} 에 쓴다. pay = 매니저 수령액 "85,000원".
 * OfferService.toItem 이 만들고, 콜 목록(CallsResponse.calls)과 상세(CallDetailResponse.of)가 쓴다
 */
public record CallItem(UUID id, String expiry, String expiresAt, String title, String location, String pay, String eta, String note, List<String> details) {}
