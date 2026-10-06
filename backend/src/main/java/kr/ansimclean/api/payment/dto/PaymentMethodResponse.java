package kr.ansimclean.api.payment.dto;

import java.util.UUID;

// 괄호 안 이름이 그대로 JSON 키가 된다 → { "id", "label", "isDefault" }. 고객 앱 getPaymentMethods 가 받는 모양과 같아서(docs/api.md),
// 이름을 바꾸면 api.md 와 앱도 같이 바꾼다. 만드는 곳: PaymentService.methods — 빌링키는 앱에 내보내지 않으려고 일부러 뺐다
/** GET /customer/payment-methods 의 한 줄 */
public record PaymentMethodResponse(UUID id, String label, boolean isDefault) {}
