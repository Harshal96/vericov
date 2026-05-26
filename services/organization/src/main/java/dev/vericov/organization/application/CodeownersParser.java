package dev.vericov.organization.application;

import java.util.ArrayList;
import java.util.List;

public final class CodeownersParser {
    private CodeownersParser() {
    }

    public static List<ParsedCodeownersRule> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<ParsedCodeownersRule> rules = new ArrayList<>();
        int providerOrder = 0;
        for (String rawLine : text.lines().toList()) {
            String line = stripComment(rawLine).trim();
            if (line.isBlank()) {
                continue;
            }
            List<String> tokens = splitTokens(line);
            if (tokens.size() < 2) {
                continue;
            }
            String pattern = normalizePattern(tokens.getFirst());
            List<String> owners = tokens.subList(1, tokens.size()).stream()
                    .filter(owner -> !owner.isBlank())
                    .toList();
            if (pattern != null && !owners.isEmpty()) {
                rules.add(new ParsedCodeownersRule(pattern, owners, providerOrder++));
            }
        }
        return List.copyOf(rules);
    }

    private static String stripComment(String line) {
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '#' && !escaped) {
                break;
            }
            value.append(current);
            escaped = current == '\\' && !escaped;
            if (current != '\\') {
                escaped = false;
            }
        }
        return value.toString();
    }

    private static List<String> splitTokens(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (escaped) {
                token.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (Character.isWhitespace(current)) {
                if (!token.isEmpty()) {
                    tokens.add(token.toString());
                    token.setLength(0);
                }
            } else {
                token.append(current);
            }
        }
        if (!token.isEmpty()) {
            tokens.add(token.toString());
        }
        return List.copyOf(tokens);
    }

    private static String normalizePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return null;
        }
        String normalized = pattern.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized += "**";
        }
        return normalized.isBlank() ? null : normalized;
    }
}
