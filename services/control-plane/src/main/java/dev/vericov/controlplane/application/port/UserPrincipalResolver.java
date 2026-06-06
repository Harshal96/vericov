package dev.vericov.controlplane.application.port;

import dev.vericov.controlplane.domain.AuthenticatedUser;
import dev.vericov.controlplane.domain.UserAuthContext;

public interface UserPrincipalResolver {
    AuthenticatedUser resolve(UserAuthContext context);
}
