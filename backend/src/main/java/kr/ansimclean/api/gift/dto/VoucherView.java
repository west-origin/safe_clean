package kr.ansimclean.api.gift.dto;

import java.util.UUID;

/**
 * 내 금액권 한 장. status: held(보유) | gifting(선물 중 — 받는 사람의 결정을 기다림).
 * giftable = 지금 친구에게 선물할 수 있나 (아니면 blockedReason 에 이유 문구). expiringSoon = 유효기간이 30일 안 (경고 표시)
 */
public record VoucherView(UUID id, String label, long faceAmount, long balance, String balanceLabel, String status,
                          String validUntil, String validUntilLabel, boolean giftable, boolean expiringSoon, String blockedReason) {}
