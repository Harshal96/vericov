package dev.vericov.agent.api;

import dev.vericov.agent.application.AgentControlPlaneService;
import dev.vericov.agent.application.AgentRunnerException;
import dev.vericov.agent.application.port.InternalServiceAuthorizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;

@ApplicationScoped
@Path("/internal/v1/agents/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InternalAgentTaskResource {
    private final AgentControlPlaneService service;
    private final InternalServiceAuthorizer authorizer;

    @Inject
    public InternalAgentTaskResource(
            AgentControlPlaneService service,
            InternalServiceAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @POST
    public Response createTask(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            CreateAgentTaskHttpRequest request) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            CreateAgentTaskHttpRequest body = requireRequest(request);
            var task = service.createTask(body.toCommand());
            return Response.created(URI.create("/internal/v1/agents/tasks/" + task.id()))
                    .entity(new ApiResponse<>(AgentTaskHttpResponse.from(task)))
                    .build();
        } catch (AgentRunnerException exception) {
            return errorResponse(exception);
        }
    }

    @GET
    @Path("/{task_id}")
    public Response getTask(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @PathParam("task_id") String taskId) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            var task = service.getTask(ApiParsing.parseRequiredUuid(taskId, "task_id"));
            return Response.ok(new ApiResponse<>(AgentTaskHttpResponse.from(task))).build();
        } catch (AgentRunnerException exception) {
            return errorResponse(exception);
        }
    }

    @POST
    @Path("/{task_id}/policy-decision")
    public Response recordPolicyDecision(
            @HeaderParam("X-Vericov-Service-Name") String serviceName,
            @HeaderParam("X-Vericov-Service-Token") String serviceToken,
            @PathParam("task_id") String taskId,
            RecordPolicyDecisionHttpRequest request) {
        try {
            requireAuthorizedService(serviceName, serviceToken);
            RecordPolicyDecisionHttpRequest body = requireRequest(request);
            var task = service.recordPolicyDecision(body.toCommand(taskId));
            return Response.ok(new ApiResponse<>(AgentTaskHttpResponse.from(task))).build();
        } catch (AgentRunnerException exception) {
            return errorResponse(exception);
        }
    }

    private String requireAuthorizedService(String serviceName, String serviceToken) {
        try {
            return authorizer.requireAuthorizedService(serviceName, serviceToken);
        } catch (AgentRunnerException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AgentRunnerException("unauthorized", exception.getMessage());
        }
    }

    private static <T> T requireRequest(T request) {
        if (request == null) {
            throw new AgentRunnerException("validation_error", "request body is required");
        }
        return request;
    }

    static Response errorResponse(AgentRunnerException exception) {
        int status = switch (exception.code()) {
            case "unauthorized" -> 401;
            case "forbidden", "policy_denied" -> 403;
            case "not_found" -> 404;
            case "validation_error" -> 400;
            default -> 500;
        };
        return Response.status(status)
                .entity(ApiError.of(exception.code(), exception.getMessage()))
                .build();
    }
}
