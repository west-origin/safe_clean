package kr.ansimclean.api.banner;

import kr.ansimclean.api.common.supabase.SupabaseStorageClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 아웃박스 BannerImageDeleted → banners 버킷의 그 경로를 지운다 */
class BannerImageDeletionHandlerTest {
    @Test
    void 이벤트의_경로를_지운다() {
        SupabaseStorageClient storage = mock(SupabaseStorageClient.class);
        BannerImageDeletionHandler handler = new BannerImageDeletionHandler(new BannerImages(storage));
        assertThat(handler.types()).containsExactly("BannerImageDeleted");
        handler.handle("BannerImageDeleted", "{\"path\":\"b1/x.png\"}");
        verify(storage).delete(BannerImages.BUCKET, "b1/x.png");
    }
}
