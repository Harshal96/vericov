package dev.vericov.organization.application.port;

import dev.vericov.organization.domain.AuthenticatedUser;
import dev.vericov.organization.domain.UserAuthContext;

public interface UserPrincipalResolver {
    AuthenticatedUser resolve(UserAuthContext context);
}
