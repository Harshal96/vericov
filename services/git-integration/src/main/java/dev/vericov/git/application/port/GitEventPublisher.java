package dev.vericov.git.application.port;

import dev.vericov.git.application.PublishedGitEvent;

public interface GitEventPublisher {
    void publish(PublishedGitEvent event);
}
