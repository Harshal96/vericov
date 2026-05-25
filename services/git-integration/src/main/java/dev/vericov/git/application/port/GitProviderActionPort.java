package dev.vericov.git.application.port;

import dev.vericov.git.application.GitProviderAction;
import dev.vericov.git.application.GitProviderActionResult;

public interface GitProviderActionPort {
    GitProviderActionResult execute(GitProviderAction action);
}
