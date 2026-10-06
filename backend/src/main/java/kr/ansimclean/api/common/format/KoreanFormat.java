package kr.ansimclean.api.common.format;

import kr.ansimclean.api.common.time.AppClock;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 화면에 보이는 한국어 문자열은 전부 여기서 만든다 (BACKEND_PLAN.md §6.7). 앱은 포맷을 모른다.
 * 시간대는 항상 Asia/Seoul. 요일 배열은 앱과 같다(일·월·화·수·목·금·토).
 * 쓰는 곳: 각 서비스가 응답 DTO 의 표시용 문자열(날짜·금액·주소 요약·가려진 이름 등)을 만들 때, 그리고 로그의 전화번호 가리기.
 * LocalDate·LocalTime 을 받는 메서드는 이미 서울 날짜·시각이라고 보고 그대로 쓴다.
 * Instant(UTC 순간)를 받아 시각·날짜를 보이는 메서드는 AppClock.toSeoul 로 서울 시각으로 바꾼 뒤 쓴다
 * (untilExpiry 처럼 두 순간의 차이만 보는 것은 바꿀 필요가 없다).
 * final 클래스 + private 생성자: 객체를 만들지 않고 KoreanFormat.won(…) 처럼 static 메서드로만 부르는 도구 모음이다.
 * 위 '앱은 포맷을 모른다' 의 예외: 앱도 직접 만드는 표시가 있다 — 서버에 보내기 전인 예약 초안의 날짜·시간
 * (packages/ui 의 dateKey.js: formatDate·formatTimeKo 등), 1초마다 바뀌는 콜 만료 'm:ss 후 만료'·작업 경과 '01:23:42'
 * (useCountdown·useElapsed). 여기 형식을 바꾸면 그쪽도 같이 본다.
 */
public final class KoreanFormat {
    private static final String[] WEEKDAY = { "일", "월", "화", "수", "목", "금", "토" }; // 칸 번호 = weekdayIndex (일요일 0)
    private static final String[] WEEKDAY_LONG = { "일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일" };
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm"); // 24시간제 두 자리 (09:05)
    private static final Pattern LOCAL_PHONE = Pattern.compile("^\\d{8}$"); // 010 을 뺀 숫자 8자리
    private static final Pattern E164_KR_MOBILE = Pattern.compile("^\\+8210\\d{8}$"); // 국제 형식 +82 10 + 8자리

    private KoreanFormat() {}

    /** 일요일=0 … 토요일=6 (java DayOfWeek 는 월=1 … 일=7) */
    private static int weekdayIndex(LocalDate d) {
        return d.getDayOfWeek().getValue() % 7;
    }

    /** 2026-09-15 → "9월 15일 (화)" */
    public static String dateLabel(LocalDate d) {
        return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 (" + WEEKDAY[weekdayIndex(d)] + ")";
    }

    /** → "9월 15일" (정산 목록) */
    public static String dateShort(LocalDate d) {
        return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일";
    }

    // 아래 dateLongWeekday·dateTimeLong 이 안에서 쓴다
    /** → "2026년 9월 15일" */
    public static String dateLong(LocalDate d) {
        return d.getYear() + "년 " + d.getMonthValue() + "월 " + d.getDayOfMonth() + "일";
    }

    /** → "2026년 11월 1일 (일)" (견적의 회차 날짜) */
    public static String dateLongWeekday(LocalDate d) {
        return dateLong(d) + " (" + WEEKDAY[weekdayIndex(d)] + ")";
    }

    /** → "2026년 11월 1일 (일) 오전 9시" (Reservations 일정 행) */
    public static String dateTimeLong(LocalDate d, LocalTime t) {
        return dateLong(d) + " (" + WEEKDAY[weekdayIndex(d)] + ") " + timeKo(t);
    }

    /** → "9월 18일 오후 2:00" (홈 "가장 가까운 콜" 요약) */
    public static String dateTimeShort(LocalDate d, LocalTime t) {
        int h = t.getHour();
        int hour12 = h % 12 == 0 ? 12 : h % 12; // 12시간제: 0시 → 오전 12, 12시 → 오후 12, 14시 → 오후 2
        return dateShort(d) + " " + (h < 12 ? "오전" : "오후") + " " + hour12 + ":" + String.format("%02d", t.getMinute());
    }

    /** → "2024년 3월" (활동 시작) */
    public static String yearMonth(LocalDate d) {
        return d.getYear() + "년 " + d.getMonthValue() + "월";
    }

    /** → "2026.03" (신원 인증·교육 이수 날짜) */
    public static String yearMonthDot(LocalDate d) {
        return String.format("%d.%02d", d.getYear(), d.getMonthValue());
    }

    /** 도로명주소 앞 n 어절: ("서울특별시 강남구 테헤란로 123", 3) → "서울특별시 강남구 테헤란로" (콜 카드 location, §6.2-6) */
    public static String addressHead(String roadAddress, int words) {
        if (roadAddress == null) return "";
        // 건물번호(숫자로 시작하는 어절·'지하') 앞에서 멈춘다 — 세종시처럼 시군구가 없는 주소는 앞 3어절에 번호까지 들어가 수락 전 후보 전원에게 정확한 주소가 보였다 (자체 점검 #27)
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String p : roadAddress.trim().split("\\s+")) {
            if (out.size() >= words || p.isEmpty() || Character.isDigit(p.charAt(0)) || p.equals("지하")) break;
            out.add(p);
        }
        return String.join(" ", out);
    }

    /** 시/도를 뺀 지역 2어절: "서울특별시 강남구 테헤란로 123" → "강남구 테헤란로" (홈 "14:02부터 진행 · 강남구 역삼동", 일정 카드 고객 행) */
    public static String addressArea(String roadAddress) {
        if (roadAddress == null) return "";
        String[] parts = roadAddress.trim().split("\\s+"); // 공백 여러 칸도 한 번의 구분으로 본다
        if (parts.length <= 1) return roadAddress.trim(); // 어절이 하나뿐이면 뺄 시/도가 없어 그대로
        // 2번째·3번째 어절(칸 번호 1, 2)만. 어절이 2개뿐이면 1개만 나온다
        return String.join(" ", java.util.Arrays.copyOfRange(parts, 1, Math.min(3, parts.length)));
    }

    /** → "9월 18일 목요일 오후 2:00" (콜 카드 제목) */
    public static String dateTimeCall(LocalDate d, LocalTime t) {
        int h = t.getHour();
        int hour12 = h % 12 == 0 ? 12 : h % 12;
        return dateShort(d) + " " + WEEKDAY_LONG[weekdayIndex(d)] + " " + (h < 12 ? "오전" : "오후") + " " + hour12 + ":" + String.format("%02d", t.getMinute());
    }

    /** 14:00 → "오후 2시", 14:30 → "오후 2시 30분" (앱 formatTimeKo 와 같은 규칙) */
    public static String timeKo(LocalTime t) {
        int h = t.getHour();
        int hour12 = h % 12 == 0 ? 12 : h % 12;
        String s = (h < 12 ? "오전" : "오후") + " " + hour12 + "시";
        return t.getMinute() == 0 ? s : s + " " + t.getMinute() + "분";
    }

    /** 14:00 → "14:00" (앱 time 필드) */
    public static String hhmm(LocalTime t) {
        return t.format(HHMM);
    }

    /** → "09:00 - 18:00" */
    public static String timeRange(LocalTime start, LocalTime end) {
        return start.format(HHMM) + " - " + end.format(HHMM);
    }

    /** 320 → "5:20" (녹화본 재생 위치·길이) */
    public static String mmss(long seconds) {
        long s = Math.max(0, seconds); // 음수가 오면 0:00 으로 (아래 hms·durationKo 도 같다)
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // 경과 시간(Duration)을 시:분:초 두 자리씩으로
    /** → "01:23:42" */
    public static String hms(Duration d) {
        long s = Math.max(0, d.getSeconds());
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    /** → "3시간 23분" · 1시간 미만이면 "23분" · 0이면 "0분" */
    public static String durationKo(Duration d) {
        long m = Math.max(0, d.toMinutes());
        long h = m / 60;
        long rest = m % 60;
        if (h == 0) return rest + "분";
        return rest == 0 ? h + "시간" : h + "시간 " + rest + "분"; // 딱 떨어지면 "3시간" 처럼 분을 붙이지 않는다
    }

    /** 85000 → "85,000원" */
    public static String won(long amount) {
        // %,d = 천 단위 구분자. Locale 을 주지 않아 JVM 기본 로케일의 구분자를 쓴다(한국어·영어 로케일은 쉼표)
        return String.format("%,d", amount) + "원";
    }

    /** 3000 → "+3,000원", -15000 → "-15,000원" (옵션 가격·정산 내역) */
    public static String wonSigned(long amount) {
        // 0 도 "+0원" 이 된다. 부호를 따로 붙이고 절댓값만 구분자 포맷에 넣는다
        return (amount >= 0 ? "+" : "-") + String.format("%,d", Math.abs(amount)) + "원";
    }

    // 콜 만료까지 남은 시간 (콜 목록·매니저 홈). 지금 시각 now 는 부르는 쪽이 넘긴다 — 이 함수는 시계를 읽지 않는다
    /** → "4:32 후 만료" (m:ss). 지났으면 "만료됨" */
    public static String untilExpiry(Instant expiresAt, Instant now) {
        long s = Duration.between(now, expiresAt).getSeconds();
        if (s <= 0) return "만료됨";
        return (s / 60) + ":" + String.format("%02d", s % 60) + " 후 만료";
    }

    /** (14:32, "부터 중단됨") → "14:32부터 중단됨" */
    public static String sinceLabel(Instant at, String suffix) {
        return AppClock.toSeoul(at).format(HHMM) + suffix;
    }

    /** → "14:32" */
    public static String clock(Instant at) {
        return AppClock.toSeoul(at).format(HHMM);
    }

    /** → "14:02 - 17:25" */
    public static String clockRange(Instant start, Instant end) {
        return clock(start) + " - " + clock(end);
    }

    // 아래 두 함수는 형식을 검사하지 않는다 — toE164 로 만든 +8210 + 8자리 값을 받는다고 보고 자리 수로 자른다(짧으면 예외)
    /** "+821012345678" → "010-1234-5678" */
    public static String phoneDisplay(String e164) {
        String local = "0" + e164.substring(3); // +82 10 1234 5678 → 01012345678
        return local.substring(0, 3) + "-" + local.substring(3, 7) + "-" + local.substring(7);
    }

    /** "+821012345678" → "12345678" (앱 PhoneField 의 앞자리 010 뺀 값) */
    public static String phoneLocal(String e164) {
        return e164.substring(5); // 앞 5글자 "+8210" 을 뗀다
    }

    // 부르는 곳: OtpService.toE164 (예외를 VALIDATION 400 으로 바꿔 준다)
    /** "12345678"(앱 입력, 010 뺀 8자리) → "+821012345678". 형식이 다르면 IllegalArgumentException */
    public static String toE164(String local) {
        String digits = local == null ? "" : local.replaceAll("[^0-9]", ""); // 숫자만 남긴다 — 하이픈·공백이 섞여 와도 된다
        if (digits.startsWith("010") && digits.length() == 11) digits = digits.substring(3); // "01012345678" 처럼 010 까지 온 11자리도 받는다
        if (!LOCAL_PHONE.matcher(digits).matches()) throw new IllegalArgumentException("휴대폰 번호는 010 뒤 8자리 숫자여야 해요");
        return "+8210" + digits;
    }

    /** "+8210" + 숫자 8자리 형식인지. 지금 서버 코드에서는 부르는 곳이 없고 KoreanFormatTest 만 쓴다 */
    public static boolean isE164KrMobile(String s) {
        return s != null && E164_KR_MOBILE.matcher(s).matches();
    }

    /** 로그용: "+821012345678" → "+8210****5678" */
    public static String maskPhone(String e164) {
        if (e164 == null || e164.length() < 8) return "****"; // 너무 짧으면 전부 가린다 — 로그에 번호가 새지 않게
        return e164.substring(0, 5) + "****" + e164.substring(e164.length() - 4);
    }

    /** "김하늘" → "김○○" (매니저에게 보이는 고객 이름) */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) return "";
        // 첫 글자만 남기고 나머지 글자 수만큼 ○. 한 글자 이름이어도 ○ 하나는 붙인다
        return name.substring(0, 1) + "○".repeat(Math.max(1, name.length() - 1));
    }

    /** → "2026.09.10" (공지 날짜) */
    public static String noticeDate(Instant at) {
        ZonedDateTime z = AppClock.toSeoul(at);
        return String.format("%d.%02d.%02d", z.getYear(), z.getMonthValue(), z.getDayOfMonth());
    }

    /** → "2025. 09. 11. 14:32" (라이브 시작 시각) */
    public static String liveStartedAt(Instant at) {
        ZonedDateTime z = AppClock.toSeoul(at);
        return String.format("%d. %02d. %02d. %02d:%02d", z.getYear(), z.getMonthValue(), z.getDayOfMonth(), z.getHour(), z.getMinute());
    }
}
