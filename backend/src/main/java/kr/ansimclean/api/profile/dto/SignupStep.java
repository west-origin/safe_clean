package kr.ansimclean.api.profile.dto;

/**
 * 가입 도중 앱을 닫았다가 돌아온 사용자를 어느 화면으로 보낼지 (BACKEND_PLAN.md §7.3).
 * 고객: address → housing → complete, 매니저: complete. 끝났으면 null.
 */
// enum: 정해진 값 몇 개만 가질 수 있는 타입. ProfileService.signupStep 이 고르고, 응답에는 value() 의 소문자 글자가 나간다.
// 두 앱이 각자 이 글자를 SIGNUP_STEP_PATHS 로 화면 경로에 맞춘다 (예: apps/customer/src/api/customer.js)
public enum SignupStep {
    ADDRESS("address"), HOUSING("housing"), COMPLETE("complete");

    private final String value;

    SignupStep(String value) {
        this.value = value;
    }

    /** 앱 session.user.signupStep 에 들어가는 글자 */
    public String value() {
        return value;
    }
}
