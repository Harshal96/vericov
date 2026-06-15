package dev.vericov.ignore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class CoverageIgnoreRules {
    private static final String ALLOWED_ESCAPES = "!*?[]\\# /";
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern URI = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*://.*");

    private final List<String> rules;
    private final List<CompiledRule> compiledRules;

    public CoverageIgnoreRules(List<String> rules) {
        this.rules = List.copyOf(rules == null ? List.of() : rules);
        List<CompiledRule> compiled = new ArrayList<>(this.rules.size());
        for (int index = 0; index < this.rules.size(); index++) {
            compiled.add(compileRule(this.rules.get(index), index));
        }
        this.compiledRules = List.copyOf(compiled);
    }

    public List<String> rules() {
        return rules;
    }

    public boolean isIgnored(String path) {
        String normalized = normalizeRepositoryPath(path);
        boolean ignored = false;
        for (CompiledRule rule : compiledRules) {
            if (rule.pattern().matcher(normalized).matches()) {
                ignored = !rule.negated();
            }
        }
        return ignored;
    }

    public static List<String> validate(List<String> rules) {
        return new CoverageIgnoreRules(rules).rules();
    }

    public static String normalizeRepositoryPath(String path) {
        String normalized = path == null ? "" : path.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    private static CompiledRule compileRule(String rule, int index) {
        if (rule == null || rule.isBlank()) {
            throw invalid("empty", index, "must be a non-empty string");
        }

        boolean negated = rule.startsWith("!");
        String glob = negated ? rule.substring(1) : rule;
        if (glob.isEmpty()) {
            throw invalid("bare_negation", index, "bare ! is not a valid rule");
        }
        if (isAbsoluteFilesystemPath(glob)) {
            throw invalid("absolute_path", index, "must describe a repository-relative path");
        }

        boolean anchored = glob.startsWith("/");
        if (anchored) {
            glob = glob.substring(1);
        }
        boolean directoryOnly = glob.endsWith("/");
        if (directoryOnly) {
            glob = glob.substring(0, glob.length() - 1);
        }
        if (glob.isEmpty()) {
            throw invalid("empty", index, "must contain a path pattern");
        }
        for (String segment : glob.split("/", -1)) {
            if ("..".equals(segment)) {
                throw invalid("parent_traversal", index, "must not contain parent traversal");
            }
        }

        String prefix = anchored ? "^" : "^(?:.*/)?";
        String suffix = directoryOnly ? "/.*$" : "(?:/.*)?$";
        try {
            return new CompiledRule(negated, Pattern.compile(prefix + translateGlob(glob, index) + suffix));
        } catch (PatternSyntaxException exception) {
            throw invalid("malformed_range", index, "contains a malformed character range");
        }
    }

    private static String translateGlob(String glob, int index) {
        StringBuilder translated = new StringBuilder();
        int position = 0;
        while (position < glob.length()) {
            char character = glob.charAt(position);
            if (character == '\\') {
                if (position + 1 >= glob.length()) {
                    throw invalid("invalid_escape", index, "ends with an invalid escape");
                }
                char escaped = glob.charAt(position + 1);
                if (ALLOWED_ESCAPES.indexOf(escaped) < 0) {
                    throw invalid("invalid_escape", index, "contains an invalid escape");
                }
                translated.append(Pattern.quote(String.valueOf(escaped)));
                position += 2;
                continue;
            }
            if (character == '*') {
                int starEnd = position + 1;
                while (starEnd < glob.length() && glob.charAt(starEnd) == '*') {
                    starEnd++;
                }
                boolean globstar = starEnd - position >= 2
                        && (position == 0 || glob.charAt(position - 1) == '/')
                        && (starEnd == glob.length() || glob.charAt(starEnd) == '/');
                if (globstar) {
                    if (starEnd < glob.length() && glob.charAt(starEnd) == '/') {
                        translated.append("(?:.*/)?");
                        position = starEnd + 1;
                    } else {
                        translated.append(".*");
                        position = starEnd;
                    }
                    continue;
                }
                translated.append("[^/]*".repeat(starEnd - position));
                position = starEnd;
                continue;
            }
            if (character == '?') {
                translated.append("[^/]");
                position++;
                continue;
            }
            if (character == '[') {
                CharacterClass characterClass = translateCharacterClass(glob, position, index);
                translated.append(characterClass.regex());
                position = characterClass.nextPosition();
                continue;
            }
            translated.append(Pattern.quote(String.valueOf(character)));
            position++;
        }
        return translated.toString();
    }

    private static CharacterClass translateCharacterClass(String glob, int start, int index) {
        int end = glob.indexOf(']', start + 1);
        if (end < 0) {
            throw invalid("malformed_range", index, "contains an unclosed character range");
        }
        String content = glob.substring(start + 1, end);
        boolean negated = content.startsWith("!") || content.startsWith("^");
        String body = negated ? content.substring(1) : content;
        if (body.isEmpty()) {
            throw invalid("malformed_range", index, "contains an empty character range");
        }
        validateRanges(body, index);
        String escapedBody = body.replace("\\", "\\\\").replace("]", "\\]");
        if (escapedBody.startsWith("^")) {
            escapedBody = "\\" + escapedBody;
        }
        return new CharacterClass("[" + (negated ? "^" : "") + escapedBody + "]", end + 1);
    }

    private static void validateRanges(String body, int index) {
        for (int position = 1; position < body.length() - 1; position++) {
            if (body.charAt(position) == '-'
                    && body.charAt(position - 1) > body.charAt(position + 1)) {
                throw invalid("malformed_range", index, "contains a descending character range");
            }
        }
    }

    private static boolean isAbsoluteFilesystemPath(String glob) {
        return glob.startsWith("\\\\")
                || glob.startsWith("//")
                || WINDOWS_ABSOLUTE.matcher(glob).matches()
                || URI.matcher(glob).matches();
    }

    private static InvalidCoverageIgnoreRuleException invalid(String code, int index, String detail) {
        return new InvalidCoverageIgnoreRuleException(code, index, "ignore[" + index + "] " + detail);
    }

    private record CompiledRule(boolean negated, Pattern pattern) {
    }

    private record CharacterClass(String regex, int nextPosition) {
    }
}
