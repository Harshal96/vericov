package dev.vericov.integrations;

import io.helidon.microprofile.server.Server;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationsSkeletonTest {

    @Test
    void bootsServerWithExpectedPortAndHealthEndpoint() throws Exception {
        Server server = null;
        try {
            server = Server.create().start();

            assertEquals(8082, server.port());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.port() + "/health/live"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300);
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    @Test
    void configuresExpectedServerPortAndBeanDiscovery() throws Exception {
        String applicationConfig = Files.readString(Path.of("src/main/resources/application.yaml"));
        String beansConfig = Files.readString(Path.of("src/main/resources/META-INF/beans.xml"));

        assertTrue(applicationConfig.contains("host: 127.0.0.1"));
        assertTrue(applicationConfig.contains("port: 8082"));
        assertTrue(beansConfig.contains("bean-discovery-mode=\"annotated\""));
        assertTrue(beansConfig.contains("version=\"4.0\""));
    }

    @Test
    void exposesStaticEntrypointAndHidesConstructor() throws Exception {
        Constructor<Main> constructor = Main.class.getDeclaredConstructor();
        Method main = Main.class.getDeclaredMethod("main", String[].class);

        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertTrue(Modifier.isPublic(main.getModifiers()));
        assertTrue(Modifier.isStatic(main.getModifiers()));
    }

    @Test
    void pinsPostgresDriverVersion() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>postgresql</artifactId>"));
        assertTrue(pom.contains("<version>42.7.8</version>"));
    }
}
