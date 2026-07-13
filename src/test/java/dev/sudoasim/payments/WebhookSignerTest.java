package dev.sudoasim.payments;

import dev.sudoasim.payments.webhook.WebhookSigner;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {
    @Test
    void producesDeterministicSha256Signature() {
        var signer = new WebhookSigner("test-secret");
        assertThat(signer.sign("{\"event\":\"transfer.completed\"}"))
                .isEqualTo("sha256=b9ff19ce62824534dfcd931bc938685a98c3604302b4dc7659b202055cd411e5");
    }
}

