package dev.vericov.git;

import io.helidon.microprofile.server.Server;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Server.create().start();
    }
}
