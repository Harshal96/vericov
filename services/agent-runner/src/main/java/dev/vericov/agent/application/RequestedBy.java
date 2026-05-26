package dev.vericov.agent.application;

public record RequestedBy(String type, String id) {
    public RequestedBy {
        type = AgentValues.requireRequesterType(type);
        id = AgentValues.requireTrimmed(id, "requested_by.id is required");
    }

    public static RequestedBy system(String id) {
        return new RequestedBy("system", id);
    }

    public static RequestedBy user(String id) {
        return new RequestedBy("user", id);
    }
}
