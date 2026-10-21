package io.github.khalilurrrahmanridoykhan.outbreaksignal.fhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;

/**
 * Serialises any {@link IBaseResource} (DetectedIssue, Bundle,
 * CapabilityStatement, ...) as real FHIR JSON via HAPI's own parser, not
 * Spring's Jackson — Jackson has no idea how a FHIR resource's polymorphic,
 * choice-typed fields are supposed to look on the wire and would otherwise
 * just reflect over the Java object, producing JSON that resembles but does
 * not conform to the FHIR JSON format. {@code IParser} is stateful and
 * cheap, so a new one is requested per read/write rather than shared (see
 * {@link FhirConfig}).
 */
@Component
class FhirJsonHttpMessageConverter extends AbstractHttpMessageConverter<IBaseResource> {

    private final FhirContext fhirContext;

    FhirJsonHttpMessageConverter(FhirContext fhirContext) {
        super(MediaType.valueOf("application/fhir+json"), MediaType.APPLICATION_JSON);
        this.fhirContext = fhirContext;
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return IBaseResource.class.isAssignableFrom(clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected IBaseResource readInternal(Class<? extends IBaseResource> clazz, HttpInputMessage inputMessage) throws IOException {
        IParser parser = fhirContext.newJsonParser();
        try (var reader = new InputStreamReader(inputMessage.getBody(), StandardCharsets.UTF_8)) {
            return parser.parseResource(clazz, reader);
        } catch (ca.uhn.fhir.parser.DataFormatException e) {
            throw new HttpMessageNotReadableException("Could not parse FHIR JSON: " + e.getMessage(), e, inputMessage);
        }
    }

    @Override
    protected void writeInternal(IBaseResource resource, HttpOutputMessage outputMessage) throws IOException {
        IParser parser = fhirContext.newJsonParser();
        try (Writer writer = new OutputStreamWriter(outputMessage.getBody(), StandardCharsets.UTF_8)) {
            parser.encodeResourceToWriter(resource, writer);
        }
    }
}
