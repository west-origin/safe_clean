package kr.ansimclean.api.support;

import kr.ansimclean.api.catalog.AppSettingsService;
import kr.ansimclean.api.common.error.ApiException;
import kr.ansimclean.api.common.security.Roles;
import kr.ansimclean.api.support.dto.ManagerTermsResponse;
import kr.ansimclean.api.support.dto.TermResponse;
import kr.ansimclean.api.support.dto.TermSummary;
import kr.ansimclean.api.common.time.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 약관 목록·본문. 고객은 배열, 매니저는 { terms, note } — 앱 함수 계약이 다르다 (docs/api.md §5.1) */
// 클래스 기본이 readOnly 라, 쓰는 메서드(recordAgreements·forgetAgreements)에는 @Transactional 을 따로 붙였다 —
// 쓰기 메서드를 새로 만들면 같이 붙인다.
// 두 쓰기 메서드는 부르는 쪽 묶음에 합쳐진다: recordAgreements 가 던진 VALIDATION 은 가입 완료(AuthService.completeSignup) 전체를,
// forgetAgreements 의 실패는 그 계정의 파기(DeletedAccountPurger)를 되돌린다. 부르는 곳 예: TermsController(목록·본문).
@Service
@Transactional(readOnly = true)
public class TermsService {
    public static final String SETTING_MANAGER_PRIVACY_NOTE = "manager_privacy_note"; // app_settings 키 — 매니저 약관 안내 문구

    private final TermRepository terms;
    private final TermAgreementRepository agreements;
    private final AppSettingsService settings;
    private final AppClock clock;

    public TermsService(TermRepository terms, TermAgreementRepository agreements, AppSettingsService settings, AppClock clock) {
        this.terms = terms;
        this.agreements = agreements;
        this.settings = settings;
        this.clock = clock;
    }

    /**
     * 가입 완료 때 동의 기록. 필수 약관이 하나라도 빠지면 VALIDATION (어느 약관인지 문구에 넣는다).
     * 모르는 id 는 무시하지 않고 VALIDATION.
     */
    @Transactional
    public void recordAgreements(UUID userId, String audience, List<String> agreedIds) {
        // audience 는 가입한 계정의 역할(profiles.role). 동의 목록은 Set 으로 바꿔 같은 id 가 두 번 와도 한 번만 센다(null 이면 빈 목록)
        List<Term> all = terms.findByAudienceOrderBySort(audience);
        Set<String> agreed = new HashSet<>(agreedIds == null ? List.of() : agreedIds);
        for (String id : agreed) {
            if (all.stream().noneMatch(t -> t.getId().equals(id))) throw ApiException.validation("알 수 없는 약관이에요: " + id);
        }
        for (Term t : all) {
            if (t.isRequired() && !agreed.contains(t.getId())) throw ApiException.validation("필수 약관에 동의해주세요: " + t.getTitle());
        }
        // 두 검사를 모두 통과한 뒤에만 저장한다. 약관의 지금 version 을 같이 적어 어느 판에 동의했는지 남긴다
        for (Term t : all) {
            if (agreed.contains(t.getId())) agreements.save(TermAgreement.of(userId, t.getId(), t.getVersion(), clock.now()));
        }
    }

    /** 고객 약관 목록 [{ id, label, required }] (sort 순) */
    public List<TermSummary> customerTerms() {
        return summaries(Roles.CUSTOMER);
    }

    /** 매니저 약관 목록 + 안내 문구 { terms, note }. note 는 app_settings 의 manager_privacy_note (V005) */
    public ManagerTermsResponse managerTerms() {
        return new ManagerTermsResponse(summaries(Roles.MANAGER), settings.node(SETTING_MANAGER_PRIVACY_NOTE).asString());
    }

    /** 약관 본문 { id, title, body }. (id, audience) 두 열 키로 찾고, 없으면 404 */
    public TermResponse term(String id, String audience) {
        requireAudience(audience);
        Term t = terms.findById(new Term.Key(id, audience)).orElseThrow(ApiException::notFound);
        return new TermResponse(t.getId(), t.getTitle(), t.getBody());
    }

    /**
     * audience 가 customer·manager 가 아니면 400(VALIDATION).
     * static 이라 TermsController·SupportService 가 객체 없이 바로 부른다.
     */
    public static void requireAudience(String audience) {
        if (!Roles.CUSTOMER.equals(audience) && !Roles.MANAGER.equals(audience)) throw ApiException.validation("audience 는 customer 또는 manager 여야 해요");
    }

    /** 그 대상의 약관 행 → 목록 항목(TermSummary: id·label·required) */
    private List<TermSummary> summaries(String audience) {
        return terms.findByAudienceOrderBySort(audience).stream().map(t -> new TermSummary(t.getId(), t.getLabel(), t.isRequired())).toList();
    }

    /** 탈퇴 + 5년 파기 — 약관 동의 기록 (DeletedAccountPurger). 클래스는 읽기 전용이라 쓰기로 연다 */
    @Transactional
    public void forgetAgreements(UUID userId) {
        agreements.deleteByUserId(userId);
    }
}
