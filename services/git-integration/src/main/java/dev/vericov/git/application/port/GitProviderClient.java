package dev.vericov.git.application.port;

import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;

public interface GitProviderClient {
    GitProviderActionResult execute(GitProviderAction action);
}
