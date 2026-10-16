package kr.ansimclean.api.payment.dto;
import jakarta.validation.constraints.*;
public record ConfirmPaymentRequest(@NotBlank @Size(max = 200) String paymentKey, @Positive long amount) {}
