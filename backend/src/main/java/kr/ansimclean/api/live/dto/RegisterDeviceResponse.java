package kr.ansimclean.api.live.dto;

import java.util.UUID;

/** 기기에 한 번만 설정하는 RTMP 주소와 **전체** 스트림 키 (이 응답에만 나온다. 앱은 마스킹된 키만 본다) */
// 만드는 곳: DeviceService.register (POST /admin/devices 응답). 값은 저장된 manager_devices 행 그대로
public record RegisterDeviceResponse(UUID managerId, String name, String serial, String ingressId, String streamUrl, String streamKey) {}
