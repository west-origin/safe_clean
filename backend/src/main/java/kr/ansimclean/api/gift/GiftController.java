package kr.ansimclean.api.gift;

import jakarta.validation.Valid;
import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.gift.dto.CreateGiftRequest;
import kr.ansimclean.api.gift.dto.GiftCatalogResponse;
import kr.ansimclean.api.gift.dto.GiftDetailResponse;
import kr.ansimclean.api.gift.dto.GiftsResponse;
import kr.ansimclean.api.gift.dto.VouchersResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 고객 금액권·선물 API (docs/api.md '금액권·선물'). 가입을 끝낸 고객만(SecurityConfig /customer/**).
 * 구매 선물의 결제 승인·조회는 기존 /customer/payments/{id}(/confirm) 를 그대로 쓴다 (orderId = 응답의 checkout.orderId)
 */
@RestController
@RequestMapping(SecurityConfig.API + "/customer")
public class GiftController {
    private final GiftService service;

    public GiftController(GiftService service) {
        this.service = service;
    }

    /** 새로 구매해 선물할 수 있는 항목 (앱 getGiftCatalog) */
    @GetMapping("/gifts/catalog")
    public GiftCatalogResponse catalog() {
        return service.catalog();
    }

    /** 내 금액권 (앱 getVouchers) */
    @GetMapping("/vouchers")
    public VouchersResponse vouchers(@AuthenticationPrincipal CurrentUser user) {
        return service.vouchers(user.id());
    }

    /** 선물 보내기 (앱 sendGift). Idempotency-Key 선택 */
    @PostMapping("/gifts")
    public GiftDetailResponse create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateGiftRequest req,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.create(user.id(), key, req);
    }

    /** 보낸·받은 선물 (앱 getGifts) */
    @GetMapping("/gifts")
    public GiftsResponse gifts(@AuthenticationPrincipal CurrentUser user, @RequestParam("box") String box) {
        return service.gifts(user.id(), box);
    }

    /** 선물 한 건 (앱 getGift) — 푸시를 탭했을 때·결제를 이어갈 때 */
    @GetMapping("/gifts/{id}")
    public GiftDetailResponse detail(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.detail(user.id(), id);
    }

    @PostMapping("/gifts/{id}/accept")
    public GiftDetailResponse accept(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.accept(user.id(), id);
    }

    @PostMapping("/gifts/{id}/reject")
    public GiftDetailResponse reject(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.reject(user.id(), id);
    }

    @PostMapping("/gifts/{id}/cancel")
    public GiftDetailResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.cancel(user.id(), id);
    }
}
