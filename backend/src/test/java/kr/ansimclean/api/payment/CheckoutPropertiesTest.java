package kr.ansimclean.api.payment;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
class CheckoutPropertiesTest {
    @Test void empty_keys_disable_charging_and_mixed_environments_are_not_enabled() {
        new ApplicationContextRunner().withUserConfiguration(CheckoutProperties.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CheckoutProperties.class).enabled()).isFalse();
        });
        assertThat(new CheckoutProperties("test_ck_example", "live_sk_example").enabled()).isFalse();
        assertThat(new CheckoutProperties("live_ck_example", "live_sk_example").toString()).doesNotContain("sk_example");
    }
    @Test void configured_keys_are_injected_into_the_gateway_boundary() {
        new ApplicationContextRunner().withUserConfiguration(CheckoutProperties.class)
                .withPropertyValues("app.payment.client-key=test_ck_example", "app.payment.secret-key=test_sk_example")
                .run(context -> assertThat(context.getBean(CheckoutProperties.class).enabled()).isTrue());
    }
}
