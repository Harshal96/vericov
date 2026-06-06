package dev.vericov.controlplane.adapter.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.vericov.controlplane.application.OrganizationException;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ServiceJwtVerifierTest {
    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void acceptsValidRs256ServiceJwt() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        ServiceJwtVerifier verifier = verifierFor(keyPair.getPublic());

        var principal = verifier.verify("Bearer " + token(keyPair, "vericov", NOW.plusSeconds(300)));

        assertEquals(USER_ID, principal.userId());
        assertEquals(TENANT_ID, principal.tenantId());
        assertEquals("user:" + USER_ID, principal.subject());
        assertEquals(java.util.Set.of("repos:read", "reports:write"), principal.scopes());
    }

    @Test
    void rejectsExpiredServiceJwt() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        ServiceJwtVerifier verifier = verifierFor(keyPair.getPublic());

        String token = token(keyPair, "vericov", NOW.minusSeconds(31));

        assertThrows(OrganizationException.class, () -> verifier.verify("Bearer " + token));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        ServiceJwtVerifier verifier = verifierFor(keyPair.getPublic());

        String token = token(keyPair, "not-vericov", NOW.plusSeconds(300));

        assertThrows(OrganizationException.class, () -> verifier.verify("Bearer " + token));
    }

    @Test
    void rejectsBadSignature() throws Exception {
        KeyPair verifierKeyPair = rsaKeyPair();
        KeyPair signingKeyPair = rsaKeyPair();
        ServiceJwtVerifier verifier = verifierFor(verifierKeyPair.getPublic());

        String token = token(signingKeyPair, "vericov", NOW.plusSeconds(300));

        assertThrows(OrganizationException.class, () -> verifier.verify("Bearer " + token));
    }

    private static ServiceJwtVerifier verifierFor(PublicKey publicKey) {
        return new ServiceJwtVerifier(
                pem(publicKey),
                "",
                "veriapi",
                "vericov",
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String token(KeyPair keyPair, String audience, Instant expiresAt) {
        return Jwts.builder()
                .issuer("veriapi")
                .audience().add(audience).and()
                .subject("user:" + USER_ID)
                .issuedAt(Date.from(NOW))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim("vericov_tenant_id", TENANT_ID.toString())
                .claim("vericov_user_id", USER_ID.toString())
                .claim("vericov_scopes", List.of("repos:read", "reports:write"))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(PublicKey publicKey) {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}
