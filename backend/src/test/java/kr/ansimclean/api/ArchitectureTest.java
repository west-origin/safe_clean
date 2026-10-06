package kr.ansimclean.api;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// ArchUnit: 컴파일된 클래스 파일을 읽어 '어느 클래스가 어느 클래스를 쓰는지' 를 규칙으로 검사하는 라이브러리.
// 여기서 지키는 경계: Controller(입출력) → Service(규칙) → Repository(DB) 한 방향 (CLAUDE.md §11-1),
// 다른 기능의 Repository 를 직접 쓰지 않기, common 은 기능 패키지를 모르기, 기능 패키지 사이 순환 없음.
// DB·스프링 없이 도는 단위 테스트다. 규칙 문장은 noClasses()/classes() … should() … check(classes) 모양으로 읽는다.
/** 패키지 의존 방향 (BACKEND_PLAN.md §2.4). 위반하면 verify 가 실패한다. */
class ArchitectureTest {
    // 검사 대상: kr.ansimclean.api 아래 main 코드의 클래스 전부 (테스트 클래스는 뺀다). 한 번 읽어 모든 테스트가 같이 쓴다
    static JavaClasses classes;

    // @BeforeAll: 이 클래스의 테스트들이 돌기 전에 한 번만 부른다 (그래서 static)
    @BeforeAll
    static void load() {
        classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("kr.ansimclean.api");
    }

    // 이름이 …Controller 인 클래스가 이름이 …Repository 인 클래스를 참조하면 실패
    @Test
    void 컨트롤러는_Repository_를_직접_쓰지_않는다() {
        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(classes);
    }

    @Test
    void 컨트롤러_말고는_아무도_컨트롤러에_의존하지_않는다() {
        // Service→Controller, Entity→Controller 같은 역방향 (§11-1)
        noClasses().that().resideInAPackage("kr.ansimclean.api..").and().haveSimpleNameNotEndingWith("Controller")
                // 테스트 클래스는 load() 에서 이미 빠지므로 아래 조건은 겹치는 안전장치다
                .and().haveSimpleNameNotEndingWith("ControllerTest")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
                .check(classes);
    }

    @Test
    void 컨트롤러는_DB_에_직접_닿지_않는다() {
        // 이름이 *Repository 가 아닌 DB 접근(Spring Data 인터페이스·JdbcClient·EntityManager·SupabaseUserLookup 같은 조회 클래스)도 막는다
        noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat(JavaClass.Predicates.assignableTo(org.springframework.data.repository.Repository.class)
                        .or(JavaClass.Predicates.resideInAnyPackage("org.springframework.jdbc..", "jakarta.persistence..", "org.springframework.data.."))
                        .or(JavaClass.Predicates.simpleName("SupabaseUserLookup")))
                .check(classes);
    }

    // 컨트롤러가 쓰는 Service 는 같은 패키지(같은 기능)이거나 common 의 것이어야 한다 (판정은 아래 onlyUseServicesFromOwnPackage)
    @Test
    void 컨트롤러는_자기_기능의_Service_만_부른다() {
        classes().that().haveSimpleNameEndingWith("Controller")
                .should(onlyUseServicesFromOwnPackage())
                .check(classes);
    }

    // 다른 기능의 데이터가 필요하면 그 기능의 Service 를 부른다 — Repository 를 건너 쓰지 않는다 (판정은 onlyBeUsedFromOwnPackage)
    @Test
    void Repository_는_같은_기능_패키지_안에서만_쓴다() {
        classes().that().haveSimpleNameEndingWith("Repository")
                .should(onlyBeUsedFromOwnPackage())
                .check(classes);
    }

    // common(설정·보안·오류·아웃박스 같은 공용 부품) 안의 클래스가 common 밖의 kr.ansimclean.api 클래스를 쓰면 실패 — 기능 → common 한 방향만
    @Test
    void common_은_기능_패키지에_의존하지_않는다() {
        noClasses().that().resideInAPackage("kr.ansimclean.api.common..")
                .should().dependOnClassesThat(JavaClass.Predicates.resideInAPackage("kr.ansimclean.api..")
                        .and(JavaClass.Predicates.resideOutsideOfPackage("kr.ansimclean.api.common..")))
                .check(classes);
    }

    // (*) 자리의 패키지 이름(auth, booking, offer …)마다 한 덩어리로 묶고, 덩어리 사이 의존이 A → B → A 처럼 돌아오면 실패.
    // 그래서 auth 는 booking 을 직접 부르지 않고 AccountPurge·AccountDeletionGuard 같은 인터페이스를 booking 이 구현하게 둔다
    @Test
    void 기능_패키지_사이에_순환이_없다() {
        slices().matching("kr.ansimclean.api.(*)..").should().beFreeOfCycles().check(classes);
    }

    /**
     * 직접 만든 검사 규칙(ArchCondition). 컨트롤러 하나를 받아, 그 컨트롤러가 직접 참조하는 클래스 중
     * 우리 코드의 …Service 마다 "같은 패키지 또는 common 인가" 를 판정해 events 에 적는다. ok=false 인 기록이 있으면 테스트가 실패한다
     */
    private static ArchCondition<JavaClass> onlyUseServicesFromOwnPackage() {
        return new ArchCondition<>("only use services from its own package") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                controller.getDirectDependenciesFromSelf().forEach(dep -> {
                    JavaClass target = dep.getTargetClass();
                    // Service 가 아니거나 우리 패키지 밖(스프링 등)의 클래스는 판정하지 않고 넘어간다 (forEach 안의 return = 다음 항목으로)
                    if (!target.getSimpleName().endsWith("Service") || !target.getPackageName().startsWith("kr.ansimclean.api")) return;
                    boolean ok = target.getPackageName().equals(controller.getPackageName()) || target.getPackageName().startsWith("kr.ansimclean.api.common");
                    events.add(new SimpleConditionEvent(controller, ok, controller.getSimpleName() + " 가 다른 기능의 " + target.getSimpleName() + " 를 직접 쓴다 (자기 기능의 Service 가 부를 것)"));
                });
            }
        };
    }

    /** 검사 규칙: Repository 하나를 받아, 그것을 직접 참조하는 모든 클래스(origin)가 같은 패키지인지 판정한다. 하위 패키지도 '다른 패키지' 로 본다 */
    private static ArchCondition<JavaClass> onlyBeUsedFromOwnPackage() {
        return new ArchCondition<>("only be used from its own package") {
            @Override
            public void check(JavaClass repository, ConditionEvents events) {
                repository.getDirectDependenciesToSelf().forEach(dep -> {
                    JavaClass origin = dep.getOriginClass();
                    boolean ok = origin.getPackageName().equals(repository.getPackageName());
                    events.add(new SimpleConditionEvent(repository, ok, origin.getName() + " 가 " + repository.getSimpleName() + " 를 직접 쓴다 (그 기능의 Service 를 부를 것)"));
                });
            }
        };
    }
}
