package dev.sudoasim.payments;

import dev.sudoasim.payments.webhook.WebhookSigner;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {
    @Test
    void producesDeterministicSha256Signature() {
        var signer = new WebhookSigner("test-secret");
        assertThat(signer.sign("{\"event\":\"transfer.completed\"}"))
                .isEqualTo("sha256=a06502578465966b56922f13145256e1246e47db0cd6ebe7dc796db6f994714f");
    }
}
