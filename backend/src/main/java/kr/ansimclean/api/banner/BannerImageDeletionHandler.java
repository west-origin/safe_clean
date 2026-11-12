package kr.ansimclean.api.banner;

import kr.ansimclean.api.common.events.BannerImageDeleted;
import kr.ansimclean.api.common.outbox.OutboxHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/** 배너 삭제·이미지 교체 뒤 옛 파일 삭제 (아웃박스). 이미 지워졌으면(404) 조용히 끝나므로 다시 와도 결과가 같다 */
@Component
public class BannerImageDeletionHandler implements OutboxHandler {
    private final BannerImages images;
    private final JsonMapper json = JsonMapper.builder().build();

    public BannerImageDeletionHandler(BannerImages images) {
        this.images = images;
    }

    @Override
    public Set<String> types() {
        return Set.of("BannerImageDeleted");
    }

    @Override
    public void handle(String type, String payloadJson) {
        images.delete(json.readValue(payloadJson, BannerImageDeleted.class).path());
    }
}
