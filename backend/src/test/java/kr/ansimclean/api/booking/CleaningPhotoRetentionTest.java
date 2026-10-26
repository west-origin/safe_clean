package kr.ansimclean.api.booking;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 매일 정리 진입점: 서비스의 deleteExpired 를 부르고, 실패해도 예외를 밖으로 던지지 않는다(다음 날 다시) */
class CleaningPhotoRetentionTest {
    CleaningPhotoService photos = mock(CleaningPhotoService.class);
    CleaningPhotoRetention retention = new CleaningPhotoRetention(photos);

    @Test
    void 정리를_부른다() {
        when(photos.deleteExpired()).thenReturn(3);
        retention.run();
        verify(photos).deleteExpired();
    }

    @Test
    void 정리_전체가_실패해도_던지지_않는다() {
        when(photos.deleteExpired()).thenThrow(new IllegalStateException("db down"));
        retention.run(); // 예외가 새면 테스트가 실패한다
    }
}
