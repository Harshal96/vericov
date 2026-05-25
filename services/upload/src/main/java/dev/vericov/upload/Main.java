package dev.vericov.upload;

import io.helidon.microprofile.server.Server;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Server.create().start();
    }
}
