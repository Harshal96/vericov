package dev.vericov.controlplane.api;

import dev.vericov.controlplane.application.CoverageBadgeDetails;
import dev.vericov.controlplane.application.OrganizationException;
import java.util.Locale;

final class CoverageBadgeSvgRenderer {
    private CoverageBadgeSvgRenderer() {
    }

    static String render(CoverageBadgeDetails badge, String requestedStyle) {
        BadgeStyle style = BadgeStyle.from(requestedStyle);
        String rawLabel = style.uppercase ? badge.label().toUpperCase(Locale.ROOT) : badge.label();
        String rawMessage = style.uppercase ? badge.message().toUpperCase(Locale.ROOT) : badge.message();
        String label = escape(rawLabel);
        String message = escape(rawMessage);
        int labelWidth = Math.max(style.minimumLabelWidth, rawLabel.length() * style.characterWidth + style.horizontalPadding);
        int messageWidth = Math.max(style.minimumMessageWidth, rawMessage.length() * style.characterWidth + style.horizontalPadding);
        int width = labelWidth + messageWidth;
        String color = colorHex(badge.color());
        String gradient = style.gradientId == null ? "" : """
                  <linearGradient id="%s" x2="0" y2="100%%">
                    <stop offset="0" stop-color="#fff" stop-opacity=".25"/>
                    <stop offset="1" stop-color="#000" stop-opacity=".05"/>
                  </linearGradient>
                """.formatted(style.gradientId);
        String gradientRect = style.gradientId == null ? "" : """
                    <rect width="%d" height="%d" fill="url(#%s)"/>
                """.formatted(width, style.height, style.gradientId);

        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" role="img" aria-label="%s: %s">
                %s  <clipPath id="r">
                    <rect width="%d" height="%d" rx="%d" fill="#fff"/>
                  </clipPath>
                  <g clip-path="url(#r)">
                    <rect width="%d" height="%d" fill="#555"/>
                    <rect x="%d" width="%d" height="%d" fill="%s"/>
                %s  </g>
                  <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
                    <text x="%d" y="%d" fill="#010101" fill-opacity=".3">%s</text>
                    <text x="%d" y="%d">%s</text>
                    <text x="%d" y="%d" fill="#010101" fill-opacity=".3">%s</text>
                    <text x="%d" y="%d">%s</text>
                  </g>
                </svg>
                """.formatted(
                width,
                style.height,
                label,
                message,
                gradient,
                width,
                style.height,
                style.cornerRadius,
                labelWidth,
                style.height,
                labelWidth,
                messageWidth,
                style.height,
                color,
                gradientRect,
                labelWidth / 2,
                style.shadowTextY,
                label,
                labelWidth / 2,
                style.textY,
                label,
                labelWidth + messageWidth / 2,
                style.shadowTextY,
                message,
                labelWidth + messageWidth / 2,
                style.textY,
                message);
    }

    private static String colorHex(String color) {
        return switch (color) {
            case "brightgreen" -> "#4c1";
            case "green" -> "#97ca00";
            case "yellow" -> "#dfb317";
            case "red" -> "#e05d44";
            case "lightgrey" -> "#9f9f9f";
            default -> "#007ec6";
        };
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private enum BadgeStyle {
        FLAT("flat", 20, 3, 54, 50, 7, 10, 15, 14, null, false),
        FLAT_SQUARE("flat-square", 20, 0, 54, 50, 7, 10, 15, 14, null, false),
        PLASTIC("plastic", 20, 3, 54, 50, 7, 10, 15, 14, "plastic", false),
        FOR_THE_BADGE("for-the-badge", 28, 0, 90, 70, 8, 18, 18, 17, null, true);

        private final String wireValue;
        private final int height;
        private final int cornerRadius;
        private final int minimumLabelWidth;
        private final int minimumMessageWidth;
        private final int characterWidth;
        private final int horizontalPadding;
        private final int shadowTextY;
        private final int textY;
        private final String gradientId;
        private final boolean uppercase;

        BadgeStyle(
                String wireValue,
                int height,
                int cornerRadius,
                int minimumLabelWidth,
                int minimumMessageWidth,
                int characterWidth,
                int horizontalPadding,
                int shadowTextY,
                int textY,
                String gradientId,
                boolean uppercase) {
            this.wireValue = wireValue;
            this.height = height;
            this.cornerRadius = cornerRadius;
            this.minimumLabelWidth = minimumLabelWidth;
            this.minimumMessageWidth = minimumMessageWidth;
            this.characterWidth = characterWidth;
            this.horizontalPadding = horizontalPadding;
            this.shadowTextY = shadowTextY;
            this.textY = textY;
            this.gradientId = gradientId;
            this.uppercase = uppercase;
        }

        private static BadgeStyle from(String value) {
            String normalized = value == null || value.isBlank() ? "flat" : value.trim().toLowerCase(Locale.ROOT);
            for (BadgeStyle style : values()) {
                if (style.wireValue.equals(normalized)) {
                    return style;
                }
            }
            throw new OrganizationException("validation_error", "badge style is invalid");
        }
    }
}
