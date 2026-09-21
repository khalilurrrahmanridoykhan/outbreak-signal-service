package io.github.khalilurrrahmanridoykhan.outbreaksignal.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class OutbreakPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void usesDefaultsWhenNothingIsConfigured() {
        runner.run(context -> assertThat(context.getBean(OutbreakProperties.class).sync().windowWeeks())
                .isEqualTo(104));
    }

    @Test
    void bindsConfiguredValues() {
        runner.withPropertyValues("outbreak.sync.window-weeks=52")
                .run(context -> assertThat(context.getBean(OutbreakProperties.class).sync().windowWeeks())
                        .isEqualTo(52));
    }

    @Test
    void refusesToStartWhenTheWindowIsTooShort() {
        runner.withPropertyValues("outbreak.sync.window-weeks=3")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesToStartWhenTheWindowIsTooLong() {
        runner.withPropertyValues("outbreak.sync.window-weeks=9999")
                .run(context -> assertThat(context).hasFailed());
    }
}
