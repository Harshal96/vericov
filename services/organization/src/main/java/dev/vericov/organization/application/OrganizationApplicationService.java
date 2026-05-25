package dev.vericov.organization.application;

import dev.vericov.organization.application.port.OrganizationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class OrganizationApplicationService {
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern FULL_REPOSITORY_NAME_PATTERN = Pattern.compile("^[^\\s/]+/[^\\s/]+$");
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");
    private static final int INVITATION_TOKEN_BYTES = 32;
    private static final int BADGE_TOKEN_BYTES = 24;
    private static final String BADGE_TOKEN_PREFIX = "vc_badge_";
    private static final String BADGE_CACHE_SCOPE_TOKEN = "token";
    private static final String BADGE_CACHE_SCOPE_AUTHENTICATED = "authenticated";
    private static final long TOKEN_BADGE_CACHE_TTL_SECONDS = 60;
    private static final long AUTHENTICATED_BADGE_CACHE_TTL_SECONDS = 30;
    private static final long INVITATION_TTL_SECONDS = 7 * 24 * 60 * 60;
    private static final Set<String> PLANS = Set.of("free", "team", "enterprise");
    private static final Set<String> ORGANIZATION_STATUSES = Set.of("active", "suspended", "deleted");
    private static final Set<String> MEMBERSHIP_ROLES = Set.of("owner", "admin", "developer", "viewer", "auditor");
    private static final Set<String> MEMBERSHIP_STATUSES = Set.of("active", "invited", "disabled");
    private static final Set<String> REPOSITORY_PROVIDERS = Set.of("github", "gitlab", "bitbucket");
    private static final Set<String> REPOSITORY_VISIBILITIES = Set.of("public", "private", "internal");
    private static final Set<String> REPOSITORY_STATUSES = Set.of("active", "disabled", "archived");
    private static final Set<String> POLICY_TYPES = Set.of("coverage", "mutation", "agent_review", "waiver");
    private static final Set<String> POLICY_TARGET_TYPES = Set.of("repository", "component", "path");
    private static final Set<String> POLICY_STATUSES = Set.of("active", "disabled");
    private static final Set<String> GATE_TYPES = Set.of(
            "project_coverage",
            "patch_coverage",
            "coverage_drop",
            "component_coverage",
            "mutation_score",
            "agent_review_required");
    private static final Set<String> GATE_METRICS = Set.of("line", "branch", "function", "statement", "mutation", "risk");
    private static final Set<String> BADGE_METRICS = Set.of("line", "branch", "function", "statement");
    private static final Map<String, Object> DEFAULT_BADGE_THRESHOLDS = Map.of(
            "brightgreen", new BigDecimal("90"),
            "green", new BigDecimal("80"),
            "yellow", new BigDecimal("60"));
    private static final Set<String> ADMIN_ROLES = Set.of("owner", "admin");
    private static final Set<String> MEMBER_READ_ACTIONS = Set.of(
            "org.read",
            "org.members.read",
            "repositories.read");
    private static final Set<String> ADMIN_ACTIONS = Set.of(
            "org.update",
            "org.members.invite",
            "org.members.update",
            "org.members.disable",
            "repositories.register",
            "repositories.update",
            "repositories.config.update",
            "repositories.policies.update",
            "repositories.gates.update",
            "org.policy_defaults.update");

    private final OrganizationRepository repository;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public OrganizationApplicationService(OrganizationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = new SecureRandom();
    }

    public List<OrganizationDetails> listOrganizations(UUID requesterUserId) {
        requireUser(requesterUserId);
        return repository.findOrganizationsForUser(requesterUserId);
    }

    public OrganizationDetails createOrganization(CreateOrganizationCommand command) {
        requireUser(command.requesterUserId());
        String name = validateName(command.name());
        String slug = validateSlug(command.slug());
        String plan = validatePlan(command.plan() == null || command.plan().isBlank() ? "free" : command.plan());
        if (repository.slugExists(slug)) {
            throw new OrganizationException("conflict", "Organization slug already exists");
        }

        Instant now = clock.instant();
        UUID tenantId = UUID.randomUUID();
        OrganizationDetails organization = new OrganizationDetails(
                UUID.randomUUID(),
                tenantId,
                name,
                slug,
                plan,
                "active",
                now,
                now);
        MembershipDetails owner = new MembershipDetails(
                UUID.randomUUID(),
                tenantId,
                organization.id(),
                command.requesterUserId(),
                "owner",
                "active",
                now,
                now);
        return repository.createOrganizationWithOwner(organization, owner);
    }

    public OrganizationDetails getOrganization(UUID requesterUserId, UUID organizationId) {
        requireUser(requesterUserId);
        requireId(organizationId, "organization_id is required");
        return repository.findOrganizationForUser(organizationId, requesterUserId)
                .orElseThrow(() -> new OrganizationException("not_found", "Organization not found"));
    }

    public OrganizationDetails updateOrganization(UpdateOrganizationCommand command) {
        requireUser(command.requesterUserId());
        requireId(command.organizationId(), "organization_id is required");
        requireAdmin(command.requesterUserId(), command.organizationId());
        OrganizationDetails current = repository.findById(command.organizationId())
                .orElseThrow(() -> new OrganizationException("not_found", "Organization not found"));

        String nextName = command.name() == null ? current.name() : validateName(command.name());
        String nextSlug = command.slug() == null ? current.slug() : validateSlug(command.slug());
        String nextStatus = command.status() == null ? current.status() : validateOrganizationStatus(command.status());
        if (!nextSlug.equals(current.slug()) && repository.slugExists(nextSlug)) {
            throw new OrganizationException("conflict", "Organization slug already exists");
        }
        return repository.updateOrganization(current.withValues(nextName, nextSlug, nextStatus, clock.instant()));
    }

    public List<MembershipDetails> listMemberships(UUID requesterUserId, UUID organizationId) {
        requireUser(requesterUserId);
        requireActiveMembership(requesterUserId, organizationId);
        return repository.listMemberships(organizationId);
    }

    public MembershipDetails addMembership(CreateMembershipCommand command) {
        requireUser(command.requesterUserId());
        requireId(command.organizationId(), "organization_id is required");
        requireId(command.supabaseUserId(), "supabase_user_id is required");
        MembershipDetails requesterMembership = requireAdmin(command.requesterUserId(), command.organizationId());
        OrganizationDetails organization = repository.findById(command.organizationId())
                .orElseThrow(() -> new OrganizationException("not_found", "Organization not found"));

        String role = validateRole(command.role());
        requireOwnerForOwnerRole(requesterMembership, role);
        String status = validateMembershipStatus(command.status() == null || command.status().isBlank()
                ? "active"
                : command.status());
        Instant now = clock.instant();
        return repository.saveMembership(new MembershipDetails(
                UUID.randomUUID(),
                organization.tenantId(),
                organization.id(),
                command.supabaseUserId(),
                role,
                status,
                now,
                now));
    }

    public MembershipDetails updateMembership(UpdateMembershipCommand command) {
        requireUser(command.requesterUserId());
        requireId(command.organizationId(), "organization_id is required");
        requireId(command.membershipId(), "membership_id is required");
        MembershipDetails requesterMembership = requireAdmin(command.requesterUserId(), command.organizationId());
        MembershipDetails current = repository.findMembershipById(command.organizationId(), command.membershipId())
                .orElseThrow(() -> new OrganizationException("not_found", "Membership not found"));

        String nextRole = command.role() == null ? current.role() : validateRole(command.role());
        String nextStatus = command.status() == null ? current.status() : validateMembershipStatus(command.status());
        requireOwnerForOwnerChange(requesterMembership, current.role(), nextRole);
        if (removesActiveOwner(current, nextRole, nextStatus)
                && !hasAnotherActiveOwner(command.organizationId(), command.membershipId())) {
            throw new OrganizationException("last_owner", "Organization must keep at least one active owner");
        }
        return repository.updateMembership(current.withValues(nextRole, nextStatus, clock.instant()));
    }

    public OrganizationInvitationDetails inviteMember(CreateInvitationCommand command) {
        requireUser(command.requesterUserId());
        requireId(command.organizationId(), "organization_id is required");
        MembershipDetails requesterMembership = requireAdmin(command.requesterUserId(), command.organizationId());
        OrganizationDetails organization = repository.findById(command.organizationId())
                .orElseThrow(() -> new OrganizationException("not_found", "Organization not found"));
        String email = validateEmail(command.email());
        String role = validateRole(command.role());
        requireOwnerForOwnerRole(requesterMembership, role);

        Instant now = clock.instant();
        String token = generateInvitationToken();
        OrganizationInvitation invitation = new OrganizationInvitation(
                UUID.randomUUID(),
                organization.tenantId(),
                organization.id(),
                email,
                role,
                "pending",
                command.requesterUserId(),
                hashToken(token),
                now.plusSeconds(INVITATION_TTL_SECONDS),
                null,
                now,
                now);
        return repository.saveInvitation(invitation).toDetails(token);
    }

    public List<OrganizationInvitationDetails> listInvitations(UUID requesterUserId, UUID organizationId) {
        requireUser(requesterUserId);
        requireAdmin(requesterUserId, organizationId);
        return repository.listInvitations(organizationId).stream()
                .map(invitation -> invitation.toDetails(null))
                .toList();
    }

    public MembershipDetails acceptInvitation(AcceptInvitationCommand command) {
        requireUser(command.acceptingUserId());
        requireId(command.organizationId(), "organization_id is required");
        requireId(command.invitationId(), "invitation_id is required");
        String acceptingEmail = validateEmail(command.acceptingEmail());
        String token = trim(command.acceptanceToken());
        if (token == null) {
            throw new OrganizationException("validation_error", "acceptance_token is required");
        }

        OrganizationInvitation invitation = repository.findInvitationById(command.organizationId(), command.invitationId())
                .orElseThrow(() -> new OrganizationException("not_found", "Invitation not found"));
        if (!"pending".equals(invitation.status())) {
            throw new OrganizationException("conflict", "Invitation is not pending");
        }
        Instant now = clock.instant();
        if (!invitation.expiresAt().isAfter(now)) {
            throw new OrganizationException("conflict", "Invitation has expired");
        }
        if (!invitation.email().equals(acceptingEmail)) {
            throw new OrganizationException("forbidden", "Invitation email does not match authenticated user");
        }
        if (!MessageDigest.isEqual(invitation.acceptanceTokenHash().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                hashToken(token).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new OrganizationException("forbidden", "Invitation token is invalid");
        }

        MembershipDetails membership = repository.saveMembership(new MembershipDetails(
                UUID.randomUUID(),
                invitation.tenantId(),
                invitation.organizationId(),
                command.acceptingUserId(),
                invitation.role(),
                "active",
                now,
                now));
        repository.updateInvitation(invitation.accept(now));
        return membership;
    }

    public AuthorizationDecision checkAuthorization(AuthorizationCheckCommand command) {
        requireUser(command.requesterUserId());
        requireId(command.organizationId(), "organization_id is required");
        String action = trim(command.action());
        if (action == null) {
            throw new OrganizationException("validation_error", "action is required");
        }

        MembershipDetails membership = repository.findMembership(command.organizationId(), command.requesterUserId())
                .orElse(null);
        if (membership == null || !"active".equals(membership.status())) {
            return AuthorizationDecision.deny("not_found", "Organization not found");
        }
        if (MEMBER_READ_ACTIONS.contains(action)) {
            return AuthorizationDecision.allow();
        }
        if (ADMIN_ACTIONS.contains(action)) {
            return ADMIN_ROLES.contains(membership.role())
                    ? AuthorizationDecision.allow()
                    : AuthorizationDecision.deny("forbidden", "Admin or owner role is required");
        }
        if ("org.members.assign_owner".equals(action)) {
            return "owner".equals(membership.role())
                    ? AuthorizationDecision.allow()
                    : AuthorizationDecision.deny("forbidden", "Owner role is required");
        }
        return AuthorizationDecision.deny("unknown_action", "Action is not recognized");
    }

    public List<RepositoryDetails> listRepositories(UUID requesterUserId, UUID organizationId) {
        requireUser(requesterUserId);
        requireActiveMembership(requesterUserId, organizationId);
        requireOrganization(organizationId);
        return repository.listRepositories(organizationId);
    }

    public RepositoryDetails registerRepository(CreateRepositoryCommand command) {
        requireUser(command.requesterUserId());
        requireId(command.organizationId(), "organization_id is required");
        requireAdmin(command.requesterUserId(), command.organizationId());
        OrganizationDetails organization = requireOrganization(command.organizationId());

        String provider = validateProvider(command.provider());
        String providerRepositoryId = validateProviderRepositoryId(command.providerRepositoryId());
        String fullName = validateRepositoryFullName(command.fullName());
        String defaultBranch = validateDefaultBranch(defaultIfBlank(command.defaultBranch(), "main"));
        String visibility = validateVisibility(defaultIfBlank(command.visibility(), "private"));

        repository.findRepositoryByProviderIdentity(organization.id(), provider, providerRepositoryId)
                .ifPresent(existing -> {
                    throw new OrganizationException("conflict", "Repository already exists");
                });

        Instant now = clock.instant();
        return repository.saveRepository(new RepositoryDetails(
                UUID.randomUUID(),
                organization.tenantId(),
                organization.id(),
                provider,
                providerRepositoryId,
                fullName,
                defaultBranch,
                visibility,
                "private_saas",
                "active",
                now,
                now));
    }

    public RepositoryDetails getRepository(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        requireUser(requesterUserId);
        requireId(repositoryId, "repository_id is required");
        requireActiveMembership(requesterUserId, organizationId);
        requireOrganization(organizationId);
        return repository.findRepository(organizationId, repositoryId)
                .orElseThrow(() -> new OrganizationException("not_found", "Repository not found"));
    }

    public RepositoryDetails updateRepository(UpdateRepositoryCommand command) {
        requireUser(command.requesterUserId());
        requireId(command.organizationId(), "organization_id is required");
        requireId(command.repositoryId(), "repository_id is required");
        requireAdmin(command.requesterUserId(), command.organizationId());
        requireOrganization(command.organizationId());
        RepositoryDetails current = repository.findRepository(command.organizationId(), command.repositoryId())
                .orElseThrow(() -> new OrganizationException("not_found", "Repository not found"));

        String nextFullName = command.fullName() == null
                ? current.fullName()
                : validateRepositoryFullName(command.fullName());
        String nextDefaultBranch = command.defaultBranch() == null
                ? current.defaultBranch()
                : validateDefaultBranch(command.defaultBranch());
        String nextVisibility = command.visibility() == null
                ? current.visibility()
                : validateVisibility(command.visibility());
        String nextStatus = command.status() == null
                ? current.status()
                : validateRepositoryStatus(command.status());

        return repository.updateRepository(current.withValues(
                nextFullName,
                nextDefaultBranch,
                nextVisibility,
                nextStatus,
                clock.instant()));
    }

    public PolicyDefaultsDetails getPolicyDefaults(UUID requesterUserId, UUID organizationId) {
        requireUser(requesterUserId);
        OrganizationDetails organization = requireOrganizationForRead(requesterUserId, organizationId);
        Instant now = clock.instant();
        return repository.findPolicyDefaults(organizationId)
                .orElseGet(() -> new PolicyDefaultsDetails(
                        UUID.randomUUID(),
                        organization.tenantId(),
                        organization.id(),
                        Map.of(),
                        1,
                        requesterUserId,
                        now,
                        now));
    }

    public PolicyDefaultsDetails upsertPolicyDefaults(UpsertPolicyDefaultsCommand command) {
        requireUser(command.requesterUserId());
        OrganizationDetails organization = requireOrganizationForAdmin(command.requesterUserId(), command.organizationId());
        Map<String, Object> defaults = validateConfig(command.defaults());
        int schemaVersion = validateSchemaVersion(command.schemaVersion());
        Instant now = clock.instant();
        return repository.findPolicyDefaults(organization.id())
                .map(current -> repository.updatePolicyDefaults(current.withValues(
                        defaults,
                        schemaVersion,
                        command.requesterUserId(),
                        now)))
                .orElseGet(() -> repository.savePolicyDefaults(new PolicyDefaultsDetails(
                        UUID.randomUUID(),
                        organization.tenantId(),
                        organization.id(),
                        defaults,
                        schemaVersion,
                        command.requesterUserId(),
                        now,
                        now)));
    }

    public RepositoryConfigDetails validateRepositoryConfig(ValidateRepositoryConfigCommand command) {
        requireUser(command.requesterUserId());
        RepositoryDetails registeredRepository = requireRepositoryForAdmin(
                command.requesterUserId(),
                command.organizationId(),
                command.repositoryId());
        Map<String, Object> config = validateConfig(command.config());
        int schemaVersion = validateSchemaVersion(command.schemaVersion());
        Instant now = clock.instant();
        return new RepositoryConfigDetails(
                UUID.randomUUID(),
                registeredRepository.tenantId(),
                registeredRepository.organizationId(),
                registeredRepository.id(),
                "ui_override",
                config,
                schemaVersion,
                "valid",
                List.of(),
                command.requesterUserId(),
                now,
                now);
    }

    public RepositoryConfigDetails upsertRepositoryConfig(UpsertRepositoryConfigCommand command) {
        RepositoryConfigDetails validated = validateRepositoryConfig(new ValidateRepositoryConfigCommand(
                command.requesterUserId(),
                command.organizationId(),
                command.repositoryId(),
                command.config(),
                command.schemaVersion()));
        Instant now = clock.instant();
        return repository.findRepositoryConfig(command.organizationId(), command.repositoryId())
                .map(current -> repository.updateRepositoryConfig(current.withValues(
                        validated.config(),
                        validated.schemaVersion(),
                        validated.validationStatus(),
                        validated.validationErrors(),
                        command.requesterUserId(),
                        now)))
                .orElseGet(() -> repository.saveRepositoryConfig(validated));
    }

    public EffectiveRepositoryConfig getEffectiveRepositoryConfig(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId) {
        RepositoryDetails registeredRepository = requireRepositoryForRead(requesterUserId, organizationId, repositoryId);
        Map<String, Object> defaults = repository.findPolicyDefaults(organizationId)
                .map(PolicyDefaultsDetails::defaults)
                .orElse(Map.of());
        Map<String, Object> config = repository.findRepositoryConfig(organizationId, repositoryId)
                .map(RepositoryConfigDetails::config)
                .orElse(Map.of());
        return new EffectiveRepositoryConfig(
                registeredRepository.tenantId(),
                organizationId,
                repositoryId,
                defaults,
                config,
                repository.listRepositoryPolicies(organizationId, repositoryId),
                repository.listRepositoryGates(organizationId, repositoryId),
                clock.instant());
    }

    public List<RepositoryPolicyDetails> listRepositoryPolicies(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId) {
        requireRepositoryForRead(requesterUserId, organizationId, repositoryId);
        return repository.listRepositoryPolicies(organizationId, repositoryId);
    }

    public RepositoryPolicyDetails createRepositoryPolicy(CreateRepositoryPolicyCommand command) {
        RepositoryDetails registeredRepository = requireRepositoryForAdmin(
                command.requesterUserId(),
                command.organizationId(),
                command.repositoryId());
        String name = validatePolicyName(command.name());
        String description = trim(command.description());
        String policyType = validateAllowed("policy_type", command.policyType(), POLICY_TYPES);
        String targetType = validateAllowed("target_type", command.targetType(), POLICY_TARGET_TYPES);
        String targetSelector = validateTargetSelector(targetType, command.targetSelector());
        Map<String, Object> config = validateConfig(command.config());
        String status = validateAllowed("status", defaultIfBlank(command.status(), "active"), POLICY_STATUSES);
        int priority = validatePriority(command.priority());
        Instant now = clock.instant();
        return repository.saveRepositoryPolicy(new RepositoryPolicyDetails(
                UUID.randomUUID(),
                registeredRepository.tenantId(),
                registeredRepository.organizationId(),
                registeredRepository.id(),
                name,
                description,
                policyType,
                targetType,
                targetSelector,
                config,
                status,
                priority,
                command.requesterUserId(),
                now,
                now));
    }

    public RepositoryPolicyDetails updateRepositoryPolicy(UpdateRepositoryPolicyCommand command) {
        requireRepositoryForAdmin(command.requesterUserId(), command.organizationId(), command.repositoryId());
        requireId(command.policyId(), "policy_id is required");
        RepositoryPolicyDetails current = repository.findRepositoryPolicy(
                        command.organizationId(),
                        command.repositoryId(),
                        command.policyId())
                .orElseThrow(() -> new OrganizationException("not_found", "Repository policy not found"));
        String nextName = command.name() == null ? current.name() : validatePolicyName(command.name());
        String nextDescription = command.description() == null ? current.description() : trim(command.description());
        String nextPolicyType = command.policyType() == null
                ? current.policyType()
                : validateAllowed("policy_type", command.policyType(), POLICY_TYPES);
        String nextTargetType = command.targetType() == null
                ? current.targetType()
                : validateAllowed("target_type", command.targetType(), POLICY_TARGET_TYPES);
        String nextTargetSelector = command.targetSelector() == null
                ? current.targetSelector()
                : validateTargetSelector(nextTargetType, command.targetSelector());
        Map<String, Object> nextConfig = command.config() == null ? current.config() : validateConfig(command.config());
        String nextStatus = command.status() == null
                ? current.status()
                : validateAllowed("status", command.status(), POLICY_STATUSES);
        int nextPriority = command.priority() == null ? current.priority() : validatePriority(command.priority());
        return repository.updateRepositoryPolicy(current.withValues(
                nextName,
                nextDescription,
                nextPolicyType,
                nextTargetType,
                nextTargetSelector,
                nextConfig,
                nextStatus,
                nextPriority,
                clock.instant()));
    }

    public List<RepositoryGateDetails> listRepositoryGates(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId) {
        requireRepositoryForRead(requesterUserId, organizationId, repositoryId);
        return repository.listRepositoryGates(organizationId, repositoryId);
    }

    public List<RepositoryGateDetails> validateRepositoryGates(UpsertRepositoryGatesCommand command) {
        RepositoryDetails registeredRepository = requireRepositoryForAdmin(
                command.requesterUserId(),
                command.organizationId(),
                command.repositoryId());
        return command.gates().stream()
                .map(gate -> validateGate(registeredRepository, gate))
                .toList();
    }

    public List<RepositoryGateDetails> replaceRepositoryGates(UpsertRepositoryGatesCommand command) {
        List<RepositoryGateDetails> gates = validateRepositoryGates(command);
        repository.replaceRepositoryGates(command.organizationId(), command.repositoryId(), gates);
        return repository.listRepositoryGates(command.organizationId(), command.repositoryId());
    }

    public RepositoryBadgeSettingsDetails getRepositoryBadgeSettings(
            UUID requesterUserId,
            UUID organizationId,
            UUID repositoryId) {
        RepositoryDetails registeredRepository = requireRepositoryForRead(requesterUserId, organizationId, repositoryId);
        return repository.findRepositoryBadgeSettings(organizationId, repositoryId)
                .orElseGet(() -> defaultBadgeSettings(registeredRepository, requesterUserId, clock.instant()));
    }

    public RepositoryBadgeSettingsDetails upsertRepositoryBadgeSettings(
            UpsertRepositoryBadgeSettingsCommand command) {
        RepositoryDetails registeredRepository = requireRepositoryForAdmin(
                command.requesterUserId(),
                command.organizationId(),
                command.repositoryId());
        boolean enabled = command.enabled() != null && command.enabled();
        String branch = validateDefaultBranch(defaultIfBlank(command.branch(), registeredRepository.defaultBranch()));
        String metric = validateBadgeMetric(defaultIfBlank(command.metric(), "line"));
        String label = validateBadgeLabel(defaultIfBlank(command.label(), "coverage"));
        Map<String, Object> thresholds = validateBadgeThresholds(command.thresholds());
        Instant now = clock.instant();
        RepositoryBadgeSettingsDetails settings = repository.findRepositoryBadgeSettings(command.organizationId(), command.repositoryId())
                .map(current -> repository.updateRepositoryBadgeSettings(current.withValues(
                        enabled,
                        branch,
                        metric,
                        label,
                        thresholds,
                        now)))
                .orElseGet(() -> repository.saveRepositoryBadgeSettings(new RepositoryBadgeSettingsDetails(
                        UUID.randomUUID(),
                        registeredRepository.tenantId(),
                        registeredRepository.organizationId(),
                        registeredRepository.id(),
                        enabled,
                        branch,
                        metric,
                        label,
                        thresholds,
                        null,
                        null,
                        command.requesterUserId(),
                        now,
                        now,
                        null)));
        repository.deleteCoverageBadgeCache(command.organizationId(), command.repositoryId());
        return settings;
    }

    public BadgeTokenDetails rotateRepositoryBadgeToken(RotateRepositoryBadgeTokenCommand command) {
        RepositoryDetails registeredRepository = requireRepositoryForAdmin(
                command.requesterUserId(),
                command.organizationId(),
                command.repositoryId());
        Instant now = clock.instant();
        RepositoryBadgeSettingsDetails settings = repository.findRepositoryBadgeSettings(
                        command.organizationId(),
                        command.repositoryId())
                .orElseGet(() -> repository.saveRepositoryBadgeSettings(defaultBadgeSettings(
                        registeredRepository,
                        command.requesterUserId(),
                        now)));
        String token = generateBadgeToken();
        String tokenPrefix = tokenPrefix(token);
        repository.updateRepositoryBadgeSettings(settings.withToken(hashToken(token), tokenPrefix, now));
        repository.deleteCoverageBadgeCache(command.organizationId(), command.repositoryId());
        return new BadgeTokenDetails(
                command.organizationId(),
                command.repositoryId(),
                token,
                tokenPrefix,
                now);
    }

    public CoverageBadgeDetails getCoverageBadge(GetCoverageBadgeCommand command) {
        requireId(command.organizationId(), "organization_id is required");
        requireId(command.repositoryId(), "repository_id is required");
        String token = trim(command.token());
        RepositoryDetails registeredRepository;
        RepositoryBadgeSettingsDetails settings;
        if (token == null) {
            registeredRepository = requireRepositoryForRead(
                    command.requesterUserId(),
                    command.organizationId(),
                    command.repositoryId());
            settings = repository.findRepositoryBadgeSettings(command.organizationId(), command.repositoryId())
                    .orElseGet(() -> defaultBadgeSettings(
                            registeredRepository,
                            command.requesterUserId(),
                            registeredRepository.updatedAt()));
        } else {
            registeredRepository = repository.findRepository(command.organizationId(), command.repositoryId())
                    .orElseThrow(() -> new OrganizationException("not_found", "Coverage badge not found"));
            settings = repository.findRepositoryBadgeSettings(command.organizationId(), command.repositoryId())
                    .orElseThrow(() -> new OrganizationException("not_found", "Coverage badge not found"));
            requireValidBadgeToken(settings, token);
        }

        String branch = validateDefaultBranch(defaultIfBlank(command.branch(), settings.branch()));
        String metric = validateBadgeMetric(defaultIfBlank(command.metric(), settings.metric()));
        Instant now = clock.instant();
        String cacheScope = token == null ? BADGE_CACHE_SCOPE_AUTHENTICATED : BADGE_CACHE_SCOPE_TOKEN;
        return repository.findFreshCoverageBadgeCache(
                        registeredRepository.organizationId(),
                        registeredRepository.id(),
                        cacheScope,
                        branch,
                        metric,
                        settings.updatedAt(),
                        now)
                .map(CoverageBadgeCacheEntry::toDetails)
                .orElseGet(() -> resolveAndCacheCoverageBadge(
                        registeredRepository,
                        settings,
                        cacheScope,
                        branch,
                        metric,
                        now));
    }

    public CoverageReportDetails getCommitCoverageReport(GetCommitCoverageReportQuery query) {
        RepositoryDetails registeredRepository = requireRepositoryForRead(
                query.requesterUserId(),
                query.organizationId(),
                query.repositoryId());
        String commitSha = validateCommitSha(query.commitSha());
        CoverageReportSummary summary = repository.findCoverageReportByCommit(registeredRepository.id(), commitSha)
                .orElseThrow(() -> new OrganizationException("not_found", "Coverage report not found"));
        return coverageReportDetails(query.organizationId(), summary, query.includeFiles(), query.fileLimit());
    }

    public CoverageLineHitMapDetails getCoverageLineHits(GetCoverageLineHitsQuery query) {
        RepositoryDetails registeredRepository = requireRepositoryForRead(
                query.requesterUserId(),
                query.organizationId(),
                query.repositoryId());
        String commitSha = validateCommitSha(query.commitSha());
        String filePath = validateRepositoryFilePath(query.filePath());
        repository.findCoverageReportByCommit(registeredRepository.id(), commitSha)
                .orElseThrow(() -> new OrganizationException("not_found", "Coverage report not found"));
        return repository.findCoverageLineHits(registeredRepository.id(), commitSha, filePath);
    }

    public PullRequestCoverageReportDetails getPullRequestCoverageReport(GetPullRequestCoverageReportQuery query) {
        RepositoryDetails registeredRepository = requireRepositoryForRead(
                query.requesterUserId(),
                query.organizationId(),
                query.repositoryId());
        if (query.pullRequestNumber() <= 0) {
            throw new OrganizationException("validation_error", "pull_request_number must be positive");
        }
        CoverageReportSummary summary = repository.findLatestPullRequestCoverageReport(
                        registeredRepository.id(),
                        query.pullRequestNumber())
                .orElseThrow(() -> new OrganizationException("not_found", "Pull request coverage report not found"));
        return new PullRequestCoverageReportDetails(
                query.pullRequestNumber(),
                summary.commitSha(),
                coverageReportDetails(query.organizationId(), summary, query.includeFiles(), query.fileLimit()),
                repository.findPullRequestDiffCoverage(summary.id(), query.includeDiffLines()).orElse(null));
    }

    public CoverageTrendDetails listCoverageTrends(ListCoverageTrendsQuery query) {
        RepositoryDetails registeredRepository = requireRepositoryForRead(
                query.requesterUserId(),
                query.organizationId(),
                query.repositoryId());
        String branch = validateDefaultBranch(defaultIfBlank(query.branch(), registeredRepository.defaultBranch()));
        String metric = validateBadgeMetric(defaultIfBlank(query.metric(), "line"));
        int limit = validateReadLimit(query.limit(), 100);
        List<CoverageTrendPointDetails> points = repository.listCoverageReports(registeredRepository.id(), branch, limit)
                .stream()
                .filter(report -> query.from() == null || !report.createdAt().isBefore(query.from()))
                .filter(report -> query.to() == null || !report.createdAt().isAfter(query.to()))
                .map(report -> new CoverageTrendPointDetails(
                        report.id(),
                        report.commitSha(),
                        report.branch(),
                        metric,
                        CoverageMetricDetails.of(report.coveredForMetric(metric), report.totalForMetric(metric)).percent(),
                        report.createdAt()))
                .toList();
        return new CoverageTrendDetails(query.organizationId(), registeredRepository.id(), branch, metric, points);
    }

    public List<GateEvaluationDetails> listGateEvaluations(ListGateEvaluationsQuery query) {
        requireRepositoryForRead(query.requesterUserId(), query.organizationId(), query.repositoryId());
        String branch = query.branch() == null ? null : validateDefaultBranch(query.branch());
        String status = query.status() == null ? null : validateGateEvaluationStatus(query.status());
        return repository.listGateEvaluations(
                query.organizationId(),
                query.repositoryId(),
                branch,
                status,
                validateReadLimit(query.limit(), 100));
    }

    public RepositoryDashboardDetails getRepositoryDashboard(GetRepositoryDashboardQuery query) {
        RepositoryDetails registeredRepository = requireRepositoryForRead(
                query.requesterUserId(),
                query.organizationId(),
                query.repositoryId());
        String branch = validateDefaultBranch(defaultIfBlank(query.branch(), registeredRepository.defaultBranch()));
        CoverageReportSummary latest = repository.findLatestCoverageReport(registeredRepository.id(), branch)
                .orElseThrow(() -> new OrganizationException("not_found", "Coverage report not found"));
        int failingGateCount = failingGateCountForLatestReport(
                query.organizationId(),
                registeredRepository.id(),
                branch,
                latest);
        return new RepositoryDashboardDetails(
                query.organizationId(),
                registeredRepository.id(),
                branch,
                latest.commitSha(),
                CoverageMetricDetails.of(latest.lineCovered(), latest.lineTotal()),
                CoverageMetricDetails.of(latest.branchCovered(), latest.branchTotal()),
                CoverageMetricDetails.of(latest.functionCovered(), latest.functionTotal()),
                CoverageMetricDetails.of(latest.statementCovered(), latest.statementTotal()),
                failingGateCount,
                latest.createdAt());
    }

    public OrganizationDashboardDetails getOrganizationDashboard(GetOrganizationDashboardQuery query) {
        requireOrganizationForRead(query.requesterUserId(), query.organizationId());
        String branch = validateDefaultBranch(defaultIfBlank(query.branch(), "main"));
        List<RepositoryDashboardSummaryDetails> summaries = listRepositoryDashboards(
                new ListRepositoryDashboardsQuery(query.requesterUserId(), query.organizationId(), branch));
        BigDecimal averageLineCoverage = summaries.isEmpty()
                ? BigDecimal.ZERO
                : summaries.stream()
                        .map(RepositoryDashboardSummaryDetails::latestLineCoverage)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(summaries.size()), 1, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
        int failingGateCount = summaries.stream().mapToInt(RepositoryDashboardSummaryDetails::failingGateCount).sum();
        return new OrganizationDashboardDetails(
                query.organizationId(),
                branch,
                summaries.size(),
                averageLineCoverage,
                failingGateCount,
                summaries);
    }

    public List<RepositoryDashboardSummaryDetails> listRepositoryDashboards(ListRepositoryDashboardsQuery query) {
        requireOrganizationForRead(query.requesterUserId(), query.organizationId());
        String requestedBranch = query.branch() == null ? null : validateDefaultBranch(query.branch());
        return repository.listRepositories(query.organizationId()).stream()
                .map(registeredRepository -> repositoryDashboardSummary(query.organizationId(), registeredRepository, requestedBranch))
                .flatMap(Optional::stream)
                .toList();
    }

    private void requireActiveMembership(UUID requesterUserId, UUID organizationId) {
        requireId(organizationId, "organization_id is required");
        MembershipDetails membership = repository.findMembership(organizationId, requesterUserId)
                .orElseThrow(() -> new OrganizationException("not_found", "Organization not found"));
        if (!"active".equals(membership.status())) {
            throw new OrganizationException("forbidden", "Membership is not active");
        }
    }

    private MembershipDetails requireAdmin(UUID requesterUserId, UUID organizationId) {
        requireActiveMembership(requesterUserId, organizationId);
        MembershipDetails membership = repository.findMembership(organizationId, requesterUserId).orElseThrow();
        if (!ADMIN_ROLES.contains(membership.role())) {
            throw new OrganizationException("forbidden", "Admin or owner role is required");
        }
        return membership;
    }

    private OrganizationDetails requireOrganization(UUID organizationId) {
        return repository.findById(organizationId)
                .filter(organization -> !"deleted".equals(organization.status()))
                .orElseThrow(() -> new OrganizationException("not_found", "Organization not found"));
    }

    private OrganizationDetails requireOrganizationForRead(UUID requesterUserId, UUID organizationId) {
        requireUser(requesterUserId);
        requireActiveMembership(requesterUserId, organizationId);
        return requireOrganization(organizationId);
    }

    private OrganizationDetails requireOrganizationForAdmin(UUID requesterUserId, UUID organizationId) {
        requireUser(requesterUserId);
        requireId(organizationId, "organization_id is required");
        requireAdmin(requesterUserId, organizationId);
        return requireOrganization(organizationId);
    }

    private RepositoryDetails requireRepositoryForRead(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        requireUser(requesterUserId);
        requireId(repositoryId, "repository_id is required");
        requireOrganizationForRead(requesterUserId, organizationId);
        return repository.findRepository(organizationId, repositoryId)
                .orElseThrow(() -> new OrganizationException("not_found", "Repository not found"));
    }

    private RepositoryDetails requireRepositoryForAdmin(UUID requesterUserId, UUID organizationId, UUID repositoryId) {
        requireUser(requesterUserId);
        requireId(repositoryId, "repository_id is required");
        requireOrganizationForAdmin(requesterUserId, organizationId);
        return repository.findRepository(organizationId, repositoryId)
                .orElseThrow(() -> new OrganizationException("not_found", "Repository not found"));
    }

    private static void requireOwnerForOwnerRole(MembershipDetails requesterMembership, String role) {
        if ("owner".equals(role) && !"owner".equals(requesterMembership.role())) {
            throw new OrganizationException("forbidden", "Owner role is required");
        }
    }

    private static void requireOwnerForOwnerChange(MembershipDetails requesterMembership, String currentRole, String nextRole) {
        if (("owner".equals(currentRole) || "owner".equals(nextRole)) && !"owner".equals(requesterMembership.role())) {
            throw new OrganizationException("forbidden", "Owner role is required");
        }
    }

    private boolean hasAnotherActiveOwner(UUID organizationId, UUID membershipId) {
        return repository.listMemberships(organizationId).stream()
                .anyMatch(membership -> !membership.id().equals(membershipId)
                        && "owner".equals(membership.role())
                        && "active".equals(membership.status()));
    }

    private static boolean removesActiveOwner(MembershipDetails current, String nextRole, String nextStatus) {
        return "owner".equals(current.role())
                && "active".equals(current.status())
                && (!"owner".equals(nextRole) || !"active".equals(nextStatus));
    }

    private static void requireUser(UUID requesterUserId) {
        requireId(requesterUserId, "authenticated user is required");
    }

    private static void requireId(UUID id, String message) {
        if (id == null) {
            throw new OrganizationException("validation_error", message);
        }
    }

    private static String validateName(String name) {
        String normalized = trim(name);
        if (normalized == null || normalized.length() > 120) {
            throw new OrganizationException("validation_error", "Organization name must be 1 to 120 characters");
        }
        return normalized;
    }

    private static String validateSlug(String slug) {
        String normalized = trim(slug);
        if (normalized == null || !SLUG_PATTERN.matcher(normalized).matches()) {
            throw new OrganizationException(
                    "validation_error",
                    "Organization slug must use lowercase letters, numbers, and hyphens");
        }
        return normalized;
    }

    private static String validatePlan(String plan) {
        return validateAllowed("plan", plan, PLANS);
    }

    private static String validateOrganizationStatus(String status) {
        return validateAllowed("status", status, ORGANIZATION_STATUSES);
    }

    private static String validateRole(String role) {
        return validateAllowed("role", role, MEMBERSHIP_ROLES);
    }

    private static Map<String, Object> validateConfig(Map<String, Object> config) {
        Map<String, Object> copy = ConfigurationValues.deepCopyMap(config);
        validateConfigKeys(copy);
        return copy;
    }

    private static void validateConfigKeys(Map<?, ?> config) {
        for (Map.Entry<?, ?> entry : config.entrySet()) {
            if (!(entry.getKey() instanceof String key)
                    || key.isBlank()
                    || !CONFIG_KEY_PATTERN.matcher(key).matches()) {
                throw new OrganizationException("validation_error", "config key is invalid");
            }
            if (entry.getValue() instanceof Map<?, ?> nestedMap) {
                validateConfigKeys(nestedMap);
            }
        }
    }

    private static int validateSchemaVersion(int schemaVersion) {
        if (schemaVersion < 1) {
            throw new OrganizationException("validation_error", "schema_version must be positive");
        }
        return schemaVersion;
    }

    private static String validatePolicyName(String name) {
        String normalized = trim(name);
        if (normalized == null || normalized.length() > 120) {
            throw new OrganizationException("validation_error", "policy name must be 1 to 120 characters");
        }
        return normalized;
    }

    private static String validateTargetSelector(String targetType, String targetSelector) {
        String normalized = trim(targetSelector);
        if (("component".equals(targetType) || "path".equals(targetType)) && normalized == null) {
            throw new OrganizationException("validation_error", "target_selector is required");
        }
        return normalized;
    }

    private static int validatePriority(int priority) {
        if (priority < 0 || priority > 1000) {
            throw new OrganizationException("validation_error", "priority must be between 0 and 1000");
        }
        return priority;
    }

    private static RepositoryGateDetails validateGate(RepositoryDetails repository, RepositoryGateDetails gate) {
        if (!repository.tenantId().equals(gate.tenantId())
                || !repository.organizationId().equals(gate.organizationId())
                || !repository.id().equals(gate.repositoryId())) {
            throw new OrganizationException("validation_error", "gate repository scope is invalid");
        }
        String name = validatePolicyName(gate.name());
        String gateType = validateAllowed("gate_type", gate.gateType(), GATE_TYPES);
        String metric = validateAllowed("metric", gate.metric(), GATE_METRICS);
        String status = validateAllowed("status", gate.status(), POLICY_STATUSES);
        if (!"agent_review_required".equals(gateType) && gate.threshold() == null) {
            throw new OrganizationException("validation_error", "threshold is required");
        }
        if (gate.threshold() != null && gate.threshold().compareTo(BigDecimal.ZERO) < 0) {
            throw new OrganizationException("validation_error", "threshold must be non-negative");
        }
        if (gate.maxDrop() != null && !"coverage_drop".equals(gateType)) {
            throw new OrganizationException("validation_error", "max_drop is only valid for coverage_drop gates");
        }
        if (gate.maxDrop() != null && gate.maxDrop().compareTo(BigDecimal.ZERO) < 0) {
            throw new OrganizationException("validation_error", "max_drop must be non-negative");
        }
        return new RepositoryGateDetails(
                gate.id(),
                repository.tenantId(),
                repository.organizationId(),
                repository.id(),
                name,
                gateType,
                metric,
                gate.threshold(),
                gate.maxDrop(),
                gate.blocking(),
                validateConfig(gate.config()),
                status,
                gate.createdAt(),
                gate.updatedAt());
    }

    private static String validateBadgeMetric(String metric) {
        return validateAllowed("metric", metric, BADGE_METRICS);
    }

    private static String validateBadgeLabel(String label) {
        String normalized = trim(label);
        if (normalized == null || normalized.length() > 64) {
            throw new OrganizationException("validation_error", "badge label must be 1 to 64 characters");
        }
        return normalized;
    }

    private static Map<String, Object> validateBadgeThresholds(Map<String, Object> thresholds) {
        Map<String, Object> configured = validateConfig(thresholds);
        if (configured.isEmpty()) {
            return DEFAULT_BADGE_THRESHOLDS;
        }
        configured.keySet().forEach(key -> {
            if (!DEFAULT_BADGE_THRESHOLDS.containsKey(key)) {
                throw new OrganizationException("validation_error", "badge threshold key is invalid");
            }
            badgeThreshold(configured, key, BigDecimal.ZERO);
        });
        return Map.of(
                "brightgreen", badgeThreshold(configured, "brightgreen", new BigDecimal("90")),
                "green", badgeThreshold(configured, "green", new BigDecimal("80")),
                "yellow", badgeThreshold(configured, "yellow", new BigDecimal("60")));
    }

    private static BigDecimal badgeThreshold(Map<String, Object> thresholds, String key, BigDecimal fallback) {
        Object value = thresholds.get(key);
        if (value == null) {
            return fallback;
        }
        BigDecimal threshold = switch (value) {
            case BigDecimal decimal -> decimal;
            case Integer integer -> new BigDecimal(integer);
            case Long longValue -> new BigDecimal(longValue);
            case Double doubleValue -> new BigDecimal(doubleValue.toString());
            case Number number -> new BigDecimal(number.toString());
            default -> throw new OrganizationException("validation_error", "badge threshold must be numeric");
        };
        if (threshold.compareTo(BigDecimal.ZERO) < 0 || threshold.compareTo(new BigDecimal("100")) > 0) {
            throw new OrganizationException("validation_error", "badge threshold must be between 0 and 100");
        }
        return threshold;
    }

    private RepositoryBadgeSettingsDetails defaultBadgeSettings(
            RepositoryDetails repository,
            UUID createdByUserId,
            Instant now) {
        return new RepositoryBadgeSettingsDetails(
                UUID.randomUUID(),
                repository.tenantId(),
                repository.organizationId(),
                repository.id(),
                false,
                repository.defaultBranch(),
                "line",
                "coverage",
                DEFAULT_BADGE_THRESHOLDS,
                null,
                null,
                createdByUserId == null ? UUID.randomUUID() : createdByUserId,
                now,
                now,
                null);
    }

    private static void requireValidBadgeToken(RepositoryBadgeSettingsDetails settings, String token) {
        if (!settings.enabled()
                || settings.revokedAt() != null
                || settings.tokenHash() == null
                || settings.tokenPrefix() == null
                || !settings.tokenPrefix().equals(tokenPrefix(token))
                || !MessageDigest.isEqual(
                        settings.tokenHash().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        hashToken(token).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new OrganizationException("not_found", "Coverage badge not found");
        }
    }

    private CoverageBadgeDetails resolveAndCacheCoverageBadge(
            RepositoryDetails repositoryDetails,
            RepositoryBadgeSettingsDetails settings,
            String cacheScope,
            String branch,
            String metric,
            Instant now) {
        Optional<CoverageReportSummary> report = repository.findLatestCoverageReport(repositoryDetails.id(), branch);
        CoverageBadgeDetails badge = report
                .map(summary -> coverageBadgeFromReport(settings, summary, branch, metric, now))
                .orElseGet(() -> new CoverageBadgeDetails(
                        repositoryDetails.organizationId(),
                        repositoryDetails.id(),
                        settings.label(),
                        "unknown",
                        "lightgrey",
                        metric,
                        branch,
                        null,
                        null,
                        null,
                        now));
        repository.upsertCoverageBadgeCache(new CoverageBadgeCacheEntry(
                UUID.randomUUID(),
                repositoryDetails.tenantId(),
                repositoryDetails.organizationId(),
                repositoryDetails.id(),
                cacheScope,
                branch,
                metric,
                badge.label(),
                badge.message(),
                badge.color(),
                badge.commitSha(),
                badge.coveragePercent(),
                report.map(CoverageReportSummary::id).orElse(null),
                badge.reportCreatedAt(),
                settings.updatedAt(),
                now,
                now.plusSeconds(badgeCacheTtlSeconds(cacheScope))));
        return badge;
    }

    private static long badgeCacheTtlSeconds(String cacheScope) {
        return BADGE_CACHE_SCOPE_TOKEN.equals(cacheScope)
                ? TOKEN_BADGE_CACHE_TTL_SECONDS
                : AUTHENTICATED_BADGE_CACHE_TTL_SECONDS;
    }

    private CoverageBadgeDetails coverageBadgeFromReport(
            RepositoryBadgeSettingsDetails settings,
            CoverageReportSummary report,
            String branch,
            String metric,
            Instant resolvedAt) {
        int covered = report.coveredForMetric(metric);
        int total = report.totalForMetric(metric);
        BigDecimal percent = total == 0
                ? BigDecimal.ZERO
                : new BigDecimal(covered)
                        .multiply(new BigDecimal("100"))
                        .divide(new BigDecimal(total), 1, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
        return new CoverageBadgeDetails(
                settings.organizationId(),
                settings.repositoryId(),
                settings.label(),
                percent.toPlainString() + "%",
                badgeColor(settings.thresholds(), percent),
                metric,
                branch,
                report.commitSha(),
                percent,
                report.createdAt(),
                resolvedAt);
    }

    private static String badgeColor(Map<String, Object> thresholds, BigDecimal percent) {
        if (percent.compareTo(badgeThreshold(thresholds, "brightgreen", new BigDecimal("90"))) >= 0) {
            return "brightgreen";
        }
        if (percent.compareTo(badgeThreshold(thresholds, "green", new BigDecimal("80"))) >= 0) {
            return "green";
        }
        if (percent.compareTo(badgeThreshold(thresholds, "yellow", new BigDecimal("60"))) >= 0) {
            return "yellow";
        }
        return "red";
    }

    private CoverageReportDetails coverageReportDetails(
            UUID organizationId,
            CoverageReportSummary summary,
            boolean includeFiles,
            int fileLimit) {
        List<CoverageFileSummaryDetails> files = includeFiles
                ? repository.listCoverageFileSummaries(summary.id(), validateReadLimit(fileLimit, 100))
                : List.of();
        return CoverageReportDetails.from(organizationId, summary, files);
    }

    private Optional<RepositoryDashboardSummaryDetails> repositoryDashboardSummary(
            UUID organizationId,
            RepositoryDetails registeredRepository,
            String requestedBranch) {
        String branch = validateDefaultBranch(defaultIfBlank(requestedBranch, registeredRepository.defaultBranch()));
        return repository.findLatestCoverageReport(registeredRepository.id(), branch)
                .map(latest -> {
                    int failingGateCount = failingGateCountForLatestReport(
                            organizationId,
                            registeredRepository.id(),
                            branch,
                            latest);
                    return new RepositoryDashboardSummaryDetails(
                            registeredRepository.id(),
                            registeredRepository.fullName(),
                            branch,
                            latest.commitSha(),
                            CoverageMetricDetails.of(latest.lineCovered(), latest.lineTotal()).percent(),
                            failingGateCount,
                            latest.createdAt());
                });
    }

    private int failingGateCountForLatestReport(
            UUID organizationId,
            UUID repositoryId,
            String branch,
            CoverageReportSummary latest) {
        return (int) repository.listGateEvaluations(organizationId, repositoryId, branch, "failed", 100).stream()
                .filter(evaluation -> latest.id().equals(evaluation.coverageReportId())
                        || latest.commitSha().equals(evaluation.commitSha()))
                .count();
    }

    private static String validateProvider(String provider) {
        return validateAllowed("provider", provider, REPOSITORY_PROVIDERS);
    }

    private static String validateVisibility(String visibility) {
        return validateAllowed("visibility", visibility, REPOSITORY_VISIBILITIES);
    }

    private static String validateRepositoryStatus(String status) {
        return validateAllowed("status", status, REPOSITORY_STATUSES);
    }

    private static String validateProviderRepositoryId(String providerRepositoryId) {
        String normalized = trim(providerRepositoryId);
        if (normalized == null || normalized.length() > 120) {
            throw new OrganizationException(
                    "validation_error",
                    "provider_repository_id must be 1 to 120 characters");
        }
        return normalized;
    }

    private static String validateRepositoryFullName(String fullName) {
        String normalized = trim(fullName);
        if (normalized == null
                || normalized.length() > 300
                || !FULL_REPOSITORY_NAME_PATTERN.matcher(normalized).matches()) {
            throw new OrganizationException(
                    "validation_error",
                    "full_name must be a provider repository name such as owner/name");
        }
        return normalized;
    }

    private static String validateDefaultBranch(String defaultBranch) {
        String normalized = trim(defaultBranch);
        if (normalized == null
                || normalized.length() > 255
                || normalized.contains("..")
                || normalized.contains("@{")
                || normalized.startsWith("/")
                || normalized.endsWith("/")
                || normalized.indexOf('\\') >= 0
                || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new OrganizationException("validation_error", "default_branch is invalid");
        }
        return normalized;
    }

    private static String validateEmail(String email) {
        String normalized = trim(email);
        if (normalized == null) {
            throw new OrganizationException("validation_error", "email is required");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new OrganizationException("validation_error", "email is invalid");
        }
        return normalized;
    }

    private static String validateCommitSha(String commitSha) {
        String normalized = trim(commitSha);
        if (normalized == null || normalized.length() > 128 || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new OrganizationException("validation_error", "commit_sha is invalid");
        }
        return normalized;
    }

    private static String validateRepositoryFilePath(String filePath) {
        String normalized = trim(filePath);
        if (normalized == null
                || normalized.length() > 1024
                || normalized.startsWith("/")
                || normalized.indexOf('\\') >= 0
                || normalized.indexOf('\0') >= 0
                || normalized.equals("..")
                || normalized.startsWith("../")
                || normalized.endsWith("/..")
                || normalized.contains("/../")) {
            throw new OrganizationException("validation_error", "file_path is invalid");
        }
        return normalized;
    }

    private static String validateGateEvaluationStatus(String status) {
        return validateAllowed("status", status, Set.of("passed", "failed", "warning"));
    }

    private static int validateReadLimit(int limit, int fallback) {
        int normalized = limit <= 0 ? fallback : limit;
        if (normalized > 500) {
            throw new OrganizationException("validation_error", "limit must be at most 500");
        }
        return normalized;
    }

    private static String validateMembershipStatus(String status) {
        return validateAllowed("status", status, MEMBERSHIP_STATUSES);
    }

    private static String validateAllowed(String field, String value, Set<String> allowedValues) {
        String normalized = trim(value);
        if (normalized == null) {
            throw new OrganizationException("validation_error", field + " is required");
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new OrganizationException("validation_error", field + " is invalid");
        }
        return normalized;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String defaultIfBlank(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String generateInvitationToken() {
        byte[] tokenBytes = new byte[INVITATION_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String generateBadgeToken() {
        byte[] tokenBytes = new byte[BADGE_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return BADGE_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private static String tokenPrefix(String token) {
        return token.substring(0, Math.min(18, token.length()));
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
