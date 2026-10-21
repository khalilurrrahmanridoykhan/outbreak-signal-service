package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link FhirJsonHttpMessageConverter} ahead of the default
 * Jackson-based converter. Jackson's own converter is willing to serialise
 * any POJO by reflection, so without this ordering it could "support" a
 * FHIR resource too and win for a plain {@code application/json} request —
 * producing JSON that merely resembles FHIR's shape rather than HAPI's
 * actual FHIR JSON encoding. Every other controller's response types are
 * never {@code IBaseResource}, so {@link FhirJsonHttpMessageConverter#supports}
 * correctly defers to Jackson for all of them regardless of this ordering.
 */
@Configuration(proxyBeanMethods = false)
class FhirWebMvcConfig implements WebMvcConfigurer {

    private final FhirJsonHttpMessageConverter fhirJsonHttpMessageConverter;

    FhirWebMvcConfig(FhirJsonHttpMessageConverter fhirJsonHttpMessageConverter) {
        this.fhirJsonHttpMessageConverter = fhirJsonHttpMessageConverter;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, fhirJsonHttpMessageConverter);
    }
}
