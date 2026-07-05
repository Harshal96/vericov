package dev.vericov.upload.adapter.auth;

import dev.vericov.upload.application.InvalidUploadException;
import dev.vericov.upload.application.port.TenantAuthenticator;
import dev.vericov.upload.domain.TenantPrincipal;
import jakarta.json.JsonObject;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

public class InternalTokenAuthenticator implements TenantAuthenticator {
    private final DataSource dataSource;
    private final String secret;
    private final String issuer;
    private final String audience;
    private final Clock clock;

    public InternalTokenAuthenticator(
            DataSource dataSource,
            String secret,
            String issuer,
            String audience,
            Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.secret = secret;
        this.issuer = issuer;
        this.audience = audience;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TenantPrincipal authenticateTenant(String authorizationHeader) {
        String token = bearerToken(authorizationHeader);
        JsonObject payload = UploadJwtSupport.verifiedHs256Payload(token, secret, issuer, audience, clock);
        UUID orgId = UploadJwtSupport.uuidClaim(payload, "org_id");
        UUID tenantId = tenantIdFor(orgId);
        return new TenantPrincipal(
                tenantId,
                orgId,
                UploadJwtSupport.stringClaim(payload, "sub"),
                Set.copyOf(UploadJwtSupport.stringArrayClaim(payload, "roles", List.of())),
                Set.copyOf(UploadJwtSupport.stringArrayClaim(payload, "scope", List.of())));
    }

    private UUID tenantIdFor(UUID orgId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select id
                        from vericov.tenants
                        where external_org_id = ?
                        """)) {
            statement.setObject(1, orgId);
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getObject("id", UUID.class);
                }
                throw new InvalidUploadException(
                        "tenant_not_provisioned",
                        "No Vericov tenant is provisioned for this organization");
            }
        } catch (SQLException exception) {
            throw new InvalidUploadException("unauthorized", "Internal token tenant lookup failed");
        }
    }

    private static String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw UploadJwtSupport.unauthorized();
        }
        String token = authorizationHeader.replaceFirst("(?i)^Bearer\\s+", "").trim();
        if (token.isBlank() || token.equals(authorizationHeader)) {
            throw UploadJwtSupport.unauthorized();
        }
        return token;
    }
}
