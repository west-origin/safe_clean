# 영상 공간 라벨과 필수 사진 (2026-10-24)

## 매니저 작업 순서

1. 준비 화면의 청소 시작: 작업 상태만 진행 중으로 변경하고 전 사진 화면으로 이동한다. 이 단계에서는 라이브를 시작하지 않는다.
2. `required_photo_rooms`의 각 공간에서 전 사진 한 장을 저장한다. 기본 공간은 작업 체크리스트 그룹을 중복 없이 복사한다. 실제 현장에 안방/작은방/화장실 등이 더 있으면 **공간 추가**로 필수 사진 목록과 완료 체크 항목을 함께 추가한다. 등록된 모든 공간을 촬영해야 한다. 현장에 존재하는 방을 자동 인식하지는 않는다.
3. 실시간 영상 시작: 서버가 전 사진 누락을 검사하고 확인 시각을 저장한다. 이후 송출 시작 가능. 전 사진 추가/삭제와 공간 추가는 잠긴다.
4. 체크리스트 완료: 체크 원본 이력과 `recording_segments`를 같은 트랜잭션에 저장한다. 같은 상태 재요청은 중복 저장하지 않는다. 체크 해제는 이력 추가 + 해당 항목의 기존 라벨을 `revoked`로 무효화한다. 재체크는 새 구간을 생성한다.
5. 실시간 영상 종료: 라이브를 시작한 세션인지 확인하고 `video_finished_at`를 저장한다. 녹화 시작 실패·녹화 ID 없음도 종료할 수 있다. 이후 재송출/재개/체크 변경은 금지한다. LiveKit 방 종료 API 성공 후 후 사진 화면을 연다. 실패/앱 재실행 시 종료를 재시도한다. 웹훅·재조정기도 방 삭제 실패 시 세션을 `ended`로 확정하지 않는다.
6. 각 장소의 전 사진을 50% 투명도로 겹친 휴대폰 카메라로 후 사진을 촬영한다. 이 카메라는 라이브를 다시 시작하지 않는다. 서버도 실제 세션 종료 전에는 후 사진 업로드를 거부한다.
7. 모든 필수 공간 및 기존 추가 위치의 전/후 사진이 있어야 청소 완료 가능. 서버 검사도 동일하다. 완료 후 고객 시청 화면에서 전/후 슬라이더를 공개한다.

현재 기본 가정청소 체크리스트에는 거실/주방/욕실이 있다. 안방 등 현장의 추가 공간은 매니저가 등록해야 한다. 이름은 1~20자, 최대 10곳이다. 기존 진행 중 작업의 필수 전 사진이 비어 있으면 V057에서 전 사진 보완 단계로 돌린다. 기존 녹화 파일은 삭제/재생성하지 않는다.

## API 변경

| API | 기능 |
|---|---|
| `GET /manager/jobs/{id}/photos` | 기존 `photos,max` + `requiredRooms:string[]`, `phase:before/live/after` |
| `POST /manager/jobs/{id}/rooms` | `{name}`. 전 사진 확인 전 공간 추가 + 해당 공간의 `청소 완료` 체크 항목 추가 |
| `POST /manager/jobs/{id}/photos/confirm-before` | 필수 전 사진 검사 후 송출 허용. 재요청 멱등 |
| `POST /manager/jobs/{id}/finish-video` | 라이브 시작 확인, 종료 의도 저장. 녹화 장애도 허용. 그 다음 기존 `POST /live/sessions/{id}/stop` 호출 |
| `POST /manager/jobs/{id}/complete` | 영상 종료 및 전/후 사진 필수 검사 추가 |

기존 사진 multipart API는 유지한다. 전 사진 `location`은 필수 공간명과 정확히 일치해야 하며 공간당 한 장이다. 저장 전 단계에서는 삭제 후 다시 찍을 수 있다. 모든 변경은 예약 잠금으로 확인/삭제/완료 요청과 직렬화한다. 운영자 강제 완료 API는 기존의 예외 복구 수단으로 유지한다.

## 사진 서명 URL의 폴더 경로 (2026-10-26)

Storage 서명 요청의 사진 경로를 하나의 URI 변수로 넘기면 폴더 구분자 `/`까지 `%2F`로 인코딩된다. 이 요청은 서명 URL 발급에 성공해도 이미지 GET은 `400 InvalidSignature`로 거부될 수 있다. `SupabaseStorageClient.signedUrl`은 경로를 폴더별로 분리해 각 조각만 인코딩한 URI로 요청한다. 현재 테스트 예약의 전 사진 3장을 실제 Java 클라이언트로 서명·다운로드해 HTTP 200 및 JPEG 헤더를 확인했다. 사진 원장과 파일의 수정·재업로드는 필요 없다.

## 녹화 시작 실패 (2026-10-26)

`GET /live/sessions/{id}`에 `recordingStatus`와 `recordingStartFailed`가 추가된다. 녹화 시작 API 실패 시 `live_sessions.recording_status=failed`로 저장하며 카메라 송출 상태(`live` 등)는 별도로 유지한다. `recordingStartFailed=true`는 `failed`이면서 `egress_id`가 없는 시작 실패를 뜻한다. 기존 DB 상태 값을 사용하므로 스키마 변경은 필요 없다.

Egress ID가 없는 시작 실패만 기존 재조정 주기에서 재시도한다. 성공 응답 또는 늦은 시작 웹훅을 받으면 `recording`으로 복구한다. 이미 발급된 녹화의 실패나 종료 세션은 새 녹화를 시작하지 않는다. 매니저 화면은 녹화 실패를 별도 안내하고 종료 버튼을 유지한다. 녹화 실패를 정상 파일·정확한 녹화 시각으로 꾸미지 않는다.

## `recording_segments`의 의미

파일은 `live_sessions.recording_path` 한 개이다. 구간은 `live_session_id`로 연결하며 `room_code`, `room_name`, `start_ms`, `end_ms`, 생성 출처, 검토 상태, 매니저, 생성/수정 시각을 별도 행으로 저장한다. 모든 신규 테이블/컬럼에 PostgreSQL `COMMENT ON` 설명을 추가했다(V056).

**체크 완료는 방 이동 감지와 다르다.** 각 구간은 **이전 체크 완료 시각부터 이번 완료 시각까지**를 이번 항목의 공간으로 추정한다. 첫 구간의 원본 시작 경계는 라이브 시작 요청 시각이다. 첫 녹화 프레임 이후로 자르고, 방을 오가거나 체크를 늦게 누르면 실제 이동과 차이가 생긴다. 따라서 `label_source=checklist`, `review_status=pending`으로 저장하고 학습 전 검토한다. 마지막 체크 이후 영상 끝까지의 미체크 부분은 라벨을 임의로 붙이지 않는다.

시간 경계는 `[start_ms,end_ms)`이며 영상 시작 기준 밀리초다. 요청 시각을 실제 파일 시작 시각으로 사용하지 않는다. 정확한 녹화 시작이 미확인인 동안 `timing_status=pending`, `start_ms/end_ms=null`이고 원본 `start_at/end_at`만 보관한다. LiveKit 웹훅이 실제 시작/길이를 확인하면 DB 트리거가 기존 구간을 보정한다. 음수는 0으로, 종료는 알려진 파일 길이로 자른다. 영상 밖이거나 길이가 0이면 `timing_status=empty`이며 유효 구간으로 내보내지 않는다.

검토/학습 대상 조회 예:

```sql
select s.id, v.recording_path, s.room_code, s.room_name,
       s.start_ms, s.end_ms, s.label_source, s.review_status
from recording_segments s
join live_sessions v on v.booking_id = s.live_session_id
where v.recording_status = 'ready'
  and v.recording_path is not null
  and s.timing_status = 'ready'
  and s.review_status <> 'revoked'
order by s.live_session_id, s.start_ms;
```

학습 데이터로 확정할 때는 검토 후 `review_status='approved'`인 행만 사용한다. 구간 등록/수정용 별도 관리자 UI나 AI 분류기는 이번 범위에 포함하지 않는다. RLS를 켜고 anon/authenticated 권한을 회수하여 앱은 Spring API만 사용한다.

## 확인

- `node scripts/verify-recording-segments.mjs`: 원본 SQL 전체 적용, 늦은 녹화 시작/최종 길이 보정, 중복 방지, 무효 라벨 보존, 코멘트/RLS, 예약 삭제 시 정리 검증.
- `npm test`: 전 사진 누락 차단, 직접 경로 접근 차단, 종료 선행, 후 사진 필수, 반투명 가이드, 고객 완료 후 노출 검증.
- 서버 `mvnw test`: 사진 단계/완료/송출 검사와 체크 구간 저장 검증.

두 앱의 별도 시작 오디오 재생 코드는 없었다. Android의 모든 화면 자식 View에 터치 효과음 차단을 적용하고, Capacitor의 앱 실행 중 알림 표시 옵션에서 sound를 제거했다. 추가 요청으로 백그라운드 푸시도 무음 처리했다: 두 Android 앱은 `ansimclean_silent_v1` 채널(IMPORTANCE_LOW, sound=null)을 생성해 FCM 기본 채널로 사용하며, 서버도 해당 채널과 default_sound=false를 지정한다. iOS APNs의 sound=default도 제거했다. 영상의 기존 음소거 설정은 유지한다.
