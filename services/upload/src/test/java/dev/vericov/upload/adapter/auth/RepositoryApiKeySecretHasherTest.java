package dev.vericov.upload.adapter.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryApiKeySecretHasherTest {

    @Test
    void hashesApiKeysWithPepperAndVerifiesUsingConstantTimeComparison() {
        RepositoryApiKeySecretHasher hasher = new RepositoryApiKeySecretHasher("test-pepper");

        String hash = hasher.hash("vc_repo_test_secret");

        assertTrue(hasher.matches("vc_repo_test_secret", hash));
        assertFalse(hasher.matches("vc_repo_wrong_secret", hash));
        assertFalse(hasher.matches("vc_repo_test_secret", new RepositoryApiKeySecretHasher("other-pepper")
                .hash("vc_repo_test_secret")));
    }
}
