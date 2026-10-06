package kr.ansimclean.api.booking;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// JPA(자바 객체 ↔ DB 행을 이어 주는 표준) 의 값 변환기. 자바에서는 BookingStatus enum 으로 다루고,
// DB 에는 "in_progress" 같은 소문자 문자열로 저장한다. autoApply 가 없어 @Convert(converter = …) 를 붙인 필드(예: Booking.status)에만 쓰인다.
/** BookingStatus ↔ bookings.status(text). 엔티티 필드에 @Convert 로 붙인다 */
@Converter
public class BookingStatusConverter implements AttributeConverter<BookingStatus, String> {
    /** 저장할 때: enum → DB 문자열 (null 은 그대로 null) */
    @Override
    public String convertToDatabaseColumn(BookingStatus status) {
        return status == null ? null : status.db();
    }

    /** 읽을 때: DB 문자열 → enum. 모르는 값이면 BookingStatus.fromDb 가 예외를 던진다 */
    @Override
    public BookingStatus convertToEntityAttribute(String db) {
        return db == null ? null : BookingStatus.fromDb(db);
    }
}
