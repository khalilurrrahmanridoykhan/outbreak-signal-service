package io.github.khalilurrrahmanridoykhan.outbreaksignal.notification;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Signs (and verifies) a webhook body the same way Stripe's webhooks do: the
 * HMAC-SHA256 is computed over {@code "<unix-seconds>.<raw body>"}, not the
 * body alone, so a receiver that also checks the timestamp against its own
 * clock can reject a replayed request even if the original signature is
 * still mathematically valid — the plan's "a timestamp to allow replay
 * rejection" requirement.
 */
@Component
public class WebhookSigner {

    private static final String ALGORITHM = "HmacSHA256";

    /** @return the hex-encoded HMAC of {@code timestamp + "." + body}, keyed by {@code secret}. */
    public String sign(String secret, Instant timestamp, String body) {
        String signedContent = timestamp.getEpochSecond() + "." + body;
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 is a required algorithm on every JDK and the key is
            // never empty (secrets are generated, never user-supplied blank
            // strings) — this can only fail as a programming error.
            throw new IllegalStateException("Could not compute a webhook signature", e);
        }
    }

    /** @return whether {@code signature} is the correct HMAC for {@code timestamp + "." + body} under {@code secret}. */
    public boolean verify(String secret, Instant timestamp, String body, String signature) {
        String expected = sign(secret, timestamp, body);
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }
}
