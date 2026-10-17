package kr.ansimclean.api.job;

import kr.ansimclean.api.common.security.CurrentUser;
import kr.ansimclean.api.common.security.SecurityConfig;
import kr.ansimclean.api.job.dto.CustomerChecklistResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** job → live 의 기존 의존 방향을 유지하며 고객 경로를 제공한다. */
@RestController
@RequestMapping(SecurityConfig.API + "/customer/bookings")
public class CustomerChecklistController {
    private final JobService service;

    public CustomerChecklistController(JobService service) { this.service = service; }

    @GetMapping("/{id}/checklist")
    public CustomerChecklistResponse checklist(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return service.customerChecklist(user.id(), id);
    }
}
