package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One {@link FhirContext} for the whole application: building it scans and
 * caches the R4 model classes, which is expensive enough that HAPI's own
 * docs call for exactly one long-lived instance, never one per request.
 * {@code IParser} instances (from {@code newJsonParser()}) are cheap and
 * stateful, so those are created per read/write instead — see
 * {@link FhirJsonHttpMessageConverter}.
 *
 * <p>Public: {@code @WebMvcTest} auto-detects every {@code HttpMessageConverter}
 * bean application-wide regardless of which controller is under test (the
 * same way it always auto-detects every {@code WebMvcConfigurer}), so
 * {@link FhirJsonHttpMessageConverter} needs a {@link FhirContext} even in a
 * slice test for an unrelated controller — every such test in this project
 * imports this class explicitly for that reason, the same way each already
 * imports {@code SecurityConfig} explicitly rather than relying on it being
 * picked up implicitly.
 */
@Configuration(proxyBeanMethods = false)
public class FhirConfig {

    @Bean
    FhirContext fhirContext() {
        return FhirContext.forR4();
    }
}
