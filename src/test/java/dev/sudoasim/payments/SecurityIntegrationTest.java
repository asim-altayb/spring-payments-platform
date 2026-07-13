package dev.sudoasim.payments;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.sudoasim.payments.account.Account;
import dev.sudoasim.payments.account.AccountRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "payments.outbox-poll-ms=3600000")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class SecurityIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accounts;
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    @Value("${spring.security.oauth2.resourceserver.jwt.secret-key}")
    String jwtSecret;

    UUID source;
    UUID destination;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery(
                        "TRUNCATE TABLE outbox_events, ledger_entries, transfers, accounts CASCADE")
                        .executeUpdate());

        source = UUID.randomUUID();
        destination = UUID.randomUUID();
        accounts.save(new Account(source, "sec-source-" + source, "SDG", new BigDecimal("200.0000")));
        accounts.save(new Account(destination, "sec-dest-" + destination, "SDG", BigDecimal.ZERO));
    }

    @Test
    void transferEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "unauth-1")
                        .content(transferBody(source, destination, "10.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void transferEndpointRequiresTransfersWriteScope() throws Exception {
        String token = mintToken(List.of("accounts:read"));

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "wrong-scope-1")
                        .content(transferBody(source, destination, "10.00")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accountReadRequiresAccountsReadScope() throws Exception {
        String writeOnly = mintToken(List.of("accounts:write"));

        mockMvc.perform(get("/api/v1/accounts/" + source)
                        .header("Authorization", "Bearer " + writeOnly))
                .andExpect(status().isForbidden());

        String reader = mintToken(List.of("accounts:read"));
        mockMvc.perform(get("/api/v1/accounts/" + source)
                        .header("Authorization", "Bearer " + reader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("SDG"));
    }

    @Test
    void transferSucceedsWithTransfersWriteScope() throws Exception {
        String token = mintToken(List.of("transfers:write"));

        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "secured-transfer-1")
                        .header("X-Client-Id", "security-suite")
                        .content(transferBody(source, destination, "15.50")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(15.50));
    }

    @Test
    void openapiIsPublic() throws Exception {
        mockMvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
    }

    private String mintToken(List<String> scopes) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("security-test")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .claim("scope", String.join(" ", scopes))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(jwtSecret.getBytes()));
        return jwt.serialize();
    }

    private static String transferBody(UUID sourceId, UUID destinationId, String amount) {
        return """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": %s,
                  "currency": "SDG"
                }
                """.formatted(sourceId, destinationId, amount);
    }
}
