package dev.vericov.controlplane.adapter.auth;

import dev.vericov.controlplane.application.OrganizationException;
import dev.vericov.controlplane.application.port.UserPrincipalResolver;
import dev.vericov.controlplane.domain.AuthenticatedUser;
import dev.vericov.controlplane.domain.UserAuthContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.json.JsonString;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;

public final class ServiceJwtVerifier implements UserPrincipalResolver {
    private static final Duration MAX_TOKEN_LIFETIME = Duration.ofMinutes(5);
    private static final long CLOCK_SKEW_SECONDS = 30;

    private final PublicKey publicKey;
    private final SecretKey secretKey;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public ServiceJwtVerifier(
            String publicKeyPem,
            String hmacSecret,
            String issuer,
            String audience,
            Clock clock) {
        this.publicKey = blank(publicKeyPem) ? null : parsePublicKey(publicKeyPem);
        this.secretKey = this.publicKey == null && !blank(hmacSecret)
                ? Keys.hmacShaKeyFor(hmacSecret.getBytes(StandardCharsets.UTF_8))
                : null;
        if (this.publicKey == null && this.secretKey == null) {
            throw new IllegalArgumentException("A service JWT public key or secret is required");
        }
        this.issuer = blank(issuer) ? null : issuer.trim();
        this.audience = blank(audience) ? "vericov" : audience.trim();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuthenticatedUser resolve(UserAuthContext context) {
        ServiceJwtPrincipal principal = verify(context.authorizationHeader());
        UUID userId = principal.userId();
        if (userId == null) {
            throw unauthorized();
        }
        return new AuthenticatedUser(userId);
    }

    public ServiceJwtPrincipal verify(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        try {
            var builder = Jwts.parser()
                    .requireAudience(audience)
                    .clock(() -> Date.from(clock.instant()))
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS);
            if (issuer != null) {
                builder.requireIssuer(issuer);
            }
            if (publicKey != null) {
                builder.verifyWith(publicKey);
            } else {
                builder.verifyWith(secretKey);
            }

            Claims claims = builder.build().parseSignedClaims(token).getPayload();
            validateTokenLifetime(claims);
            return principalFrom(claims);
        } catch (JwtException | IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private ServiceJwtPrincipal principalFrom(Claims claims) {
        String subject = claims.getSubject();
        String userIdClaim = stringClaim(claims, "vericov_user_id");
        UUID userId = uuidOrNull(firstPresent(userIdClaim, stripSubjectPrefix(subject, "user:")));
        UUID tenantId = uuidOrNull(stringClaim(claims, "vericov_tenant_id"));
        return new ServiceJwtPrincipal(
                subject,
                userId,
                tenantId,
                scopesClaim(claims));
    }

    private void validateTokenLifetime(Claims claims) {
        Date issuedAt = claims.getIssuedAt();
        Date expiresAt = claims.getExpiration();
        if (issuedAt == null || expiresAt == null) {
            throw unauthorized();
        }
        Instant issued = issuedAt.toInstant();
        Instant expires = expiresAt.toInstant();
        if (expires.isAfter(issued.plus(MAX_TOKEN_LIFETIME))) {
            throw unauthorized();
        }
    }

    private static Set<String> scopesClaim(Claims claims) {
        Object value = claims.get("vericov_scopes");
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(ServiceJwtVerifier::scopeString)
                    .filter(scope -> scope != null && !scope.isBlank())
                    .collect(Collectors.toUnmodifiableSet());
        }
        return Set.of();
    }

    private static String scopeString(Object value) {
        if (value instanceof String string) {
            return string.trim();
        }
        if (value instanceof JsonString jsonString) {
            return jsonString.getString().trim();
        }
        return null;
    }

    private static String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value instanceof String string && !string.isBlank() ? string.trim() : null;
    }

    private static UUID uuidOrNull(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw unauthorized();
        }
    }

    private static String stripSubjectPrefix(String subject, String prefix) {
        if (subject == null || !subject.startsWith(prefix)) {
            return null;
        }
        return subject.substring(prefix.length());
    }

    private static String firstPresent(String first, String second) {
        if (!blank(first)) {
            return first.trim();
        }
        if (!blank(second)) {
            return second.trim();
        }
        return null;
    }

    private static String bearerToken(String authorizationHeader) {
        if (blank(authorizationHeader)) {
            throw unauthorized();
        }
        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            throw unauthorized();
        }
        String token = trimmed.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw unauthorized();
        }
        return token;
    }

    private static PublicKey parsePublicKey(String publicKeyPem) {
        try {
            String normalized = publicKeyPem
                    .replace("\\n", "\n")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(normalized);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
            if (!(publicKey instanceof RSAPublicKey)) {
                throw new IllegalArgumentException("Only RSA public keys are supported");
            }
            return publicKey;
        } catch (Exception exception) {
            throw new IllegalArgumentException("VERICOV_SERVICE_JWT_PUBLIC_KEY is invalid", exception);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static OrganizationException unauthorized() {
        return new OrganizationException("unauthorized", "Service JWT authentication is required");
    }

    public record ServiceJwtPrincipal(
            String subject,
            UUID userId,
            UUID tenantId,
            Set<String> scopes) {
        public ServiceJwtPrincipal {
            scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
        }
    }
}
