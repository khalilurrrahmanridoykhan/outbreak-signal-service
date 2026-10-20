package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void verifyAcceptsItsOwnSignature() {
        Instant timestamp = Instant.parse("2026-10-19T03:00:00Z");
        String body = "{\"eventId\":1,\"eventType\":\"OPEN\"}";

        String signature = signer.sign("a-shared-secret", timestamp, body);

        assertThat(signer.verify("a-shared-secret", timestamp, body, signature)).isTrue();
    }

    @Test
    void verifyRejectsATamperedBody() {
        Instant timestamp = Instant.parse("2026-10-19T03:00:00Z");
        String signature = signer.sign("a-shared-secret", timestamp, "{\"eventId\":1}");

        assertThat(signer.verify("a-shared-secret", timestamp, "{\"eventId\":2}", signature)).isFalse();
    }

    @Test
    void verifyRejectsAReplayedSignatureUnderADifferentTimestamp() {
        String body = "{\"eventId\":1}";
        String signature = signer.sign("a-shared-secret", Instant.parse("2026-10-19T03:00:00Z"), body);

        assertThat(signer.verify("a-shared-secret", Instant.parse("2026-10-19T04:00:00Z"), body, signature)).isFalse();
    }

    @Test
    void verifyRejectsTheWrongSecret() {
        Instant timestamp = Instant.parse("2026-10-19T03:00:00Z");
        String body = "{\"eventId\":1}";
        String signature = signer.sign("a-shared-secret", timestamp, body);

        assertThat(signer.verify("a-different-secret", timestamp, body, signature)).isFalse();
    }

    @Test
    void signingTheSameInputsTwiceIsDeterministic() {
        Instant timestamp = Instant.parse("2026-10-19T03:00:00Z");
        String body = "{\"eventId\":1}";

        assertThat(signer.sign("secret", timestamp, body)).isEqualTo(signer.sign("secret", timestamp, body));
    }
}
