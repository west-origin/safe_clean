package kr.ansimclean.api.live.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** POST /admin/devices — 운영자가 바디캠을 매니저에게 붙인다 (§13-20) */
/** rotate=true 면 기존 Ingress 를 지우고 새로 만든다(스트림 키 회전 — 분실·도난) */
// 받는 곳: AdminDeviceController → DeviceService.register. NotNull·NotBlank·Size = 값 검사 규칙(비었거나 50자를 넘으면 안 된다).
// 이 규칙은 AdminDeviceController 가 관리자 토큰을 확인한 뒤 validator 로 직접 검사하고, 어기면 VALIDATION 오류다
public record RegisterDeviceRequest(@NotNull UUID managerId, @NotBlank @Size(max = 50) String name, @NotBlank @Size(max = 50) String serial, Boolean rotate) {
    // 압축 생성자: record 가 값을 필드에 넣기 전에 끼어든다. rotate 를 빼고 보내면(null) false 로 채운다
    public RegisterDeviceRequest {
        if (rotate == null) rotate = false;
    }
}
