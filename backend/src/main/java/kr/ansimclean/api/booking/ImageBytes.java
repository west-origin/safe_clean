package kr.ansimclean.api.booking;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * 업로드한 사진의 **실제 내용**을 본다 (감사 M-02). 클라이언트가 보낸 Content-Type 은 믿지 않는다.
 * 파일 앞부분의 서명(magic bytes)으로 형식을 고르고, JPG·PNG·WEBP 는 헤더에서 가로·세로를 읽어 픽셀 수 상한을 건다
 * (5MB 안에 수억 픽셀짜리 '압축 폭탄'을 넣어 매니저 앱 화면을 멈추지 못하게). HEIC/HEIF 는 서명만 본다 — 헤더 구조가 복잡해 여기서 풀지 않는다.
 * 서버는 이미지를 디코딩하지 않는다(헤더 몇 바이트만 읽는다).
 * 예: RepairPhotoService.upload 가 detect 로 형식을 고르고, HEIC 가 아니면 dimensions 로 가로·세로를 읽어 픽셀 수를 검사한다.
 * static 메서드만 모은 도구 클래스라 객체를 만들지 않는다(생성자가 private). public 이 아니라 booking 패키지 안에서만 쓴다.
 * 짧거나 깨진 파일이어도 예외를 내지 않고 빈 값(Optional.empty)을 돌려준다 — 부르는 쪽이 그것을 '읽을 수 없는 사진' 400 으로 바꾼다.
 */
final class ImageBytes {
    /** 폰 카메라 사진(최대 5,000만 화소 안팎)은 넉넉히 통과하고, 5MB 안의 비정상적인 해상도만 막는다 */
    static final long MAX_PIXELS = 100_000_000L;
    // HEIC/HEIF 파일 앞 'ftyp' 상자 바로 뒤 4글자(brand, 파일 종류 이름)로 올 수 있는 값들
    private static final Set<String> HEIF_BRANDS = Set.of("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1", "heif");

    private ImageBytes() {}

    /** image/jpeg | image/png | image/webp | image/heic. 모르는 형식이면 빈 값 */
    static Optional<String> detect(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) return Optional.of("image/jpeg"); // JPEG 는 FF D8 FF 로 시작한다
        if (startsWith(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of("image/png"); // PNG 고정 서명 8바이트
        if (ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WEBP")) return Optional.of("image/webp"); // 'RIFF' + 파일 길이 4바이트 + 'WEBP'
        if (ascii(b, 4, 4).equals("ftyp") && HEIF_BRANDS.contains(ascii(b, 8, 4))) return Optional.of("image/heic"); // 상자 길이 4바이트 + 'ftyp' + brand. HEIF 도 image/heic 로 묶는다
        return Optional.empty();
    }

    /** 헤더에서 읽은 가로×세로. 읽지 못하면(깨진 파일) 빈 값. HEIC 는 항상 빈 값 */
    // 결과 배열은 { 가로, 세로 } 순서다 (RepairPhotoService 가 가로×세로가 MAX_PIXELS 를 넘는지 본다)
    static Optional<long[]> dimensions(byte[] b, String type) {
        return switch (type) {
            // PNG: 서명 8바이트 + 길이 4바이트 뒤 12번째부터 'IHDR' 조각. 16번째·20번째에 가로·세로(4바이트, 큰 자리 먼저)
            case "image/png" -> ascii(b, 12, 4).equals("IHDR") ? Optional.of(new long[] { u32(b, 16), u32(b, 20) }) : Optional.empty();
            case "image/jpeg" -> jpeg(b);
            case "image/webp" -> webp(b);
            default -> Optional.empty();
        };
    }

    /** JPEG: 세그먼트를 따라가다 SOFn(프레임 헤더)에서 높이·너비를 읽는다 */
    private static Optional<long[]> jpeg(byte[] b) {
        // JPEG 는 'FF 표식 + 길이 2바이트 + 내용' 세그먼트가 줄지어 있다. 맨 앞 FF D8(파일 시작) 2바이트 다음부터 본다
        int i = 2;
        while (i + 9 < b.length) { // SOF 에서 읽는 마지막 바이트(i + 8)까지 파일 안에 있을 때만
            if (u8(b, i) != 0xFF) return Optional.empty(); // 세그먼트는 FF 로 시작해야 한다 — 아니면 깨진 파일
            int marker = u8(b, i + 1);
            if (marker == 0xFF) { // 채움 바이트
                i++;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) { // 길이 없는 표식
                i += 2;
                continue;
            }
            // C0~CF 가 SOF(프레임 헤더)인데, 그 사이의 C4(허프만 표)·C8(예약)·CC(산술 부호 표)는 SOF 가 아니다
            boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            // SOF 배치: 표식 2 + 길이 2 + 정밀도 1 + 높이 2(i+5) + 너비 2(i+7). 돌려줄 때는 { 너비, 높이 } 로 바꿔 담는다
            if (sof) return Optional.of(new long[] { u16(b, i + 7), u16(b, i + 5) });
            if (marker == 0xDA || marker == 0xD9) return Optional.empty(); // 영상 데이터가 시작됐는데 프레임 헤더가 없었다
            i += 2 + u16(b, i + 2); // 다음 세그먼트로: 표식 2바이트 + 길이 값(길이 칸 2바이트를 포함한다)
        }
        return Optional.empty();
    }

    /**
     * WEBP: 12번째 바이트의 첫 조각 이름에 따라 가로·세로 위치가 다르다.
     * 'VP8 '(손실 압축) = 14비트 값 그대로, 'VP8L'(무손실) = 비트로 붙어 있는 14비트 값 + 1, 'VP8X'(확장) = 3바이트 값 + 1
     */
    private static Optional<long[]> webp(byte[] b) {
        if (b.length < 30) return Optional.empty(); // 아래에서 읽는 가장 뒤 바이트(29번째)가 없으면 깨진 파일
        String chunk = ascii(b, 12, 4);
        return switch (chunk) {
            case "VP8 " -> Optional.of(new long[] { u16le(b, 26) & 0x3FFF, u16le(b, 28) & 0x3FFF }); // 위 2비트는 확대 배율이라 버린다
            case "VP8L" -> Optional.of(new long[] {
                    1 + (((u8(b, 22) & 0x3F) << 8) | u8(b, 21)),
                    1 + (((u8(b, 24) & 0x0F) << 10) | (u8(b, 23) << 2) | ((u8(b, 22) & 0xC0) >> 6)) });
            case "VP8X" -> Optional.of(new long[] { 1 + u24le(b, 24), 1 + u24le(b, 27) });
            default -> Optional.empty();
        };
    }

    // 아래는 바이트 배열을 읽는 작은 도구들. 파일 끝을 넘으면 u8 은 0, ascii 는 빈 글자, startsWith 는 false 를 돌려줘
    // 짧거나 깨진 파일에서도 배열 밖을 읽는 예외가 나지 않는다

    /** b 의 offset 위치부터 expected 바이트들이 그대로 있는가 */
    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) return false;
        for (int k = 0; k < expected.length; k++) if (u8(b, offset + k) != expected[k]) return false;
        return true;
    }

    /** offset 부터 length 바이트를 글자로 (ISO_8859_1 = 바이트 하나를 글자 하나로 그대로 바꾼다). 모자라면 빈 글자 */
    private static String ascii(byte[] b, int offset, int length) {
        return b.length < offset + length ? "" : new String(b, offset, length, StandardCharsets.ISO_8859_1);
    }

    /** 바이트 1개를 0~255 로. 자바 byte 는 -128~127 이라 0xFF 와 AND 해서 부호를 없앤다 */
    private static int u8(byte[] b, int i) {
        return i < b.length ? b[i] & 0xFF : 0;
    }

    /** 2바이트 수, 큰 자리 바이트가 먼저 (JPEG 방식) */
    private static int u16(byte[] b, int i) {
        return (u8(b, i) << 8) | u8(b, i + 1);
    }

    /** 2바이트 수, 작은 자리 바이트가 먼저 (le = little-endian, WEBP 방식) */
    private static int u16le(byte[] b, int i) {
        return u8(b, i) | (u8(b, i + 1) << 8);
    }

    /** 3바이트 수, 작은 자리 바이트가 먼저 (WEBP VP8X) */
    private static int u24le(byte[] b, int i) {
        return u8(b, i) | (u8(b, i + 1) << 8) | (u8(b, i + 2) << 16);
    }

    /** 4바이트 수, 큰 자리 바이트가 먼저 (PNG). int 로는 부호가 생길 수 있어 long 으로 돌려준다 */
    private static long u32(byte[] b, int i) {
        return ((long) u8(b, i) << 24) | ((long) u8(b, i + 1) << 16) | ((long) u8(b, i + 2) << 8) | u8(b, i + 3);
    }
}
