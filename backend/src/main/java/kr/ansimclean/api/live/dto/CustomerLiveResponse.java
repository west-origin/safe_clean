package kr.ansimclean.api.live.dto;

/**
 * GET /customer/bookings/{id}/live — 앱 LIVE_SESSION 모양 + live 상태 (§5.2). status.title 은 상태별 고정 문구(app_settings.live_status_copy).
 * avatar 는 서명 URL 이 생기기 전까지 앱 에셋 키 "managerAvatar". playback 은 녹화본이 있을 때 총 길이만(현재 위치는 앱이 계산)
 * 만드는 곳: LiveService.customerLive. 받는 곳: 고객 앱 api/customer.js 의 getLiveSession.
 * startedAt = 작업(청소)을 시작한 시각(bookings.started_at) "2025. 09. 11. 14:32"(서울 시간, 시작 전이면 ""), viewers = 지금 보고 있는 고객 수.
 */
public record CustomerLiveResponse(Manager manager, String startedAt, int viewers, Copy status, Copy reassurance, Playback playback, Live live) {
    /** 매니저 소개 카드. name = "○○○ 매니저", description = "경력 N년 · 완료 청소 N건"(활동 시작일이 없으면 경력은 빠짐). 배정 전이면 이름·설명이 빈 문자열 */
    public record Manager(String name, String description, String avatar) {}

    /** 제목·설명 문구 한 쌍 (status = 상태별 문구, reassurance = 안심 문구) */
    public record Copy(String title, String description) {}

    /** 재생 막대. 서버는 늘 현재 "0:00"·ratio 0 을 주고, totalLabel 만 녹화 길이("5:20" 모양, 없으면 "0:00")다 */
    public record Playback(String currentLabel, String totalLabel, double ratio) {}

    /** state = waiting | live | paused | ended, available = state == live */
    /** recordingPending: 녹화가 진행·업로드 중이라 곧 recordingReady 가 될 수 있다. 둘 다 false 면 녹화본은 없다(앱은 폴링을 멈춘다).
     *  recording = none | pending | ready | failed — 실패와 '녹화 없음' 을 구분해 문구를 다르게 (검증 검토) */
    public record Live(String state, boolean available, boolean recordingReady, boolean recordingPending, String recording) {}
}
