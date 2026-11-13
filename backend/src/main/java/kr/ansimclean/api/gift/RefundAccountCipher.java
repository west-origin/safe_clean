package kr.ansimclean.api.gift;

import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 금액권 계좌 환불의 계좌번호 암호화 (사용자 결정 2026-11-13: 암호화 저장, 송금 후 삭제).
 * AES-256-GCM, 키는 환경변수 REFUND_ACCOUNT_KEY (base64 32바이트 — 예: openssl rand -base64 32). 저장 값 = base64(IV 12바이트 + 암호문·태그).
 * 키가 없으면 계좌 환불 접수를 막는다(INVALID_STATE). 키를 바꾸면 처리 중인 요청의 계좌는 읽을 수 없으니 남은 요청을 처리한 뒤 바꾼다
 */
@Component
public class RefundAccountCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public RefundAccountCipher(@Value("${app.refund.account-key:}") String base64Key) {
        SecretKeySpec k = null;
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] raw = Base64.getDecoder().decode(base64Key.strip());
            if (raw.length != 32) throw new IllegalStateException("REFUND_ACCOUNT_KEY 는 base64 로 인코딩한 32바이트여야 해요");
            k = new SecretKeySpec(raw, "AES");
        }
        this.key = k;
    }

    public boolean enabled() {
        return key != null;
    }

    public String encrypt(String plain) {
        if (key == null) throw new ApiException(ErrorCode.INVALID_STATE, "환불 접수를 준비하고 있어요. 고객센터로 문의해주세요");
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] body = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("계좌번호 암호화 실패", e);
        }
    }

    public String decrypt(String stored) {
        if (key == null) throw new ApiException(ErrorCode.INVALID_STATE, "계좌번호를 열 수 있는 키가 없어요");
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.INVALID_STATE, "계좌번호를 열 수 없어요. 키가 바뀌었는지 확인해주세요");
        }
    }
}
