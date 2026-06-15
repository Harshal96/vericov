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
            CompiledPath path = compilePath(this.rules.get(index), index, true, "ignore");
            compiled.add(new CompiledRule(path.negated(), path.pattern()));
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

    static CompiledPath compilePath(
            String rule,
            int index,
            boolean allowNegation,
            String field) {
        if (rule == null || rule.isBlank()) {
            throw invalid("empty", index, "must be a non-empty string", field);
        }

        boolean negated = rule.startsWith("!");
        if (negated && !allowNegation) {
            throw invalid("negation_not_allowed", index, "must not use negation", field);
        }
        String glob = negated ? rule.substring(1) : rule;
        if (glob.isEmpty()) {
            throw invalid("bare_negation", index, "bare ! is not a valid rule", field);
        }
        if (isAbsoluteFilesystemPath(glob)) {
            throw invalid("absolute_path", index, "must describe a repository-relative path", field);
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
            throw invalid("empty", index, "must contain a path pattern", field);
        }
        for (String segment : glob.split("/", -1)) {
            if ("..".equals(segment)) {
                throw invalid("parent_traversal", index, "must not contain parent traversal", field);
            }
        }

        String prefix = anchored ? "^" : "^(?:.*/)?";
        String suffix = directoryOnly ? "/.*$" : "(?:/.*)?$";
        try {
            return new CompiledPath(
                    negated,
                    Pattern.compile(prefix + translateGlob(glob, index, field) + suffix),
                    specificity(rule));
        } catch (PatternSyntaxException exception) {
            throw invalid("malformed_range", index, "contains a malformed character range", field);
        }
    }

    private static String translateGlob(String glob, int index, String field) {
        StringBuilder translated = new StringBuilder();
        int position = 0;
        while (position < glob.length()) {
            char character = glob.charAt(position);
            if (character == '\\') {
                if (position + 1 >= glob.length()) {
                    throw invalid("invalid_escape", index, "ends with an invalid escape", field);
                }
                char escaped = glob.charAt(position + 1);
                if (ALLOWED_ESCAPES.indexOf(escaped) < 0) {
                    throw invalid("invalid_escape", index, "contains an invalid escape", field);
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
                CharacterClass characterClass = translateCharacterClass(glob, position, index, field);
                translated.append(characterClass.regex());
                position = characterClass.nextPosition();
                continue;
            }
            translated.append(Pattern.quote(String.valueOf(character)));
            position++;
        }
        return translated.toString();
    }

    private static CharacterClass translateCharacterClass(String glob, int start, int index, String field) {
        int end = glob.indexOf(']', start + 1);
        if (end < 0) {
            throw invalid("malformed_range", index, "contains an unclosed character range", field);
        }
        String content = glob.substring(start + 1, end);
        boolean negated = content.startsWith("!") || content.startsWith("^");
        String body = negated ? content.substring(1) : content;
        if (body.isEmpty()) {
            throw invalid("malformed_range", index, "contains an empty character range", field);
        }
        validateRanges(body, index, field);
        String escapedBody = body.replace("\\", "\\\\").replace("]", "\\]");
        if (escapedBody.startsWith("^")) {
            escapedBody = "\\" + escapedBody;
        }
        return new CharacterClass("[" + (negated ? "^" : "") + escapedBody + "]", end + 1);
    }

    private static void validateRanges(String body, int index, String field) {
        for (int position = 1; position < body.length() - 1; position++) {
            if (body.charAt(position) == '-'
                    && body.charAt(position - 1) > body.charAt(position + 1)) {
                throw invalid("malformed_range", index, "contains a descending character range", field);
            }
        }
    }

    private static boolean isAbsoluteFilesystemPath(String glob) {
        return glob.startsWith("\\\\")
                || glob.startsWith("//")
                || WINDOWS_ABSOLUTE.matcher(glob).matches()
                || URI.matcher(glob).matches();
    }

    private static CoveragePathPattern.Specificity specificity(String rule) {
        String glob = rule.startsWith("/") ? rule.substring(1) : rule;
        StringBuilder literal = new StringBuilder();
        boolean wildcardFound = false;
        int position = 0;
        while (position < glob.length()) {
            char character = glob.charAt(position);
            if (character == '\\' && position + 1 < glob.length()) {
                literal.append(glob.charAt(position + 1));
                position += 2;
                continue;
            }
            if (character == '*' || character == '?' || character == '[') {
                wildcardFound = true;
                break;
            }
            literal.append(character);
            position++;
        }
        int segments = 0;
        if (wildcardFound) {
            for (int index = 0; index < literal.length(); index++) {
                if (literal.charAt(index) == '/') {
                    segments++;
                }
            }
        } else {
            for (String segment : literal.toString().split("/")) {
                if (!segment.isEmpty()) {
                    segments++;
                }
            }
        }
        return new CoveragePathPattern.Specificity(segments, literal.length());
    }

    private static InvalidCoverageIgnoreRuleException invalid(
            String code,
            int index,
            String detail,
            String field) {
        return new InvalidCoverageIgnoreRuleException(code, index, field + "[" + index + "] " + detail);
    }

    private record CompiledRule(boolean negated, Pattern pattern) {
    }

    record CompiledPath(
            boolean negated,
            Pattern pattern,
            CoveragePathPattern.Specificity specificity) {
    }

    private record CharacterClass(String regex, int nextPosition) {
    }
}
