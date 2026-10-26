package kr.ansimclean.api.common.supabase;

import kr.ansimclean.api.common.config.AppProperties;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Supabase Storage REST — 녹화본 서명 URL(고객 재생, §8.2)과 삭제(보존 기간 뒤, §2.5). 업로드는 LiveKit Egress 가 S3 로 직접 한다.
 * service_role 키는 서버에만 있다.
 * 그 밖에 수리 사진(repair-photos 버킷)의 업로드·서명 URL·목록·삭제에도 쓴다.
 * 부르는 곳 예: RepairPhotoService(수리 사진 올리기·지우기), LiveService(녹화본 재생 URL).
 * 실패는 모두 ApiException(대개 UPSTREAM 502)으로 바꿔 던진다. 없는 파일 삭제·없는 폴더 목록은 오류가 아니다.
 * 오류 문구는 버킷과 관계없는 중립 문구다(업로드만 '사진을 올리지 못했어요…') — 녹화본·수리 사진에 맞는 문구는 부르는 쪽이 바꿔 던진다 (문제 목록 21번).
 */
@Component
public class SupabaseStorageClient {
    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageClient.class);

    private final RestClient client; // 기본 주소가 base 로 고정된 HTTP 호출 도구
    private final String base; // '<Supabase 주소>/storage/v1' — 서명 URL 을 완성할 때도 앞에 붙인다
    private final String serviceRoleKey; // 서버 전용 키. 모든 호출의 Authorization·apikey 헤더에 싣는다

    public SupabaseStorageClient(AppProperties props, RestClient.Builder builder) {
        this.base = props.supabase().url() + "/storage/v1";
        this.serviceRoleKey = props.supabase().serviceRoleKey();
        this.client = builder.baseUrl(base).build();
    }

    /** 파일 업로드 (같은 경로면 덮어쓴다). 수리 사진(repair-photos) 등 앱이 올리는 작은 파일용 */
    public void upload(String bucket, String path, byte[] bytes, String contentType) {
        try {
            // x-upsert: true — 같은 경로에 파일이 있으면 오류 대신 덮어쓴다
            client.post().uri("/object/{bucket}/{path}", bucket, path)
                    .header("Authorization", "Bearer " + serviceRoleKey).header("apikey", serviceRoleKey)
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes)
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) { // Storage 가 4xx·5xx 로 답함. 아래 RestClientException 은 연결 실패 등 그 밖의 호출 오류
            log.error("Storage 업로드 실패 {} {}", e.getStatusCode(), bucket + "/" + path);
            throw new ApiException(ErrorCode.UPSTREAM, "사진을 올리지 못했어요. 잠시 후 다시 시도해주세요");
        } catch (RestClientException e) {
            log.error("Storage 연결 실패 {}: {}", bucket + "/" + path, e.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM, "사진을 올리지 못했어요. 잠시 후 다시 시도해주세요");
        }
    }

    // 비공개 버킷의 파일을 expiresInSeconds 초 동안만 열 수 있는 주소.
    // 실패: 파일이 없으면(404) NOT_FOUND, 응답에 주소가 없거나 그 밖의 오류면 UPSTREAM — 둘 다 ErrorCode 의 기본 문구라 어느 버킷에 써도 틀리지 않는다.
    // 부르는 쪽이 제 문구로 바꾼다: 녹화본(LiveService.recording)은 404 를 RECORDING_NOT_READY 로, 수리 사진 업로드(RepairPhotoService.upload)는 '사진을 올리지 못했어요…' 로
    /** 만료가 있는 다운로드 URL */
    public String signedUrl(String bucket, String path, int expiresInSeconds) {
        try {
            // 폴더 구분자까지 하나의 변수로 넘기면 '/'가 '%2F'가 된다. Storage는 이 경로로
            // URL을 발급하지만 이미지 GET에서 InvalidSignature가 발생하므로 각 경로 조각만 인코딩한다.
            var signingUri = UriComponentsBuilder.fromUriString(base)
                    .pathSegment("object", "sign", bucket)
                    .pathSegment(path.split("/", -1)).build().encode().toUri();
            JsonNode body = client.post().uri(signingUri)
                    .header("Authorization", "Bearer " + serviceRoleKey).header("apikey", serviceRoleKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("expiresIn", expiresInSeconds))
                    .retrieve().body(JsonNode.class);
            // 응답의 signedURL 은 '/object/sign/…?token=…' 처럼 /storage/v1 뒤에 붙는 경로라 base 를 앞에 붙여 완성한다
            String signed = body == null ? null : body.path("signedURL").asString();
            if (signed == null || signed.isBlank()) {
                log.error("Storage 서명 URL 응답에 주소가 없음 {}", bucket + "/" + path);
                throw new ApiException(ErrorCode.UPSTREAM);
            }
            return base + signed;
        } catch (HttpStatusCodeException e) {
            log.error("Storage 서명 URL 실패 {} {}", e.getStatusCode(), bucket + "/" + path);
            throw new ApiException(e.getStatusCode() == HttpStatus.NOT_FOUND ? ErrorCode.NOT_FOUND : ErrorCode.UPSTREAM);
        } catch (RestClientException e) {
            log.error("Storage 연결 실패 {}: {}", bucket + "/" + path, e.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM);
        }
    }

    /**
     * 폴더 안의 파일 경로 목록 (`<folder>/<name>`). 탈퇴 때 이 고객 폴더를 통째로 지우려고 (감사 M-04). 1,000개씩 끝까지 읽는다.
     * Supabase Storage `POST /object/list/{bucket}` — 하위 폴더(id 가 null)는 건너뛴다.
     */
    public java.util.List<String> list(String bucket, String folder) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int page = 1000;
        try {
            // 끝 조건이 없는 for: 한 번에 page 개씩 offset 을 늘려 가며 읽고, 빈 응답이거나 page 개보다 적게 오면 마지막 쪽으로 보고 끝낸다
            for (int offset = 0; ; offset += page) {
                JsonNode body = client.post().uri("/object/list/{bucket}", bucket)
                        .header("Authorization", "Bearer " + serviceRoleKey).header("apikey", serviceRoleKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("prefix", folder, "limit", page, "offset", offset))
                        .retrieve().body(JsonNode.class);
                if (body == null || !body.isArray() || body.isEmpty()) return out;
                for (JsonNode o : body) {
                    if (o.path("id").isNull() || o.path("id").isMissingNode()) continue; // 폴더
                    out.add(folder + "/" + o.path("name").asString());
                }
                if (body.size() < page) return out;
            }
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) return out; // 폴더·버킷이 없으면 지금까지 모은 목록(대개 빈 목록)으로 끝낸다
            log.error("Storage 목록 실패 {} {}", e.getStatusCode(), bucket);
            throw new ApiException(ErrorCode.UPSTREAM);
        } catch (RestClientException e) {
            log.error("Storage 연결 실패 {}: {}", bucket, e.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM);
        }
    }

    // 이미 없는 파일(404)은 성공으로 친다 — 그래서 한 번 지운 파일을 재시도에서 다시 지우거나,
    // 업로드가 실패해 처음부터 없던 수리 사진을 지우려 해도 오류로 멈추지 않는다
    /** 삭제. 이미 없으면 그대로 */
    public void delete(String bucket, String path) {
        try {
            client.delete().uri("/object/{bucket}/{path}", bucket, path)
                    .header("Authorization", "Bearer " + serviceRoleKey).header("apikey", serviceRoleKey)
                    .retrieve().toBodilessEntity();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) return;
            log.error("Storage 삭제 실패 {} {}", e.getStatusCode(), bucket + "/" + path);
            throw new ApiException(ErrorCode.UPSTREAM);
        } catch (RestClientException e) {
            log.error("Storage 연결 실패 {}: {}", bucket + "/" + path, e.getMessage());
            throw new ApiException(ErrorCode.UPSTREAM); // 연결 실패·타임아웃이 500 으로 새거나 보존 삭제 루프를 끊지 않게
        }
    }
}
